import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { UpsertCustomerGQL } from '../../api/generated/graphql';
import { MissionControlStore } from '../mission-control/mission-control.store';
import { CrmSalesPage } from './crm-sales.page';

describe('CrmSalesPage', () => {
  const mutate = vi.fn().mockReturnValue(
    of({ data: { upsertCustomer: { id: 'customer-1', legalName: 'Globex', active: true } } }),
  );
  const store = {
    data: signal({ executions: [], customers: [], stockItems: [], stockReservations: [], creditProfiles: [] }),
    loading: signal(false),
    error: signal<string | null>(null),
    refresh: vi.fn(),
  };

  beforeEach(async () => {
    mutate.mockClear();
    store.refresh.mockClear();
    await TestBed.configureTestingModule({
      imports: [CrmSalesPage],
      providers: [
        provideRouter([]),
        { provide: MissionControlStore, useValue: store },
        { provide: UpsertCustomerGQL, useValue: { mutate } },
      ],
    }).compileComponents();
  });

  it('creates a customer and refreshes authoritative data', () => {
    const fixture = TestBed.createComponent(CrmSalesPage);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[formControlName="legalName"]') as HTMLInputElement;
    input.value = 'Globex';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();

    expect(mutate).toHaveBeenCalledWith({
      variables: { input: { id: null, legalName: 'Globex', active: true } },
    });
    expect(store.refresh).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Globex was saved in CRM.');
  });
});
