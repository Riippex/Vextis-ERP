import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { MissionControlStore } from '../mission-control/mission-control.store';

@Component({
  selector: 'vxt-inventory-operations-page',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './inventory-operations.page.html',
  styleUrl: '../mission-control/workspace-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryOperationsPage {
  protected readonly store = inject(MissionControlStore);
  protected readonly availableUnits = computed(
    () =>
      this.store.data()?.stockItems.reduce((total, item) => total + item.availableQuantity, 0) ?? 0,
  );
}
