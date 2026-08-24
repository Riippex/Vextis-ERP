import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { Subscription, switchMap, takeWhile, timer } from 'rxjs';

import {
  FindExecutionGQL,
  ReceivePurchaseOrderGQL,
  type FindExecutionQuery,
  type ReceivePurchaseOrderMutation,
} from '../../api/generated/graphql';

type PurchaseOrderReceipt = ReceivePurchaseOrderMutation['receivePurchaseOrder'];
type Execution = NonNullable<FindExecutionQuery['execution']>;

@Component({
  selector: 'vxt-receive-purchase-order-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './receive-purchase-order.page.html',
  styleUrl: './receive-purchase-order.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceivePurchaseOrderPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly receivePurchaseOrder = inject(ReceivePurchaseOrderGQL);
  private readonly findExecution = inject(FindExecutionGQL);
  private readonly destroyRef = inject(DestroyRef);
  private monitoringSubscription?: Subscription;

  protected readonly submitting = signal(false);
  protected readonly monitoring = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly receipt = signal<PurchaseOrderReceipt | null>(null);

  protected readonly form = this.formBuilder.group({
    purchaseOrderNumber: ['PO-2026-001', [Validators.required, Validators.maxLength(100)]],
    customerName: ['Acme Colombia', [Validators.required, Validators.maxLength(200)]],
    documentUri: [
      'gs://vextis-erp-hackathon-assets/demo/purchase-orders/PO-2026-001.pdf',
      [Validators.required, Validators.pattern(/^gs:\/\/.+/), Validators.maxLength(1000)],
    ],
    idempotencyKey: [this.newIdempotencyKey(), [Validators.required, Validators.maxLength(200)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.receivePurchaseOrder
      .mutate({ variables: { input: this.form.getRawValue() } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          this.submitting.set(false);
          if (!data) {
            this.errorMessage.set('Enterprise Core did not return a receipt. Try again.');
            return;
          }
          this.receipt.set(data.receivePurchaseOrder);
          this.monitorExecution(data.receivePurchaseOrder.execution.id);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(this.toActionableMessage(error));
        },
      });
  }

  protected startAnother(): void {
    this.monitoringSubscription?.unsubscribe();
    this.monitoring.set(false);
    this.receipt.set(null);
    this.errorMessage.set(null);
    this.form.reset({
      purchaseOrderNumber: '',
      customerName: '',
      documentUri: '',
      idempotencyKey: this.newIdempotencyKey(),
    });
  }

  private newIdempotencyKey(): string {
    return `receive-po-${globalThis.crypto.randomUUID()}`;
  }

  private monitorExecution(executionId: string): void {
    this.monitoringSubscription?.unsubscribe();
    this.monitoring.set(true);
    this.monitoringSubscription = timer(0, 2_000)
      .pipe(
        switchMap(() =>
          this.findExecution.fetch({
            variables: { id: executionId },
            fetchPolicy: 'network-only',
          }),
        ),
        takeWhile(({ data }) => this.isAwaitingReadiness(data?.execution), true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ data }) => {
          if (!data?.execution) {
            return;
          }
          this.updateExecution(data.execution);
          if (!this.isAwaitingReadiness(data.execution)) {
            this.monitoring.set(false);
          }
        },
        error: () => {
          this.monitoring.set(false);
          this.errorMessage.set(
            'The order was received, but its live status could not be refreshed.',
          );
        },
        complete: () => this.monitoring.set(false),
      });
  }

  private updateExecution(execution: Execution): void {
    this.receipt.update((current) =>
      current ? { ...current, execution } : current,
    );
  }

  private isAwaitingReadiness(execution: Execution | null | undefined): boolean {
    return (
      execution?.state === 'RECEIVED' ||
      execution?.state === 'PLANNING' ||
      (execution?.state === 'RUNNING' && !execution.readiness)
    );
  }

  private toActionableMessage(error: unknown): string {
    if (error instanceof Error && error.message.trim()) {
      return `The order could not be received: ${error.message}`;
    }
    return 'The order could not be received. Verify Enterprise Core and try again.';
  }
}
