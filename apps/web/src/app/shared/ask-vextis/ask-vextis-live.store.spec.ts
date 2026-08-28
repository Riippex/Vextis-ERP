import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CloseLiveSessionGQL, CreateLiveSessionGQL } from '../../api/generated/graphql';
import { AskVextisLiveStore } from './ask-vextis-live.store';
import { LiveAudioService } from './live-audio.service';

interface CreateLiveSessionResult {
  data: {
    createLiveSession: {
      id: string;
      websocketUrl: string;
      sessionToken: string;
      expiresAt: string;
    };
  };
}

const SESSION_RESULT: CreateLiveSessionResult = {
  data: {
    createLiveSession: {
      id: 'live-session-123',
      websocketUrl: 'wss://agent.vextis.local/v1/live/live-session-123',
      sessionToken: 'token-abc-123',
      expiresAt: '2026-08-27T22:00:00Z',
    },
  },
};

/**
 * Minimal WebSocket stand-in that stays CONNECTING until a test opens it, so the
 * cancel-while-connecting paths can be exercised deterministically.
 */
class FakeWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;
  static instances: FakeWebSocket[] = [];

  readonly url: string;
  binaryType = 'blob';
  readyState: number = FakeWebSocket.CONNECTING;
  closeCalls = 0;
  readonly sent: unknown[] = [];

  onopen: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeWebSocket.instances.push(this);
  }

  send(data: unknown): void {
    this.sent.push(data);
  }

  close(): void {
    this.closeCalls += 1;
    this.readyState = FakeWebSocket.CLOSED;
  }

  /** Simulates the browser finishing the handshake. */
  simulateOpen(): void {
    this.readyState = FakeWebSocket.OPEN;
    this.onopen?.(new Event('open'));
  }
}

const flushMicrotasks = async (): Promise<void> => {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
};

