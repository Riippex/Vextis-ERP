import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CloseLiveSessionGQL, CreateLiveSessionGQL } from '../../api/generated/graphql';
import { AskVextisLiveStore } from './ask-vextis-live.store';
import { LiveAudioService } from './live-audio.service';

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
    createLiveSessionGqlMock = {
      mutate: vi.fn().mockReturnValue(
        of({
          data: {
            createLiveSession: {
              id: 'live-session-123',
              websocketUrl: 'wss://agent.vextis.local/v1/live/live-session-123',
              sessionToken: 'token-abc-123',
              expiresAt: '2026-08-27T22:00:00Z',
            },
          },
        }),
      ),
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
});
