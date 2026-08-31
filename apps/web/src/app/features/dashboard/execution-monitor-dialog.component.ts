import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, of, switchMap, timer } from 'rxjs';

import { DecideApprovalGQL, FindExecutionGQL, type FindExecutionQuery } from '../../api/generated/graphql';

type Execution = NonNullable<FindExecutionQuery['execution']>;
export interface ExecutionMonitorDialogData {
  id: string;
  purchaseOrderNumber: string;
  customerName: string;
}

@Component({
  selector: 'vxt-execution-monitor-dialog',
  imports: [DatePipe, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressSpinnerModule, ReactiveFormsModule],
  templateUrl: './execution-monitor-dialog.component.html',
  styleUrl: './execution-monitor-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExecutionMonitorDialogComponent {
  protected readonly data = inject<ExecutionMonitorDialogData>(MAT_DIALOG_DATA);
  private readonly findExecution = inject(FindExecutionGQL);
  private readonly decideApprovalMutation = inject(DecideApprovalGQL);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly execution = signal<Execution | null>(null);
  protected readonly loading = signal(true);
  protected readonly refreshing = signal(false);
  protected readonly deciding = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly decisionForm = this.formBuilder.group({ reason: ['', Validators.maxLength(500)] });

  constructor() {
    timer(0, 3_000).pipe(
      switchMap(() => this.findExecution.fetch({ variables: { id: this.data.id }, fetchPolicy: 'network-only' }).pipe(
        catchError(() => { this.error.set('Live status could not be refreshed. The monitor will keep retrying.'); return of(null); }),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((result) => {
      this.loading.set(false); this.refreshing.set(false);
      if (result?.data?.execution) { this.setExecutionIfNewer(result.data.execution); this.error.set(null); }
      else if (result && !result.data?.execution) { this.error.set('This execution is no longer available in the current workspace.'); }
    });
  }

  protected refresh(): void {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.findExecution.fetch({ variables: { id: this.data.id }, fetchPolicy: 'network-only' })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: ({ data }) => { this.refreshing.set(false); if (data?.execution) this.setExecutionIfNewer(data.execution); },
        error: () => { this.refreshing.set(false); this.error.set('The execution could not be refreshed.'); },
      });
  }

  protected decide(decision: 'APPROVE' | 'REJECT'): void {
    const execution = this.execution();
    if (!execution?.approval || execution.approval.status !== 'PENDING' || this.deciding()) return;
    this.deciding.set(true); this.error.set(null);
    this.decideApprovalMutation.mutate({ variables: { input: {
      executionId: execution.id,
      approvalId: execution.approval.id,
      decision,
      reason: this.decisionForm.controls.reason.value.trim() || null,
      idempotencyKey: `decide-approval-${globalThis.crypto.randomUUID()}`,
    } } }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ data }) => { this.deciding.set(false); if (data?.decideApproval) this.setExecutionIfNewer(data.decideApproval); },
      error: (error: unknown) => { this.deciding.set(false); this.error.set(error instanceof Error ? error.message : 'The approval decision could not be saved.'); },
    });
  }

  protected isTerminal(state: string): boolean { return state === 'COMPLETED' || state === 'FAILED'; }

  private setExecutionIfNewer(candidate: Execution): void {
    const current = this.execution();
    if (!current || Date.parse(candidate.updatedAt) >= Date.parse(current.updatedAt)) {
      this.execution.set(candidate);
    }
  }
}
