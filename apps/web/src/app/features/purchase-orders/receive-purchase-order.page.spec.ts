import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import {
  DecideApprovalGQL,
  FindExecutionGQL,
  PreparePurchaseOrderUploadGQL,
  ReceivePurchaseOrderGQL,
} from '../../api/generated/graphql';
import { ReceivePurchaseOrderPage } from './receive-purchase-order.page';

describe('ReceivePurchaseOrderPage', () => {
  const receiveMutate = vi.fn().mockReturnValue(
    of({
      data: {
        receivePurchaseOrder: {
          purchaseOrder: {
            id: '77cc63cc-3c91-4d80-a918-605b7f231cf8',
            purchaseOrderNumber: 'PO-2026-001',
            customerName: 'Acme Colombia',
            documentUri:
              'gs://vextis-erp-hackathon-assets/demo/purchase-orders/PO-2026-001.pdf',
            receivedAt: '2026-08-21T03:30:00Z',
          },
          execution: {
            id: '8d3f290d-1322-44a2-8bd7-3b325f170e07',
            goal: 'Process PO-2026-001',
            state: 'RECEIVED',
            correlationId: 'corr-001',
            createdAt: '2026-08-21T03:30:00Z',
            updatedAt: '2026-08-21T03:30:00Z',
            timeline: [
              {
                sequence: 1,
                type: 'RECEIVED',
                title: 'Order received',
                detail: 'Ready for agent planning.',
                occurredAt: '2026-08-21T03:30:00Z',
              },
            ],
            plan: null,
            readiness: null,
            auditTrail: [
              {
                id: 'audit-user-1',
                correlationId: 'corr-001',
                actorType: 'USER',
                actorId: 'firebase-user-123',
                action: 'RECEIVE_PURCHASE_ORDER',
                toolName: null,
                resourceType: 'PURCHASE_ORDER',
                resourceId: '77cc63cc-3c91-4d80-a918-605b7f231cf8',
                result: 'SUCCEEDED',
                occurredAt: '2026-08-21T03:30:00Z',
                approvedAgent: null,
              },
            ],
          },
        },
      },
    }),
  );
  const prepareMutate = vi.fn().mockReturnValue(
    of({
      data: {
        preparePurchaseOrderUpload: {
          uploadUrl: 'https://storage.googleapis.com/signed-upload',
          documentUri:
            'gs://vextis-erp-hackathon-assets/purchase-orders/tenant/document.pdf',
          expiresAt: '2026-08-21T03:40:00Z',
          formFields: [{ name: 'Content-Type', value: 'application/pdf' }],
        },
      },
    }),
  );
  const upload = vi.fn().mockReturnValue(of(''));
  const fetch = vi.fn().mockReturnValue(
    of({
      data: {
        execution: {
          id: '8d3f290d-1322-44a2-8bd7-3b325f170e07',
          goal: 'Process PO-2026-001',
          state: 'RUNNING',
          correlationId: 'corr-001',
          createdAt: '2026-08-21T03:30:00Z',
          updatedAt: '2026-08-21T03:30:02Z',
          timeline: [
            {
              sequence: 1,
              type: 'RECEIVED',
              title: 'Order received',
              detail: 'Ready for agent planning.',
              occurredAt: '2026-08-21T03:30:00Z',
            },
            {
              sequence: 2,
              type: 'STATUS_CHANGED',
              title: 'Agent planning started',
              detail: 'Agent Runtime accepted the event.',
              occurredAt: '2026-08-21T03:30:02Z',
            },
            {
              sequence: 3,
              type: 'STATUS_CHANGED',
              title: 'Structured plan recorded',
              detail: 'Gemini produced a validated plan with 3 steps.',
              occurredAt: '2026-08-21T03:30:04Z',
            },
          ],
          plan: {
            summary: 'Validate customer, inventory, and commercial terms.',
            modelId: 'gemini-3.5-flash',
            generatedAt: '2026-08-21T03:30:04Z',
            requestedPaymentTermsDays: 30,
            orderLines: [{ sku: 'VXT-CHAIR-01', quantity: 10 }],
            steps: [
              {
                sequence: 1,
                department: 'CRM_SALES',
                objective: 'Validate customer context.',
                requiresApproval: false,
              },
              {
                sequence: 2,
                department: 'INVENTORY_OPERATIONS',
                objective: 'Check requested products and availability.',
                requiresApproval: false,
              },
              {
                sequence: 3,
                department: 'FINANCE_BILLING',
                objective: 'Validate commercial terms.',
                requiresApproval: true,
              },
            ],
          },
          readiness: {
            evaluatedAt: '2026-08-21T03:30:06Z',
            checks: [
              {
                department: 'CRM_SALES',
                status: 'READY',
                detail: 'Active customer matched: Acme Colombia.',
              },
              {
                department: 'INVENTORY_OPERATIONS',
                status: 'READY',
                detail: 'All extracted SKU lines have sufficient stock.',
              },
              {
                department: 'FINANCE_BILLING',
                status: 'READY',
                detail: 'Credit standing is good.',
              },
            ],
          },
          auditTrail: [
            {
              id: 'audit-agent-1',
              correlationId: 'corr-001',
              actorType: 'AGENT',
              actorId: 'vextis_coordinator',
              action: 'RECORD_EXECUTION_PLAN',
              toolName: 'record_execution_plan',
              resourceType: 'WORKFLOW_EXECUTION',
              resourceId: '8d3f290d-1322-44a2-8bd7-3b325f170e07',
              result: 'SUCCEEDED',
              occurredAt: '2026-08-21T03:30:04Z',
              approvedAgent: {
                agentId: 'vextis_coordinator',
                version: '1.0.0',
                displayName: 'Vextis Coordinator',
                modelId: 'gemini-3.5-flash',
                promptVersion: '1.0.0',
                serviceIdentity: 'coordinator-agent',
              },
            },
          ],
        },
      },
    }),
  );

  beforeEach(async () => {
    receiveMutate.mockClear();
    prepareMutate.mockClear();
    upload.mockClear();
    fetch.mockClear();
    await TestBed.configureTestingModule({
      imports: [ReceivePurchaseOrderPage],
      providers: [
        provideRouter([]),
        { provide: ReceivePurchaseOrderGQL, useValue: { mutate: receiveMutate } },
        { provide: PreparePurchaseOrderUploadGQL, useValue: { mutate: prepareMutate } },
        { provide: HttpClient, useValue: { post: upload } },
        { provide: FindExecutionGQL, useValue: { fetch } },
        { provide: DecideApprovalGQL, useValue: { mutate: vi.fn() } },
      ],
    }).compileComponents();
  });

  it('submits the intake and renders evidence returned by Enterprise Core', async () => {
    const fixture = TestBed.createComponent(ReceivePurchaseOrderPage);
    fixture.detectChanges();

    const fileInput = fixture.nativeElement.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    Object.defineProperty(fileInput, 'files', {
      configurable: true,
      value: [new File(['purchase order'], 'customer-order.pdf', { type: 'application/pdf' })],
    });
    fileInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const submit = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    submit.click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    expect(prepareMutate).toHaveBeenCalledOnce();
    expect(upload).toHaveBeenCalledOnce();
    expect(receiveMutate).toHaveBeenCalledOnce();
    expect(upload.mock.calls[0]?.[0]).toBe('https://storage.googleapis.com/signed-upload');
    const formData = upload.mock.calls[0]?.[1] as FormData;
    expect(formData.get('Content-Type')).toBe('application/pdf');
    expect(formData.get('file')).toBeInstanceOf(File);
    expect(receiveMutate.mock.calls[0]?.[0].variables.input.documentUri).toBe(
      'gs://vextis-erp-hackathon-assets/purchase-orders/tenant/document.pdf',
    );
    expect(fetch).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Enterprise Core receipt');
    expect(fixture.nativeElement.textContent).toContain('corr-001');
    expect(fixture.nativeElement.textContent).toContain('Order received');
    expect(fixture.nativeElement.textContent).toContain('RUNNING');
    expect(fixture.nativeElement.textContent).toContain('Agent planning started');
    expect(fixture.nativeElement.textContent).toContain('gemini-3.5-flash');
    expect(fixture.nativeElement.textContent).toContain('Human approval required');
    expect(fixture.nativeElement.textContent).toContain('VXT-CHAIR-01');
    expect(fixture.nativeElement.textContent).toContain('Order readiness');
    expect(fixture.nativeElement.textContent).toContain('Credit standing is good');
    expect(fixture.nativeElement.textContent).toContain('Agent and user audit trail');
    expect(fixture.nativeElement.textContent).toContain('Record execution plan');
    expect(fixture.nativeElement.textContent).toContain('Vextis Coordinator');
    expect(fixture.nativeElement.textContent).toContain('prompt v1.0.0');
  });

  it('rejects unsupported files before requesting an upload policy', () => {
    const fixture = TestBed.createComponent(ReceivePurchaseOrderPage);
    fixture.detectChanges();

    const fileInput = fixture.nativeElement.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    Object.defineProperty(fileInput, 'files', {
      configurable: true,
      value: [new File(['svg'], 'order.svg', { type: 'image/svg+xml' })],
    });
    fileInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(prepareMutate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Only PDF, JPEG, and PNG purchase orders are supported.',
    );
  });
});
