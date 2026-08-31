import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  AskVextisConversationGQL,
  AskVextisGQL,
  CloseLiveSessionGQL,
  CreateLiveSessionGQL,
} from '../../api/generated/graphql';
import { AskVextisChatStore } from './ask-vextis-chat.store';
import { AskVextisLiveStore } from './ask-vextis-live.store';
import { AskVextisPanelComponent } from './ask-vextis-panel.component';
import { LiveAudioService } from './live-audio.service';

describe('AskVextisPanelComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AskVextisPanelComponent],
      providers: [
        AskVextisChatStore,
        AskVextisLiveStore,
        {
          provide: AskVextisGQL,
          useValue: {
            mutate: vi.fn().mockReturnValue(
              of({
                data: {
                  askVextis: {
                    conversationId: 'conv-1',
                    messageId: 'msg-1',
                    reply: 'Placeholder reply',
                    createdAt: '2026-08-25T12:00:00Z',
                    agentActivities: [
                      {
                        agentId: 'vextis_inventory_agent',
                        agentVersion: '1.0.0',
                        displayName: 'Inventory Agent',
                        modelId: 'gemini-3.5-flash',
                        promptVersion: '1.0.0',
                        tools: ['get_stock'],
                      },
                    ],
                    memoryEvidence: {
                      provider: 'VERTEX_AI_MEMORY_BANK',
                      available: true,
                      contextCount: 1,
                      preferenceStored: true,
                    },
                  },
                },
              }),
            ),
          },
        },
        { provide: AskVextisConversationGQL, useValue: { fetch: vi.fn() } },
        {
          provide: CreateLiveSessionGQL,
          useValue: {
            mutate: vi.fn().mockReturnValue(
              of({
                data: {
                  createLiveSession: {
                    id: 'sess-1',
                    websocketUrl: 'wss://agent.vextis.local/v1/live/sess-1',
                    sessionToken: 'token-1',
                    expiresAt: '2026-08-27T22:00:00Z',
                  },
                },
              }),
            ),
          },
        },
        { provide: CloseLiveSessionGQL, useValue: { mutate: vi.fn().mockReturnValue(of({ data: { closeLiveSession: true } })) } },
        {
          provide: LiveAudioService,
          useValue: {
            startRecording: vi.fn().mockResolvedValue(undefined),
            stopRecording: vi.fn(),
            playAudioChunk: vi.fn(),
            pcmChunks$: { subscribe: vi.fn().mockReturnValue({ unsubscribe: vi.fn() }) },
          },
        },
      ],
    });
  });

  it('is hidden until the store opens it, then renders sent messages', () => {
    const fixture = TestBed.createComponent(AskVextisPanelComponent);
    const store = TestBed.inject(AskVextisChatStore);
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.ask-vextis-panel') as HTMLElement;
    expect(panel.classList.contains('ask-vextis-panel--open')).toBe(false);
    expect(fixture.nativeElement.querySelector('.ask-vextis-backdrop')).toBeNull();

    store.openPanel();
    fixture.detectChanges();
    expect(panel.classList.contains('ask-vextis-panel--open')).toBe(true);
    expect(fixture.nativeElement.querySelector('.ask-vextis-backdrop')).toBeTruthy();

    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Hello Vextis';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Hello Vextis');
    expect(fixture.nativeElement.textContent).toContain('Inventory Agent v1.0.0');
    expect(fixture.nativeElement.textContent).toContain('get_stock');
    expect(fixture.nativeElement.textContent).toContain('Memory Bank · preference saved');
    expect(store.messages()).toHaveLength(2);
  });

  it('closes on backdrop click', () => {
    const fixture = TestBed.createComponent(AskVextisPanelComponent);
    const store = TestBed.inject(AskVextisChatStore);
    store.openPanel();
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.ask-vextis-backdrop') as HTMLElement).click();
    fixture.detectChanges();

    expect(store.open()).toBe(false);
  });

  it('shows an accessible typing indicator while the agent is composing', () => {
    const fixture = TestBed.createComponent(AskVextisPanelComponent);
    const store = TestBed.inject(AskVextisChatStore);
    store.openPanel();
    store.sending.set(true);

    fixture.detectChanges();

    const indicator = fixture.nativeElement.querySelector('.ask-vextis-message--typing');
    expect(indicator?.getAttribute('role')).toBe('status');
    expect(indicator?.getAttribute('aria-label')).toBe('Vextis is composing a response');
    expect(indicator?.querySelectorAll('.ask-vextis-typing-dots span')).toHaveLength(3);
  });

  it('renders Markdown while keeping model-supplied HTML and URLs inert', () => {
    const fixture = TestBed.createComponent(AskVextisPanelComponent);
    const store = TestBed.inject(AskVextisChatStore);
    store.messages.set([
      {
        id: 'assistant-markdown',
        sender: 'ASSISTANT',
        content:
          '**Inventory:**\n- `VXT-CHAIR-01`: 40 units\n\n<script>alert(1)</script>\n[unsafe](javascript:alert(2))\n![tracker](https://example.com/pixel.gif)',
        kind: 'TEXT',
        createdAt: '2026-08-30T12:00:00Z',
        agentActivities: [],
        memoryEvidence: null,
      },
    ]);
    store.openPanel();
    fixture.detectChanges();

    const message = fixture.nativeElement.querySelector('.vxt-markdown') as HTMLElement;
    const unsafeLink = message.querySelector('a');

    expect(message.querySelector('strong')?.textContent).toBe('Inventory:');
    expect(message.querySelector('li code')?.textContent).toBe('VXT-CHAIR-01');
    expect(message.querySelector('script')).toBeNull();
    expect(message.querySelector('img')).toBeNull();
    expect(unsafeLink?.getAttribute('href') ?? '').not.toMatch(/^javascript:/i);
  });
});
