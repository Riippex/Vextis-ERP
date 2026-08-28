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

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.stopLive();
    });
  }

  startLive(conversationId?: string | null): void {
    if (this.isLiveActive()) {
      return;
    }

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

          this.connectWebSocket(session.websocketUrl, session.sessionToken);
        },
        error: (err) => {
          this.status.set('ERROR');
          this.error.set(err?.message || 'Could not initiate live voice session.');
        },
      });
  }

  private connectWebSocket(url: string, token: string): void {
    try {
      this.socket = new WebSocket(url);
      this.socket.binaryType = 'arraybuffer';

      this.socket.onopen = async () => {
        // Send ephemeral session token as the first frame
        this.socket?.send(JSON.stringify({ type: 'auth', token }));

        try {
          await this.liveAudio.startRecording();
          this.status.set('LISTENING');

          this.pcmSubscription = this.liveAudio.pcmChunks$.subscribe((chunk) => {
            if (this.socket?.readyState === WebSocket.OPEN && !this.isMuted()) {
              this.socket.send(chunk);
            }
          });
        } catch (micErr: unknown) {
          const msg = micErr instanceof Error ? micErr.message : 'Microphone access denied';
          this.status.set('ERROR');
          this.error.set(msg);
          this.stopLive();
        }
      };

      this.socket.onmessage = (event: MessageEvent) => {
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
            if (this.status() === 'SPEAKING') {
              this.status.set(this.isMuted() ? 'MUTED' : 'LISTENING');
            }
          }, 300);
        }
      };

      this.socket.onerror = () => {
        this.error.set('Live connection error encountered.');
      };

      this.socket.onclose = (event: CloseEvent) => {
        if (event.code === 4401) {
          this.error.set('Live session token expired or invalid.');
        }
        if (this.status() !== 'IDLE') {
          this.status.set('DISCONNECTED');
        }
        this.cleanupLocalAudio();
      };
    } catch {
      this.status.set('ERROR');
      this.error.set('Failed to open live WebSocket.');
      this.cleanupLocalAudio();
    }
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
    const session = this.currentSession();
    if (session?.id) {
      this.closeLiveSessionMutation
        .mutate({ variables: { id: session.id } })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ error: () => {} });
    }

    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      try {
        this.socket.send(JSON.stringify({ type: 'close' }));
        this.socket.close();
      } catch {
        // Socket closing
      }
    }
    this.socket = null;
    this.currentSession.set(null);
    this.status.set('IDLE');
    this.isMuted.set(false);
    this.liveTranscript.set('');
    this.cleanupLocalAudio();
  }

  private cleanupLocalAudio(): void {
    if (this.pcmSubscription) {
      this.pcmSubscription.unsubscribe();
      this.pcmSubscription = null;
    }
    this.liveAudio.stopRecording();
  }
}
