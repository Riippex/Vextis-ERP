import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { type CreditStanding, UpsertCreditProfileGQL } from '../../api/generated/graphql';

export interface EditCreditProfileDialogData { customerId: string; customerName: string; standing: CreditStanding; maxPaymentTermsDays: number }

@Component({
  selector: 'vxt-edit-credit-profile-dialog',
  imports: [MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, MatSelectModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>Edit commercial credit</h2>
    <mat-dialog-content>
      <p class="dialog-copy">Update the Finance-owned evidence that gates approval and invoicing.</p>
      <form id="edit-credit-form" class="dialog-form" [formGroup]="form" (ngSubmit)="save()">
        <div class="dialog-identity"><strong>Customer</strong><br />{{ data.customerName }}</div>
        <mat-form-field appearance="outline"><mat-label>Standing</mat-label><mat-select formControlName="standing">
          @for (standing of standings; track standing) { <mat-option [value]="standing">{{ standing }}</mat-option> }
        </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Maximum payment terms</mat-label>
          <input matInput type="number" min="0" max="365" formControlName="maxPaymentTermsDays" /><span matTextSuffix>days</span>
          @if (form.controls.maxPaymentTermsDays.invalid) { <mat-error>Terms must be between 0 and 365 days.</mat-error> }
        </mat-form-field>
        @if (error()) { <p class="dialog-error" role="alert">{{ error() }}</p> }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" [disabled]="saving()">Cancel</button>
      <button mat-flat-button type="submit" form="edit-credit-form" [disabled]="saving()">
        @if (saving()) { <mat-spinner diameter="18" /> } Save changes
      </button>
    </mat-dialog-actions>
  `,
  styleUrl: '../../shared/dialogs/authoritative-edit-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditCreditProfileDialogComponent {
  protected readonly data = inject<EditCreditProfileDialogData>(MAT_DIALOG_DATA);
  protected readonly standings: CreditStanding[] = ['GOOD', 'REVIEW', 'BLOCKED'];
  private readonly dialogRef = inject(MatDialogRef<EditCreditProfileDialogComponent, boolean>);
  private readonly mutation = inject(UpsertCreditProfileGQL);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.formBuilder.group({
    standing: [this.data.standing, Validators.required],
    maxPaymentTermsDays: [this.data.maxPaymentTermsDays, [Validators.required, Validators.min(0), Validators.max(365)]],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) { this.form.markAllAsTouched(); return; }
    this.saving.set(true); this.error.set(null);
    this.mutation.mutate({ variables: { input: { customerId: this.data.customerId, ...this.form.getRawValue() } } })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: ({ data }) => data?.upsertCreditProfile ? this.dialogRef.close(true) : this.fail(),
        error: () => this.fail(),
      });
  }

  private fail(): void { this.saving.set(false); this.error.set('The credit profile could not be saved for this customer.'); }
}
