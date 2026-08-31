import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { WorkspaceSearchStore } from '../../core/search/workspace-search.store';
import { MissionControlStore } from '../mission-control/mission-control.store';
import { DashboardPage, ordersCompletedPerWeek, toDepartmentVolumeSlices, toWeeklyExecutionPoints } from './dashboard.page';

describe('DashboardPage', () => {
  const openDialog = vi.fn().mockReturnValue({ afterClosed: () => of(false) });
  const store = {
    data: signal({
      agents: [
        {
          agentId: 'vextis_inventory_agent',
          version: '1.0.0',
          displayName: 'Inventory Agent',
          department: 'INVENTORY_OPERATIONS',
          purpose: 'Provides authoritative SKU availability.',
          framework: 'GOOGLE_ADK',
          modelId: 'gemini-3.5-flash',
          promptVersion: '1.0.0',
          serviceIdentity: 'coordinator-agent',
          status: 'ACTIVE' as const,
          capabilities: ['stock lookup'],
          allowedTools: ['get_stock'],
        },
      ],
      recentAgentActivities: [
        {
          conversationId: '6b1a6e4a-2f0a-4e3b-8f0a-9b8b6a2c1d10',
          messageId: 'f83c9fc5-d498-42ea-b8db-e0475cb493f4',
          agentId: 'vextis_inventory_agent',
          agentVersion: '1.0.0',
          displayName: 'Inventory Agent',
          modelId: 'gemini-3.5-flash',
          promptVersion: '1.0.0',
          tools: ['get_stock'],
          occurredAt: '2026-08-26T12:00:00Z',
        },
      ],
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
      stockReservations: [],
      creditProfiles: [
        {
          customerId: '11111111-1111-1111-1111-111111111111',
          customerName: 'Acme Colombia',
          standing: 'GOOD' as const,
          maxPaymentTermsDays: 30,
        },
      ],
      executionVolumeByDepartment: [
        { department: 'CRM_SALES' as const, count: 2 },
        { department: 'INVENTORY_OPERATIONS' as const, count: 1 },
      ],
      completedOrdersPerWeek: [
        { weekStart: '2026-08-24T00:00:00Z', count: 1 },
      ],
    }),
    loading: signal(false),
    error: signal<string | null>(null),
    refresh: vi.fn(),
  };

  beforeEach(async () => {
    openDialog.mockClear();
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        provideRouter([]),
        { provide: MissionControlStore, useValue: store },
        { provide: MatDialog, useValue: { open: openDialog } },
      ],
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
    expect(fixture.nativeElement.textContent).toContain('Orders completed per week');
    expect(fixture.nativeElement.textContent).toContain('Execution volume by department');
    expect(fixture.nativeElement.textContent).toContain('Enterprise agent registry');
    expect(fixture.nativeElement.textContent).toContain('Inventory Agent');
    expect(fixture.nativeElement.textContent).toContain('gemini-3.5-flash');
    expect(fixture.nativeElement.textContent).toContain('get_stock');
    expect(fixture.nativeElement.textContent).toContain('Recent agent activity');
    expect(fixture.nativeElement.textContent).toContain('Validated and snapshotted by Core');
  });

  it('filters recent workflow activity by the shared workspace search query', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    const search = TestBed.inject(WorkspaceSearchStore);
    search.setQuery('does-not-match-anything');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('PO-2026-001');
    expect(fixture.nativeElement.textContent).toContain('No workflows match your search.');
  });

  it('opens the live monitor from a workflow row', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.execution-row').click();

    expect(openDialog).toHaveBeenCalledWith(expect.any(Function), expect.objectContaining({
      data: expect.objectContaining({ id: '8d3f290d-1322-44a2-8bd7-3b325f170e07' }),
    }));
  });
});

describe('ordersCompletedPerWeek', () => {
  it('buckets only completed executions into the last six ISO weeks', () => {
    const now = new Date();
    const completed = {
      state: 'COMPLETED',
      updatedAt: now.toISOString(),
    };
    const running = { state: 'RUNNING', updatedAt: now.toISOString() };

    const series = ordersCompletedPerWeek([completed, completed, running]);

    expect(series).toHaveLength(6);
    expect(series.at(-1)?.value).toBe(2);
    expect(series.reduce((total, point) => total + point.value, 0)).toBe(2);
  });
});

describe('toDepartmentVolumeSlices', () => {
  it('maps department codes to display labels', () => {
    expect(
      toDepartmentVolumeSlices([
        { department: 'CRM_SALES', count: 3 },
        { department: 'UNKNOWN_DEPARTMENT', count: 1 },
      ]),
    ).toEqual([
      { label: 'CRM & Sales', value: 3 },
      { label: 'UNKNOWN_DEPARTMENT', value: 1 },
    ]);
  });
});

describe('toWeeklyExecutionPoints', () => {
  it('maps authoritative weekly aggregates to chart points', () => {
    const points = toWeeklyExecutionPoints([{ weekStart: '2026-08-24T00:00:00Z', count: 4 }]);
    expect(points).toHaveLength(1);
    expect(points[0].value).toBe(4);
  });
});
