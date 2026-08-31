import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { SetStockAvailabilityGQL } from '../../api/generated/graphql';

export interface EditStockDialogData { sku: string; availableQuantity: number }

@Component({
  selector: 'vxt-edit-stock-dialog',
  imports: [MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>Edit stock availability</h2>
    <mat-dialog-content>
      <p class="dialog-copy">Change authoritative availability without changing the SKU identity.</p>
      <form id="edit-stock-form" class="dialog-form" [formGroup]="form" (ngSubmit)="save()">
        <div class="dialog-identity"><strong>SKU</strong><br />{{ data.sku }}</div>
        <mat-form-field appearance="outline">
          <mat-label>Available quantity</mat-label>
          <input matInput type="number" min="0" max="1000000" formControlName="availableQuantity" />
          @if (form.controls.availableQuantity.invalid) { <mat-error>Quantity must be between 0 and 1,000,000.</mat-error> }
        </mat-form-field>
        @if (error()) { <p class="dialog-error" role="alert">{{ error() }}</p> }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" [disabled]="saving()">Cancel</button>
      <button mat-flat-button type="submit" form="edit-stock-form" [disabled]="saving()">
        @if (saving()) { <mat-spinner diameter="18" /> } Save changes
      </button>
    </mat-dialog-actions>
  `,
  styleUrl: '../../shared/dialogs/authoritative-edit-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditStockDialogComponent {
  protected readonly data = inject<EditStockDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<EditStockDialogComponent, boolean>);
  private readonly mutation = inject(SetStockAvailabilityGQL);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.formBuilder.group({ availableQuantity: [this.data.availableQuantity, [Validators.required, Validators.min(0), Validators.max(1_000_000)]] });

  protected save(): void {
    if (this.form.invalid || this.saving()) { this.form.markAllAsTouched(); return; }
    this.saving.set(true); this.error.set(null);
    this.mutation.mutate({ variables: { input: { sku: this.data.sku, availableQuantity: this.form.controls.availableQuantity.value } } })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: ({ data }) => data?.setStockAvailability ? this.dialogRef.close(true) : this.fail(),
        error: () => this.fail(),
      });
  }

  private fail(): void { this.saving.set(false); this.error.set('Stock availability could not be updated.'); }
}
