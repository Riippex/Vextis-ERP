import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { WorkspaceSearchStore } from '../../core/search/workspace-search.store';
import { DonutChartComponent, type DonutChartSlice } from '../../shared/charts/donut-chart.component';
import { LineChartComponent, type LineChartPoint } from '../../shared/charts/line-chart.component';
import { MissionControlStore } from '../mission-control/mission-control.store';

const DEPARTMENT_LABELS: Record<string, string> = {
  CRM_SALES: 'CRM & Sales',
  INVENTORY_OPERATIONS: 'Inventory',
  FINANCE_BILLING: 'Finance & Billing',
};

/** Start (Monday, UTC) of the ISO week containing `date`. */
function isoWeekStart(date: Date): Date {
  const utc = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const mondayOffset = (utc.getUTCDay() + 6) % 7;
  utc.setUTCDate(utc.getUTCDate() - mondayOffset);
  return utc;
}

const WEEK_LABEL = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' });

/**
 * Buckets completed executions into the last `weeks` ISO weeks by `updatedAt`.
 * `updatedAt` is the execution's last-mutation time, not a dedicated completion
 * timestamp, so this is an approximation — acceptable for a first version.
 */
export function ordersCompletedPerWeek(
  executions: readonly { state: string; updatedAt: string }[],
  weeks = 6,
): LineChartPoint[] {
  const currentWeekStart = isoWeekStart(new Date()).getTime();
  const dayMs = 24 * 60 * 60 * 1000;
  const buckets = new Map<number, number>();
  for (let index = weeks - 1; index >= 0; index -= 1) {
    buckets.set(currentWeekStart - index * 7 * dayMs, 0);
  }

  for (const execution of executions) {
    if (execution.state !== 'COMPLETED') {
      continue;
    }
    const weekStart = isoWeekStart(new Date(execution.updatedAt)).getTime();
    if (buckets.has(weekStart)) {
      buckets.set(weekStart, (buckets.get(weekStart) ?? 0) + 1);
    }
  }

  return Array.from(buckets.entries()).map(([timestamp, count]) => ({
    label: WEEK_LABEL.format(new Date(timestamp)),
    value: count,
  }));
}

export function toDepartmentVolumeSlices(
  volumes: readonly { department: string; count: number }[],
): DonutChartSlice[] {
  return volumes.map((volume) => ({
    label: DEPARTMENT_LABELS[volume.department] ?? volume.department,
    value: volume.count,
  }));
}

@Component({
  selector: 'vxt-dashboard-page',
  imports: [
    DatePipe,
    DonutChartComponent,
    LineChartComponent,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
  ],
  templateUrl: './dashboard.page.html',
  styleUrls: ['./dashboard.page.scss', '../mission-control/workspace-page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  protected readonly store = inject(MissionControlStore);
  private readonly workspaceSearch = inject(WorkspaceSearchStore);

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

  protected readonly ordersCompletedPerWeek = computed(() =>
    ordersCompletedPerWeek(this.store.data()?.executions ?? []),
  );
  protected readonly executionVolumeByDepartment = computed(() =>
    toDepartmentVolumeSlices(this.store.data()?.executionVolumeByDepartment ?? []),
  );

  protected readonly filteredExecutions = computed(() => {
    const executions = this.store.data()?.executions ?? [];
    const query = this.workspaceSearch.query().trim().toLowerCase();
    if (!query) {
      return executions;
    }
    return executions.filter(
      (execution) =>
        execution.purchaseOrderNumber.toLowerCase().includes(query) ||
        execution.customerName.toLowerCase().includes(query),
    );
  });

  protected isHealthyState(state: string): boolean {
    return state === 'RUNNING' || state === 'COMPLETED';
  }
}
