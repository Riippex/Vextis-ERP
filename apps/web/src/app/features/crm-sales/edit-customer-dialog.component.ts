import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { UpsertCustomerGQL } from '../../api/generated/graphql';

export interface EditCustomerDialogData { id: string; legalName: string; active: boolean }

@Component({
  selector: 'vxt-edit-customer-dialog',
  imports: [MatButtonModule, MatCheckboxModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>Edit customer</h2>
    <mat-dialog-content>
      <p class="dialog-copy">Update the CRM-owned customer record used by every governed workflow.</p>
      <form id="edit-customer-form" class="dialog-form" [formGroup]="form" (ngSubmit)="save()">
        <mat-form-field appearance="outline">
          <mat-label>Legal name</mat-label>
          <input matInput formControlName="legalName" maxlength="200" autocomplete="organization" />
          @if (form.controls.legalName.invalid) { <mat-error>Enter a legal name with at most 200 characters.</mat-error> }
        </mat-form-field>
        <mat-checkbox formControlName="active">Active for workflow matching</mat-checkbox>
        @if (error()) { <p class="dialog-error" role="alert">{{ error() }}</p> }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" [disabled]="saving()">Cancel</button>
      <button mat-flat-button type="submit" form="edit-customer-form" [disabled]="saving()">
        @if (saving()) { <mat-spinner diameter="18" /> } Save changes
      </button>
    </mat-dialog-actions>
  `,
  styleUrl: '../../shared/dialogs/authoritative-edit-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditCustomerDialogComponent {
  private readonly data = inject<EditCustomerDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<EditCustomerDialogComponent, boolean>);
  private readonly mutation = inject(UpsertCustomerGQL);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.formBuilder.group({
    legalName: [this.data.legalName, [Validators.required, Validators.maxLength(200)]],
    active: [this.data.active],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) { this.form.markAllAsTouched(); return; }
    const value = this.form.getRawValue();
    this.saving.set(true); this.error.set(null);
    this.mutation.mutate({ variables: { input: { id: this.data.id, legalName: value.legalName.trim(), active: value.active } } })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: ({ data }) => data?.upsertCustomer ? this.dialogRef.close(true) : this.fail(),
        error: () => this.fail(),
      });
  }

  private fail(): void { this.saving.set(false); this.error.set('The customer could not be saved. Review the legal name and try again.'); }
}
