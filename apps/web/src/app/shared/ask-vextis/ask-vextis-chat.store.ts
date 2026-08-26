import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AskVextisConversationGQL, AskVextisGQL } from '../../api/generated/graphql';

export interface ChatMessage {
  id: string;
  sender: 'USER' | 'ASSISTANT';
  content: string;
  kind: 'TEXT' | 'VOICE_TRANSCRIPT';
  createdAt: string;
  agentActivities: AgentActivity[];
}

export interface AgentActivity {
  agentId: string;
  agentVersion: string;
  displayName: string;
  modelId: string;
  promptVersion: string;
  tools: string[];
}

/**
 * Shared open/closed state and message history for the "Ask Vextis" panel,
 * modeled on MissionControlStore's signal-based pattern so it can be read
 * from both the navbar trigger and the panel itself.
 */
@Injectable({ providedIn: 'root' })
export class AskVextisChatStore {
  private readonly askVextisMutation = inject(AskVextisGQL);
  private readonly conversationQuery = inject(AskVextisConversationGQL);
  private readonly destroyRef = inject(DestroyRef);

  readonly open = signal(false);
  readonly messages = signal<ChatMessage[]>([]);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);

  private conversationId: string | null = null;
  private historyLoadedForConversation: string | null = null;

  openPanel(): void {
    this.open.set(true);
    if (this.conversationId && this.historyLoadedForConversation !== this.conversationId) {
      this.loadHistory(this.conversationId);
    }
  }

  closePanel(): void {
    this.open.set(false);
  }

  toggle(): void {
    if (this.open()) {
      this.closePanel();
    } else {
      this.openPanel();
    }
  }

  sendMessage(text: string): void {
    const trimmed = text.trim();
    if (!trimmed || this.sending()) {
      return;
    }

    this.error.set(null);
    this.pushLocalMessage('USER', trimmed, undefined, []);
    this.sending.set(true);

    this.askVextisMutation
      .mutate({ variables: { input: { conversationId: this.conversationId, message: trimmed } } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          this.sending.set(false);
          const result = data?.askVextis;
          if (!result) {
            this.error.set('Ask Vextis did not return a reply.');
            return;
          }
          this.conversationId = result.conversationId;
          this.historyLoadedForConversation = result.conversationId;
          this.pushLocalMessage(
            'ASSISTANT',
            result.reply,
            result.createdAt,
            result.agentActivities.map((activity) => ({ ...activity, tools: [...activity.tools] })),
          );
        },
        error: () => {
          this.sending.set(false);
          this.error.set('Ask Vextis could not reach Agent Runtime. Try again.');
        },
      });
  }

  private loadHistory(conversationId: string): void {
    this.conversationQuery
      .fetch({ variables: { id: conversationId } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          this.historyLoadedForConversation = conversationId;
          const messages = data?.askVextisConversation?.messages ?? [];
          this.messages.set(
            messages.map((message) => ({
              ...message,
              agentActivities: message.agentActivities.map((activity) => ({
                ...activity,
                tools: [...activity.tools],
              })),
            })),
          );
        },
      });
  }

  private pushLocalMessage(
    sender: ChatMessage['sender'],
    content: string,
    createdAt = new Date().toISOString(),
    agentActivities: AgentActivity[] = [],
  ): void {
    this.messages.update((messages) => [
      ...messages,
      {
        id: `local-${messages.length}-${Date.now()}`,
        sender,
        content,
        kind: 'TEXT',
        createdAt,
        agentActivities,
      },
    ]);
  }
}
