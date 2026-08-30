import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { CloseLiveSessionGQL, CreateLiveSessionGQL } from '../../api/generated/graphql';
import { LiveAudioService } from './live-audio.service';

export type LiveSessionStatus =
  | 'IDLE'
  | 'CONNECTING'
  | 'LISTENING'
  | 'SPEAKING'
  | 'MUTED'
  | 'DISCONNECTED'
  | 'ERROR';

export interface LiveSessionInfo {
  id: string;
  websocketUrl: string;
  expiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class AskVextisLiveStore {
  private readonly createLiveSessionMutation = inject(CreateLiveSessionGQL);
  private readonly closeLiveSessionMutation = inject(CloseLiveSessionGQL);
  private readonly liveAudio = inject(LiveAudioService);
  private readonly destroyRef = inject(DestroyRef);

  readonly status = signal<LiveSessionStatus>('IDLE');
  readonly isMuted = signal(false);
  readonly liveTranscript = signal<string>('');
  readonly error = signal<string | null>(null);
  readonly currentSession = signal<LiveSessionInfo | null>(null);

  readonly isLiveActive = computed(() => {
    const s = this.status();
    return s === 'CONNECTING' || s === 'LISTENING' || s === 'SPEAKING' || s === 'MUTED';
  });

  private socket: WebSocket | null = null;
  private pcmSubscription: Subscription | null = null;

  /**
   * Incremented by every startLive and every stopLive. Each attempt captures the
   * value it started with, so any callback that resolves after the user cancelled
   * — a late CreateLiveSession response, a socket that finishes CONNECTING, a
   * getUserMedia prompt the user answers seconds later — can tell it is stale and
   * refuse to open a socket or turn the microphone on.
   *
   * The pending CreateLiveSession subscription is deliberately left running: a
   * stale response still has to close the credential Enterprise Core already
   * issued. takeUntilDestroyed drops it if the injector goes away first.
   */
  private sessionGeneration = 0;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.stopLive();
    });
  }

  startLive(conversationId?: string | null): void {
    if (this.isLiveActive()) {
      return;
    }

    const generation = ++this.sessionGeneration;

    this.error.set(null);
    this.liveTranscript.set('');
    this.status.set('CONNECTING');

    const convId = conversationId || crypto.randomUUID();

    this.createLiveSessionMutation
      .mutate({ variables: { input: { conversationId: convId } } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          const session = data?.createLiveSession;

          if (this.isStale(generation)) {
            // The user stopped while Enterprise Core was still authorizing.
            // Release the credential server-side instead of leaving it open,
            // and never touch the socket or the microphone.
            if (session?.id) {
              this.closeServerSession(session.id);
            }
            return;
          }

          if (!session) {
            this.status.set('ERROR');
            this.error.set('Failed to create live voice session credential.');
            return;
          }

          this.currentSession.set({
            id: session.id,
            websocketUrl: session.websocketUrl,
            expiresAt: session.expiresAt,
          });

          this.connectWebSocket(session.websocketUrl, session.sessionToken, generation);
        },
        error: (err) => {
          if (this.isStale(generation)) {
            return;
          }
          this.status.set('ERROR');
          this.error.set(err?.message || 'Could not initiate live voice session.');
        },
      });
  }

  private connectWebSocket(url: string, token: string, generation: number): void {
    let socket: WebSocket;
    try {
      socket = new WebSocket(url);
    } catch {
      this.status.set('ERROR');
      this.error.set('Failed to open live WebSocket.');
      this.cleanupLocalAudio();
      return;
    }

    this.socket = socket;
    socket.binaryType = 'arraybuffer';

    socket.onopen = () => {
      if (this.isStale(generation, socket)) {
        this.discardSocket(socket);
        return;
      }

      // Send the ephemeral session token as the first frame; it is never put in
      // the URL, where it would reach Cloud Run access logs.
      socket.send(JSON.stringify({ type: 'auth', token }));

      void this.startMicrophone(socket, generation);
    };

    socket.onmessage = (event: MessageEvent) => {
      if (this.isStale(generation, socket)) {
        return;
      }
      if (typeof event.data === 'string') {
        try {
          const parsed = JSON.parse(event.data) as { type?: string; text?: string };
          if (parsed.type === 'transcript' && parsed.text) {
            this.liveTranscript.set(parsed.text);
          }
        } catch {
          // Ignore non-JSON text frames
        }
      } else if (event.data instanceof ArrayBuffer) {
        this.status.set('SPEAKING');
        this.liveAudio.playAudioChunk(event.data);
        // Return to listening state after chunk playback
        setTimeout(() => {
          if (this.isStale(generation, socket)) {
            return;
          }
          if (this.status() === 'SPEAKING') {
            this.status.set(this.isMuted() ? 'MUTED' : 'LISTENING');
          }
        }, 300);
      }
    };

    socket.onerror = () => {
      if (this.isStale(generation, socket)) {
        return;
      }
      this.error.set('Live connection error encountered.');
    };

    socket.onclose = (event: CloseEvent) => {
      if (this.isStale(generation, socket)) {
        return;
      }
      if (event.code === 4401) {
        this.error.set('Live session token expired or invalid.');
      }
      if (this.status() !== 'IDLE') {
        this.status.set('DISCONNECTED');
      }
      this.cleanupLocalAudio();
    };
  }

  private async startMicrophone(socket: WebSocket, generation: number): Promise<void> {
    try {
      await this.liveAudio.startRecording();
    } catch (micErr: unknown) {
      if (this.isStale(generation, socket)) {
        this.liveAudio.stopRecording();
        return;
      }
      const msg = micErr instanceof Error ? micErr.message : 'Microphone access denied';
      this.status.set('ERROR');
      this.error.set(msg);
      this.stopLive();
      return;
    }

    if (this.isStale(generation, socket)) {
      // stopLive ran while the permission prompt was open: hand the microphone
      // straight back instead of leaving a live capture nobody can stop.
      this.liveAudio.stopRecording();
      return;
    }

    this.status.set('LISTENING');
    this.pcmSubscription = this.liveAudio.pcmChunks$.subscribe((chunk) => {
      if (this.isStale(generation, socket)) {
        return;
      }
      if (socket.readyState === WebSocket.OPEN && !this.isMuted()) {
        socket.send(chunk);
      }
    });
  }

  toggleMute(): void {
    if (!this.isLiveActive()) {
      return;
    }
    const newMuted = !this.isMuted();
    this.isMuted.set(newMuted);
    if (newMuted) {
      this.status.set('MUTED');
    } else {
      this.status.set('LISTENING');
    }
  }

  stopLive(): void {
    // Invalidate every in-flight callback before releasing anything, so nothing
    // scheduled by the attempt being cancelled can run against the new state.
    this.sessionGeneration++;

    const session = this.currentSession();
    const socket = this.socket;
    this.socket = null;

    if (socket) {
      const wasOpen = socket.readyState === WebSocket.OPEN;
      this.discardSocket(socket, wasOpen);
    }

    if (session?.id) {
      this.closeServerSession(session.id);
    }

    this.currentSession.set(null);
    this.status.set('IDLE');
    this.isMuted.set(false);
    this.liveTranscript.set('');
    this.cleanupLocalAudio();
  }

  /**
   * Detaches every handler and closes the socket, including while it is still
   * CONNECTING — an unattended CONNECTING socket would otherwise fire onopen
   * after the user cancelled.
   */
  private discardSocket(socket: WebSocket, sendClose = false): void {
    socket.onopen = null;
    socket.onmessage = null;
    socket.onerror = null;
    socket.onclose = null;

    try {
      if (sendClose && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'close' }));
      }
      if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
        socket.close();
      }
    } catch {
      // Socket already closing or closed
    }
  }

  private closeServerSession(sessionId: string): void {
    this.closeLiveSessionMutation.mutate({ variables: { id: sessionId } }).subscribe({
      error: (err: unknown) => {
        console.warn('Could not close live session on server', err);
      },
    });
  }

  private isStale(generation: number, socket?: WebSocket): boolean {
    if (generation !== this.sessionGeneration) {
      return true;
    }
    return socket !== undefined && this.socket !== socket;
  }

  private cleanupLocalAudio(): void {
    if (this.pcmSubscription) {
      this.pcmSubscription.unsubscribe();
      this.pcmSubscription = null;
    }
    this.liveAudio.stopRecording();
  }
}
