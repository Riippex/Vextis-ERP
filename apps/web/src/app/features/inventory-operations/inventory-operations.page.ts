import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { SetStockAvailabilityGQL } from '../../api/generated/graphql';
import { WorkspaceSkeletonComponent } from '../../shared/loading/workspace-skeleton.component';
import { MissionControlStore } from '../mission-control/mission-control.store';

@Component({
  selector: 'vxt-inventory-operations-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
    WorkspaceSkeletonComponent,
  ],
  templateUrl: './inventory-operations.page.html',
  styleUrl: '../mission-control/workspace-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryOperationsPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly mutation = inject(SetStockAvailabilityGQL);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly store = inject(MissionControlStore);
  protected readonly saving = signal(false);
  protected readonly saveMessage = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly stockForm = this.formBuilder.group({
    sku: ['', [Validators.required, Validators.maxLength(100), Validators.pattern('[A-Za-z0-9._-]+')]],
    availableQuantity: [0, [Validators.required, Validators.min(0), Validators.max(1_000_000)]],
  });
  protected readonly availableUnits = computed(
    () =>
      this.store.data()?.stockItems.reduce((total, item) => total + item.availableQuantity, 0) ?? 0,
  );

  protected editStock(item: { sku: string; availableQuantity: number }): void {
    this.stockForm.setValue({ sku: item.sku, availableQuantity: item.availableQuantity });
    this.clearFeedback();
  }

  protected saveStock(): void {
    if (this.stockForm.invalid || this.saving()) {
      this.stockForm.markAllAsTouched();
      return;
    }
    const value = this.stockForm.getRawValue();
    this.saving.set(true);
    this.clearFeedback();
    this.mutation
      .mutate({
        variables: {
          input: {
            sku: value.sku.trim(),
            availableQuantity: value.availableQuantity,
          },
        },
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          const saved = data?.setStockAvailability;
          this.saveMessage.set(saved ? `${saved.sku} availability was updated.` : 'Stock updated.');
          this.stockForm.reset({ sku: '', availableQuantity: 0 });
          this.saving.set(false);
          this.store.refresh();
        },
        error: () => {
          this.saveError.set('Stock availability could not be updated.');
          this.saving.set(false);
        },
      });
  }

  private clearFeedback(): void {
    this.saveMessage.set(null);
    this.saveError.set(null);
  }
}
