import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ReceivePurchaseOrderGQL } from '../../api/generated/graphql';
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
            documentUri: 'gs://vextis-demo/orders/po-2026-001.pdf',
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
          },
        },
      },
    }),
  );

  beforeEach(async () => {
    mutate.mockClear();
    await TestBed.configureTestingModule({
      imports: [ReceivePurchaseOrderPage],
      providers: [
        provideRouter([]),
        { provide: ReceivePurchaseOrderGQL, useValue: { mutate } },
      ],
    }).compileComponents();
  });

  it('submits the intake and renders evidence returned by Enterprise Core', () => {
    const fixture = TestBed.createComponent(ReceivePurchaseOrderPage);
    fixture.detectChanges();

    const submit = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    submit.click();
    fixture.detectChanges();

    expect(mutate).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Enterprise Core receipt');
    expect(fixture.nativeElement.textContent).toContain('corr-001');
    expect(fixture.nativeElement.textContent).toContain('Order received');
  });
});
