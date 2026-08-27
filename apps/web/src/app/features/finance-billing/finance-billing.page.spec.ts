import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { UpsertCreditProfileGQL } from '../../api/generated/graphql';
import { MissionControlStore } from '../mission-control/mission-control.store';
import { FinanceBillingPage } from './finance-billing.page';

describe('FinanceBillingPage', () => {
  const customerId = '11111111-1111-1111-1111-111111111111';
  const mutate = vi.fn().mockReturnValue(
    of({
      data: {
        upsertCreditProfile: {
          customerId,
          customerName: 'Acme Colombia',
          standing: 'GOOD',
          maxPaymentTermsDays: 45,
        },
      },
    }),
  );
  const store = {
    data: signal({
      executions: [],
      customers: [{ id: customerId, legalName: 'Acme Colombia', active: true }],
      stockItems: [],
      stockReservations: [],
      creditProfiles: [],
      invoices: [],
    }),
    loading: signal(false),
    error: signal<string | null>(null),
    refresh: vi.fn(),
  };

  beforeEach(async () => {
    mutate.mockClear();
    store.refresh.mockClear();
    await TestBed.configureTestingModule({
      imports: [FinanceBillingPage],
      providers: [
        provideRouter([]),
        { provide: MissionControlStore, useValue: store },
        { provide: UpsertCreditProfileGQL, useValue: { mutate } },
      ],
    }).compileComponents();
  });

  it('saves credit for a selected CRM customer', () => {
    const fixture = TestBed.createComponent(FinanceBillingPage);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as {
      creditForm: FormGroup;
      saveCreditProfile(): void;
    };
    component.creditForm.setValue({ customerId, standing: 'GOOD', maxPaymentTermsDays: 45 });
    component.saveCreditProfile();
    fixture.detectChanges();

    expect(mutate).toHaveBeenCalledWith({
      variables: { input: { customerId, standing: 'GOOD', maxPaymentTermsDays: 45 } },
    });
    expect(store.refresh).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain("Acme Colombia's credit profile was saved.");
  });
});
