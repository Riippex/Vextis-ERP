import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MissionControlStore } from '../mission-control/mission-control.store';
import { DashboardPage } from './dashboard.page';

describe('DashboardPage', () => {
  const store = {
    data: signal({
      executions: [
        {
          id: '8d3f290d-1322-44a2-8bd7-3b325f170e07',
          purchaseOrderNumber: 'PO-2026-001',
          customerName: 'Acme Colombia',
          state: 'RUNNING' as const,
          correlationId: 'corr-001',
          updatedAt: '2026-08-24T17:00:00Z',
        },
      ],
      customers: [
        { id: '11111111-1111-1111-1111-111111111111', legalName: 'Acme Colombia', active: true },
      ],
      stockItems: [
        { sku: 'VXT-CHAIR-01', availableQuantity: 40 },
        { sku: 'VXT-DESK-01', availableQuantity: 12 },
      ],
      creditProfiles: [
        {
          customerId: '11111111-1111-1111-1111-111111111111',
          customerName: 'Acme Colombia',
          standing: 'GOOD' as const,
          maxPaymentTermsDays: 30,
        },
      ],
    }),
    loading: signal(false),
    error: signal<string | null>(null),
    refresh: vi.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [provideRouter([]), { provide: MissionControlStore, useValue: store }],
    }).compileComponents();
  });

  it('renders live operational metrics and department navigation', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('PO-2026-001');
    expect(fixture.nativeElement.textContent).toContain('Acme Colombia');
    expect(fixture.nativeElement.textContent).toContain('52');
    expect(fixture.nativeElement.querySelector('a[href="/app/crm"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/app/inventory"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/app/finance"]')).toBeTruthy();
  });
});
