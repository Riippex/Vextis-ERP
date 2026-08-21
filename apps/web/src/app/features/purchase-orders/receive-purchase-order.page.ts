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

import {
  ReceivePurchaseOrderGQL,
  type ReceivePurchaseOrderMutation,
} from '../../api/generated/graphql';

type PurchaseOrderReceipt = ReceivePurchaseOrderMutation['receivePurchaseOrder'];

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
  private readonly destroyRef = inject(DestroyRef);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly receipt = signal<PurchaseOrderReceipt | null>(null);

  protected readonly form = this.formBuilder.group({
    purchaseOrderNumber: ['PO-2026-001', [Validators.required, Validators.maxLength(100)]],
    customerName: ['Acme Colombia', [Validators.required, Validators.maxLength(200)]],
    documentUri: [
      'gs://vextis-demo/orders/po-2026-001.pdf',
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
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(this.toActionableMessage(error));
        },
      });
  }

  protected startAnother(): void {
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

  private toActionableMessage(error: unknown): string {
    if (error instanceof Error && error.message.trim()) {
      return `The order could not be received: ${error.message}`;
    }
    return 'The order could not be received. Verify Enterprise Core and try again.';
  }
}
