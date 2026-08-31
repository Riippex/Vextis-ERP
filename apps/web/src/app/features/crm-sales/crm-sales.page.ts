import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { UpsertCustomerGQL } from '../../api/generated/graphql';
import { WorkspaceSkeletonComponent } from '../../shared/loading/workspace-skeleton.component';
import { MissionControlStore } from '../mission-control/mission-control.store';
import { EditCustomerDialogComponent } from './edit-customer-dialog.component';

@Component({
  selector: 'vxt-crm-sales-page',
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
    WorkspaceSkeletonComponent,
  ],
  templateUrl: './crm-sales.page.html',
  styleUrl: '../mission-control/workspace-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CrmSalesPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly mutation = inject(UpsertCustomerGQL);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);

  protected readonly store = inject(MissionControlStore);
  protected readonly saving = signal(false);
  protected readonly saveMessage = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly customerForm = this.formBuilder.group({
    legalName: ['', [Validators.required, Validators.maxLength(200)]],
    active: [true],
  });
  protected readonly activeCustomers = computed(
    () => this.store.data()?.customers.filter(({ active }) => active).length ?? 0,
  );

  protected editCustomer(customer: { id: string; legalName: string; active: boolean }): void {
    this.clearFeedback();
    this.dialog.open(EditCustomerDialogComponent, { data: customer, width: '36rem', maxWidth: '94vw' })
      .afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((saved) => {
        if (saved) { this.saveMessage.set(`${customer.legalName} was updated in CRM.`); this.store.refresh(); }
      });
  }

  protected saveCustomer(): void {
    if (this.customerForm.invalid || this.saving()) {
      this.customerForm.markAllAsTouched();
      return;
    }
    const value = this.customerForm.getRawValue();
    this.saving.set(true);
    this.clearFeedback();
    this.mutation
      .mutate({
        variables: {
          input: {
            id: null,
            legalName: value.legalName.trim(),
            active: value.active,
          },
        },
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          const saved = data?.upsertCustomer;
          this.saveMessage.set(saved ? `${saved.legalName} was saved in CRM.` : 'Customer saved.');
          this.customerForm.reset({ legalName: '', active: true });
          this.saving.set(false);
          this.store.refresh();
        },
        error: () => {
          this.saveError.set('The customer could not be saved. Review the legal name and try again.');
          this.saving.set(false);
        },
      });
  }

  private clearFeedback(): void {
    this.saveMessage.set(null);
    this.saveError.set(null);
  }
}
