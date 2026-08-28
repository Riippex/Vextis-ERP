import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AskVextisChatStore } from './ask-vextis-chat.store';
import { AskVextisLiveStore } from './ask-vextis-live.store';

@Component({
  selector: 'vxt-ask-vextis-panel',
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './ask-vextis-panel.component.html',
  styleUrl: './ask-vextis-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AskVextisPanelComponent {
  protected readonly store = inject(AskVextisChatStore);
  protected readonly liveStore = inject(AskVextisLiveStore);
  protected readonly draft = signal('');

  private readonly messageList = viewChild<ElementRef<HTMLDivElement>>('messageList');

  constructor() {
    effect(() => {
      const count = this.store.messages().length;
      const list = this.messageList()?.nativeElement;
      if (count > 0 && list) {
        queueMicrotask(() => {
          list.scrollTop = list.scrollHeight;
        });
      }
    });
  }

  protected close(): void {
    if (this.liveStore.isLiveActive()) {
      this.liveStore.stopLive();
    }
    this.store.closePanel();
  }

  protected onDraftInput(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    this.draft.set(textarea.value);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
    if (event.key === 'Escape') {
      this.close();
    }
  }

  protected send(): void {
    const text = this.draft();
    if (!text.trim() || this.store.sending()) {
      return;
    }
    this.store.sendMessage(text);
    this.draft.set('');
  }

  protected toggleLive(): void {
    if (this.liveStore.isLiveActive()) {
      this.liveStore.stopLive();
    } else {
      this.liveStore.startLive(this.store.getConversationId());
    }
  }

  protected toggleMute(): void {
    this.liveStore.toggleMute();
  }
}
