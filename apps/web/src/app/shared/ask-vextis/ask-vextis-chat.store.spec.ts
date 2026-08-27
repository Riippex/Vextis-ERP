import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AskVextisConversationGQL, AskVextisGQL } from '../../api/generated/graphql';
import { AskVextisChatStore } from './ask-vextis-chat.store';

describe('AskVextisChatStore', () => {
  const mutate = vi.fn().mockReturnValue(
    of({
      data: {
        askVextis: {
          conversationId: 'conv-1',
          messageId: 'msg-1',
          reply: 'Order PO-2026-001 is currently in planning.',
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
            preferenceStored: false,
          },
        },
      },
    }),
  );
  const fetch = vi.fn().mockReturnValue(
    of({
      data: {
        askVextisConversation: {
          id: 'conv-1',
          messages: [
            {
              id: 'm1',
              sender: 'USER' as const,
              content: 'Hello',
              kind: 'TEXT' as const,
              createdAt: '2026-08-25T11:00:00Z',
              agentActivities: [],
              memoryEvidence: null,
            },
          ],
        },
      },
    }),
  );

  let store: AskVextisChatStore;

  beforeEach(() => {
    mutate.mockClear();
    fetch.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AskVextisGQL, useValue: { mutate } },
        { provide: AskVextisConversationGQL, useValue: { fetch } },
      ],
    });
    store = TestBed.inject(AskVextisChatStore);
  });

  it('starts closed with no messages', () => {
    expect(store.open()).toBe(false);
    expect(store.messages()).toEqual([]);
  });

  it('opens, closes, and toggles', () => {
    store.openPanel();
    expect(store.open()).toBe(true);

    store.closePanel();
    expect(store.open()).toBe(false);

    store.toggle();
    expect(store.open()).toBe(true);
    store.toggle();
    expect(store.open()).toBe(false);
  });

  it('sends a message and appends the real assistant reply', () => {
    store.sendMessage('What is the status of PO-2026-001?');

    expect(mutate).toHaveBeenCalledOnce();
    const messages = store.messages();
    expect(messages).toHaveLength(2);
    expect(messages[0]).toMatchObject({ sender: 'USER', content: 'What is the status of PO-2026-001?' });
    expect(messages[1]).toMatchObject({
      sender: 'ASSISTANT',
      content: 'Order PO-2026-001 is currently in planning.',
      agentActivities: [
        { agentId: 'vextis_inventory_agent', displayName: 'Inventory Agent', tools: ['get_stock'] },
      ],
      memoryEvidence: {
        provider: 'VERTEX_AI_MEMORY_BANK',
        available: true,
        contextCount: 1,
        preferenceStored: false,
      },
    });
    expect(store.sending()).toBe(false);
  });

  it('ignores blank messages', () => {
    store.sendMessage('   ');
    expect(store.messages()).toEqual([]);
    expect(mutate).not.toHaveBeenCalled();
  });

  it('surfaces an error when Agent Runtime is unreachable', () => {
    mutate.mockReturnValueOnce(throwError(() => new Error('network error')));

    store.sendMessage('Hello');

    expect(store.error()).toContain('Ask Vextis could not reach Agent Runtime');
    expect(store.sending()).toBe(false);
  });
});
