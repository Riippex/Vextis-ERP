import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { MissionControlStore } from '../mission-control/mission-control.store';

@Component({
  selector: 'vxt-crm-sales-page',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './crm-sales.page.html',
  styleUrl: '../mission-control/workspace-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CrmSalesPage {
  protected readonly store = inject(MissionControlStore);
  protected readonly activeCustomers = computed(
    () => this.store.data()?.customers.filter(({ active }) => active).length ?? 0,
  );
}
