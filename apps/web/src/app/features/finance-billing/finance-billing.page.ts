import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { MissionControlStore } from '../mission-control/mission-control.store';

@Component({
  selector: 'vxt-finance-billing-page',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './finance-billing.page.html',
  styleUrl: '../mission-control/workspace-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinanceBillingPage {
  protected readonly store = inject(MissionControlStore);
  protected readonly goodStanding = computed(
    () => this.store.data()?.creditProfiles.filter(({ standing }) => standing === 'GOOD').length ?? 0,
  );
}
