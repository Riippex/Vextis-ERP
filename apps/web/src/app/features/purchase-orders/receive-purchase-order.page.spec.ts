import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { FindExecutionGQL, ReceivePurchaseOrderGQL } from '../../api/generated/graphql';
import { ReceivePurchaseOrderPage } from './receive-purchase-order.page';

describe('ReceivePurchaseOrderPage', () => {
  const mutate = vi.fn().mockReturnValue(
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
          },
        },
      },
    }),
  );
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
        },
      },
    }),
  );

  beforeEach(async () => {
    mutate.mockClear();
    fetch.mockClear();
    await TestBed.configureTestingModule({
      imports: [ReceivePurchaseOrderPage],
      providers: [
        provideRouter([]),
        { provide: ReceivePurchaseOrderGQL, useValue: { mutate } },
        { provide: FindExecutionGQL, useValue: { fetch } },
      ],
    }).compileComponents();
  });

  it('submits the intake and renders evidence returned by Enterprise Core', async () => {
    const fixture = TestBed.createComponent(ReceivePurchaseOrderPage);
    fixture.detectChanges();

    const submit = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    submit.click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    expect(mutate).toHaveBeenCalledOnce();
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
  });
});