describe('AskVextisLiveStore', () => {
  let store: AskVextisLiveStore;
  let createLiveSessionGqlMock: { mutate: ReturnType<typeof vi.fn> };
  let closeLiveSessionGqlMock: { mutate: ReturnType<typeof vi.fn> };
  let liveAudioMock: {
    startRecording: ReturnType<typeof vi.fn>;
    stopRecording: ReturnType<typeof vi.fn>;
    playAudioChunk: ReturnType<typeof vi.fn>;
    pcmChunks$: { subscribe: ReturnType<typeof vi.fn> };
  };

  beforeEach(() => {
    FakeWebSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);

    createLiveSessionGqlMock = {
      mutate: vi.fn().mockReturnValue(of(SESSION_RESULT)),
    };

    closeLiveSessionGqlMock = {
      mutate: vi.fn().mockReturnValue(of({ data: { closeLiveSession: true } })),
    };

    liveAudioMock = {
      startRecording: vi.fn().mockResolvedValue(undefined),
      stopRecording: vi.fn(),
      playAudioChunk: vi.fn(),
      pcmChunks$: {
        subscribe: vi.fn().mockReturnValue({ unsubscribe: vi.fn() }),
      },
    };

    TestBed.configureTestingModule({
      providers: [
        AskVextisLiveStore,
        { provide: CreateLiveSessionGQL, useValue: createLiveSessionGqlMock },
        { provide: CloseLiveSessionGQL, useValue: closeLiveSessionGqlMock },
        { provide: LiveAudioService, useValue: liveAudioMock },
      ],
    });

    store = TestBed.inject(AskVextisLiveStore);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('initializes with IDLE status and inactive state', () => {
    expect(store.status()).toBe('IDLE');
    expect(store.isLiveActive()).toBe(false);
    expect(store.isMuted()).toBe(false);
    expect(store.liveTranscript()).toBe('');
    expect(store.error()).toBeNull();
  });

  it('triggers CreateLiveSession mutation when starting a live session', () => {
    store.startLive('conv-456');

    expect(createLiveSessionGqlMock.mutate).toHaveBeenCalledWith({
      variables: { input: { conversationId: 'conv-456' } },
    });
    expect(store.currentSession()).toEqual({
      id: 'live-session-123',
      websocketUrl: 'wss://agent.vextis.local/v1/live/live-session-123',
      expiresAt: '2026-08-27T22:00:00Z',
    });
  });

  it('handles mutation errors gracefully', () => {
    createLiveSessionGqlMock.mutate.mockReturnValue(throwError(() => new Error('Server unreachable')));

    store.startLive('conv-err');

    expect(store.status()).toBe('ERROR');
    expect(store.error()).toBe('Server unreachable');
    expect(store.isLiveActive()).toBe(false);
  });

  it('toggles microphone mute state when active', () => {
    store.startLive('conv-mute');
    store.toggleMute();

    expect(store.isMuted()).toBe(true);
    expect(store.status()).toBe('MUTED');

    store.toggleMute();
    expect(store.isMuted()).toBe(false);
    expect(store.status()).toBe('LISTENING');
  });

  it('closes live session and releases audio resources', () => {
    store.startLive('conv-close');
    store.stopLive();

    expect(closeLiveSessionGqlMock.mutate).toHaveBeenCalledWith({
      variables: { id: 'live-session-123' },
    });
    expect(liveAudioMock.stopRecording).toHaveBeenCalled();
    expect(store.status()).toBe('IDLE');
    expect(store.currentSession()).toBeNull();
    expect(store.isLiveActive()).toBe(false);
  });

  describe('cancellation races', () => {
    it('ignores a CreateLiveSession response that arrives after stopLive', () => {
      const deferred = new Subject<CreateLiveSessionResult>();
      createLiveSessionGqlMock.mutate.mockReturnValue(deferred.asObservable());

      store.startLive('conv-late');
      expect(store.status()).toBe('CONNECTING');
      expect(FakeWebSocket.instances).toHaveLength(0);

      store.stopLive();
      expect(store.status()).toBe('IDLE');

      deferred.next(SESSION_RESULT);
      deferred.complete();

      expect(FakeWebSocket.instances).toHaveLength(0);
      expect(liveAudioMock.startRecording).not.toHaveBeenCalled();
      expect(store.currentSession()).toBeNull();
      expect(store.status()).toBe('IDLE');
      expect(store.isLiveActive()).toBe(false);
    });

    it('releases the server-side credential granted by a late CreateLiveSession response', () => {
      const deferred = new Subject<CreateLiveSessionResult>();
      createLiveSessionGqlMock.mutate.mockReturnValue(deferred.asObservable());

      store.startLive('conv-late-credential');
      store.stopLive();

      // stopLive had no session id yet, so nothing was closed at that point.
      expect(closeLiveSessionGqlMock.mutate).not.toHaveBeenCalled();

      deferred.next(SESSION_RESULT);

      expect(closeLiveSessionGqlMock.mutate).toHaveBeenCalledWith({
        variables: { id: 'live-session-123' },
      });
    });

    it('closes a CONNECTING socket on stopLive and neutralises its callbacks', () => {
      store.startLive('conv-connecting');

      const socket = FakeWebSocket.instances.at(-1);
      expect(socket).toBeDefined();
      expect(socket?.readyState).toBe(FakeWebSocket.CONNECTING);

      store.stopLive();

      expect(socket?.closeCalls).toBe(1);
      expect(socket?.onopen).toBeNull();
      expect(socket?.onmessage).toBeNull();
      expect(socket?.onerror).toBeNull();
      expect(socket?.onclose).toBeNull();
    });

    it('never starts recording when a cancelled socket finishes connecting', async () => {
      store.startLive('conv-open-after-stop');
      const socket = FakeWebSocket.instances.at(-1);

      store.stopLive();

      // The browser completes a handshake the store already walked away from.
      socket?.simulateOpen();
      await flushMicrotasks();

      expect(liveAudioMock.startRecording).not.toHaveBeenCalled();
      expect(liveAudioMock.pcmChunks$.subscribe).not.toHaveBeenCalled();
      expect(socket?.sent).toHaveLength(0);
      expect(store.status()).toBe('IDLE');
    });

    it('hands the microphone back when getUserMedia resolves after stopLive', async () => {
      let grantMicrophone: (() => void) | undefined;
      liveAudioMock.startRecording.mockReturnValue(
        new Promise<void>((resolve) => {
          grantMicrophone = resolve;
        }),
      );

      store.startLive('conv-mic-after-stop');
      const socket = FakeWebSocket.instances.at(-1);
      socket?.simulateOpen();

      expect(liveAudioMock.startRecording).toHaveBeenCalledTimes(1);

      store.stopLive();
      grantMicrophone?.();
      await flushMicrotasks();

      expect(liveAudioMock.pcmChunks$.subscribe).not.toHaveBeenCalled();
      expect(liveAudioMock.stopRecording).toHaveBeenCalled();
      expect(store.status()).toBe('IDLE');
      expect(store.isLiveActive()).toBe(false);
    });

    it('streams audio only while the attempt is still current', async () => {
      store.startLive('conv-happy');
      const socket = FakeWebSocket.instances.at(-1);
      socket?.simulateOpen();
      await flushMicrotasks();

      expect(socket?.sent[0]).toBe(JSON.stringify({ type: 'auth', token: 'token-abc-123' }));
      expect(liveAudioMock.startRecording).toHaveBeenCalledTimes(1);
      expect(liveAudioMock.pcmChunks$.subscribe).toHaveBeenCalledTimes(1);
      expect(store.status()).toBe('LISTENING');
    });

    it('does not reopen audio when startLive is called again while connecting', () => {
      const deferred = new Subject<CreateLiveSessionResult>();
      createLiveSessionGqlMock.mutate.mockReturnValue(deferred.asObservable());

      store.startLive('conv-double');
      store.startLive('conv-double');

      expect(createLiveSessionGqlMock.mutate).toHaveBeenCalledTimes(1);
    });
  });
});
