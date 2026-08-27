import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
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
import { map, Subscription, switchMap, take, takeWhile, timer } from 'rxjs';

import {
  FindExecutionGQL,
  DecideApprovalGQL,
  PreparePurchaseOrderUploadGQL,
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
    DecimalPipe,
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
  private readonly preparePurchaseOrderUpload = inject(PreparePurchaseOrderUploadGQL);
  private readonly http = inject(HttpClient);
  private readonly findExecution = inject(FindExecutionGQL);
  private readonly decideApprovalMutation = inject(DecideApprovalGQL);
  private readonly destroyRef = inject(DestroyRef);
  private monitoringSubscription?: Subscription;

  protected readonly submitting = signal(false);
  protected readonly monitoring = signal(false);
  protected readonly deciding = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly receipt = signal<PurchaseOrderReceipt | null>(null);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly submissionStage = signal<'idle' | 'authorizing' | 'uploading' | 'receiving'>('idle');

  protected readonly form = this.formBuilder.group({
    purchaseOrderNumber: ['PO-2026-001', [Validators.required, Validators.maxLength(100)]],
    customerName: ['Acme Colombia', [Validators.required, Validators.maxLength(200)]],
    idempotencyKey: [this.newIdempotencyKey(), [Validators.required, Validators.maxLength(200)]],
  });

  protected readonly decisionForm = this.formBuilder.group({
    reason: ['', [Validators.maxLength(500)]],
  });

  protected submit(): void {
    const document = this.selectedFile();
    if (this.form.invalid || document === null || this.submitting()) {
      this.form.markAllAsTouched();
      if (document === null) {
        this.errorMessage.set('Choose a PDF, JPEG, or PNG purchase order first.');
      }
      return;
    }

    this.submitting.set(true);
    this.submissionStage.set('authorizing');
    this.errorMessage.set(null);
    const routing = this.form.getRawValue();
    this.preparePurchaseOrderUpload
      .mutate({
        variables: {
          input: {
            fileName: document.name,
            contentType: document.type,
            sizeBytes: document.size,
          },
        },
      })
      .pipe(
        switchMap(({ data }) => {
          const upload = data?.preparePurchaseOrderUpload;
          if (!upload) {
            throw new Error('Enterprise Core did not authorize the document upload.');
          }
          this.submissionStage.set('uploading');
          const formData = new FormData();
          for (const field of upload.formFields) {
            formData.append(field.name, field.value);
          }
          formData.append('file', document);
          return this.http
            .post(upload.uploadUrl, formData, { responseType: 'text' })
            .pipe(map(() => upload.documentUri));
        }),
        switchMap((documentUri) => {
          this.submissionStage.set('receiving');
          return this.receivePurchaseOrder.mutate({
            variables: { input: { ...routing, documentUri } },
          });
        }),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          this.submitting.set(false);
          this.submissionStage.set('idle');
          if (!data) {
            this.errorMessage.set('Enterprise Core did not return a receipt. Try again.');
            return;
          }
          this.receipt.set(data.receivePurchaseOrder);
          this.monitorExecution(data.receivePurchaseOrder.execution.id);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.submissionStage.set('idle');
          this.errorMessage.set(this.toActionableMessage(error));
        },
      });
  }

  protected startAnother(): void {
    this.monitoringSubscription?.unsubscribe();
    this.monitoring.set(false);
    this.receipt.set(null);
    this.selectedFile.set(null);
    this.errorMessage.set(null);
    this.form.reset({
      purchaseOrderNumber: '',
      customerName: '',
      idempotencyKey: this.newIdempotencyKey(),
    });
  }

  protected selectDocument(event: Event): void {
    const input = event.target as HTMLInputElement;
    const document = input.files?.[0] ?? null;
    this.errorMessage.set(null);
    if (document === null) {
      this.selectedFile.set(null);
      return;
    }
    if (!['application/pdf', 'image/jpeg', 'image/png'].includes(document.type)) {
      input.value = '';
      this.selectedFile.set(null);
      this.errorMessage.set('Only PDF, JPEG, and PNG purchase orders are supported.');
      return;
    }
    if (document.size < 1 || document.size > 10 * 1024 * 1024) {
      input.value = '';
      this.selectedFile.set(null);
      this.errorMessage.set('The purchase order must be smaller than 10 MiB.');
      return;
    }
    this.selectedFile.set(document);
  }

  protected submissionLabel(): string {
    switch (this.submissionStage()) {
      case 'authorizing':
        return 'Authorizing upload…';
      case 'uploading':
        return 'Uploading securely…';
      case 'receiving':
        return 'Creating execution…';
      default:
        return 'Upload and create execution';
    }
  }

  protected auditActionLabel(action: string): string {
    const normalized = action.replaceAll('_', ' ').toLowerCase();
    return normalized.charAt(0).toUpperCase() + normalized.slice(1);
  }

  protected decideApproval(decision: 'APPROVE' | 'REJECT'): void {
    const execution = this.receipt()?.execution;
    if (!execution?.approval || execution.approval.status !== 'PENDING' || this.deciding()) {
      return;
    }
    this.deciding.set(true);
    this.errorMessage.set(null);
    this.decideApprovalMutation.mutate({
      variables: {
        input: {
          executionId: execution.id,
          approvalId: execution.approval.id,
          decision,
          reason: this.decisionForm.controls.reason.value.trim() || null,
          idempotencyKey: `decide-approval-${globalThis.crypto.randomUUID()}`,
        },
      },
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ data }) => {
        this.deciding.set(false);
        if (data?.decideApproval) {
          this.updateExecution(data.decideApproval);
          if (decision === 'APPROVE') {
            this.monitorExecution(data.decideApproval.id);
          }
        }
      },
      error: (error: unknown) => {
        this.deciding.set(false);
        this.errorMessage.set(this.toActionableMessage(error));
      },
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
        take(61),
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
            if (
              data.execution.state === 'RUNNING' &&
              data.execution.readiness != null &&
              !data.execution.approval &&
              !this.hasInvoicePricing(data.execution)
            ) {
              this.errorMessage.set(
                'The extracted order needs a currency and a unit price for every line before approval.',
              );
            }
          }
        },
        error: () => {
          this.monitoring.set(false);
          this.errorMessage.set(
            'The order was received, but its live status could not be refreshed.',
          );
        },
        complete: () => {
          if (this.monitoring()) {
            this.monitoring.set(false);
            if (this.isAwaitingReadiness(this.receipt()?.execution)) {
              this.errorMessage.set(
                'Live processing did not finish within two minutes. Refresh the execution before retrying.',
              );
            }
          }
        },
      });
  }

  private updateExecution(execution: Execution): void {
    this.receipt.update((current) =>
      current ? { ...current, execution } : current,
    );
  }

  protected invoicePreview(plan: NonNullable<Execution['plan']>): {
    subtotal: string;
    tax: string;
    total: string;
  } | null {
    if (!plan.currency || plan.orderLines.some((line) => line.unitPrice == null)) {
      return null;
    }
    let subtotal = 0n;
    for (const line of plan.orderLines) {
      const unitPrice = this.toCents(line.unitPrice);
      if (unitPrice === null) {
        return null;
      }
      subtotal += unitPrice * BigInt(line.quantity);
    }
    const tax = (subtotal * 19n + 50n) / 100n;
    return {
      subtotal: this.formatCents(subtotal),
      tax: this.formatCents(tax),
      total: this.formatCents(subtotal + tax),
    };
  }

  private toCents(value: unknown): bigint | null {
    const normalized = String(value);
    const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(normalized);
    if (!match) {
      return null;
    }
    return BigInt(match[1]) * 100n + BigInt((match[2] ?? '').padEnd(2, '0'));
  }

  private formatCents(value: bigint): string {
    return `${value / 100n}.${String(value % 100n).padStart(2, '0')}`;
  }

  private isAwaitingReadiness(execution: Execution | null | undefined): boolean {
    return (
      execution?.state === 'RECEIVED' ||
      execution?.state === 'PLANNING' ||
      (execution?.state === 'RUNNING' && !execution.readiness) ||
      (execution?.state === 'RUNNING' &&
        execution.readiness != null &&
        !execution.approval &&
        this.hasInvoicePricing(execution)) ||
      (execution?.approval?.status === 'APPROVED' && execution.invoice == null)
    );
  }

  private hasInvoicePricing(execution: Execution): boolean {
    return Boolean(
      execution.plan?.currency &&
        execution.plan.orderLines.every((line) => line.unitPrice != null),
    );
  }

  private toActionableMessage(error: unknown): string {
    if (error instanceof Error && error.message.trim()) {
      return `The order could not be received: ${error.message}`;
    }
    return 'The order could not be received. Verify Enterprise Core and try again.';
  }
}
