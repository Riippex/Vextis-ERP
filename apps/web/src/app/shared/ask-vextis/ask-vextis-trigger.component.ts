import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AskVextisChatStore } from './ask-vextis-chat.store';

@Component({
  selector: 'vxt-ask-vextis-trigger',
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './ask-vextis-trigger.component.html',
  styleUrl: './ask-vextis-trigger.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AskVextisTriggerComponent {
  private readonly store = inject(AskVextisChatStore);

  protected readonly open = this.store.open;

  protected toggle(): void {
    this.store.toggle();
  }
}
