import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';

import {
  type CreditStanding,
  UpsertCreditProfileGQL,
} from '../../api/generated/graphql';
import { WorkspaceSkeletonComponent } from '../../shared/loading/workspace-skeleton.component';
import { MissionControlStore } from '../mission-control/mission-control.store';

@Component({
  selector: 'vxt-finance-billing-page',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
    RouterLink,
    WorkspaceSkeletonComponent,
  ],
  templateUrl: './finance-billing.page.html',
  styleUrl: '../mission-control/workspace-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinanceBillingPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly mutation = inject(UpsertCreditProfileGQL);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly store = inject(MissionControlStore);
  protected readonly standings: CreditStanding[] = ['GOOD', 'REVIEW', 'BLOCKED'];
  protected readonly saving = signal(false);
  protected readonly saveMessage = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly creditForm = this.formBuilder.group({
    customerId: ['', Validators.required],
    standing: ['REVIEW' as CreditStanding, Validators.required],
    maxPaymentTermsDays: [30, [Validators.required, Validators.min(0), Validators.max(365)]],
  });
  protected readonly goodStanding = computed(
    () => this.store.data()?.creditProfiles.filter(({ standing }) => standing === 'GOOD').length ?? 0,
  );

  protected editProfile(profile: {
    customerId: string;
    standing: CreditStanding;
    maxPaymentTermsDays: number;
  }): void {
    this.creditForm.setValue({
      customerId: profile.customerId,
      standing: profile.standing,
      maxPaymentTermsDays: profile.maxPaymentTermsDays,
    });
    this.clearFeedback();
  }

  protected saveCreditProfile(): void {
    if (this.creditForm.invalid || this.saving()) {
      this.creditForm.markAllAsTouched();
      return;
    }
    const value = this.creditForm.getRawValue();
    this.saving.set(true);
    this.clearFeedback();
    this.mutation
      .mutate({ variables: { input: value } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          const saved = data?.upsertCreditProfile;
          this.saveMessage.set(
            saved ? `${saved.customerName}'s credit profile was saved.` : 'Credit profile saved.',
          );
          this.creditForm.reset({ customerId: '', standing: 'REVIEW', maxPaymentTermsDays: 30 });
          this.saving.set(false);
          this.store.refresh();
        },
        error: () => {
          this.saveError.set('The credit profile could not be saved for this customer.');
          this.saving.set(false);
        },
      });
  }

  private clearFeedback(): void {
    this.saveMessage.set(null);
    this.saveError.set(null);
  }
}
