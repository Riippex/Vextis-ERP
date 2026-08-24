import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { MissionControlStore } from '../mission-control/mission-control.store';

@Component({
  selector: 'vxt-dashboard-page',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './dashboard.page.html',
  styleUrls: ['./dashboard.page.scss', '../mission-control/workspace-page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  protected readonly store = inject(MissionControlStore);

  protected readonly activeExecutions = computed(
    () =>
      this.store
        .data()
        ?.executions.filter(({ state }) => state !== 'COMPLETED' && state !== 'FAILED').length ?? 0,
  );
  protected readonly waitingApprovals = computed(
    () =>
      this.store.data()?.executions.filter(({ state }) => state === 'WAITING_APPROVAL').length ?? 0,
  );
  protected readonly activeCustomers = computed(
    () => this.store.data()?.customers.filter(({ active }) => active).length ?? 0,
  );
  protected readonly availableUnits = computed(
    () =>
      this.store.data()?.stockItems.reduce((total, item) => total + item.availableQuantity, 0) ?? 0,
  );

  protected isHealthyState(state: string): boolean {
    return state === 'RUNNING' || state === 'COMPLETED';
  }
}
