import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { SetStockAvailabilityGQL } from '../../api/generated/graphql';
import { MissionControlStore } from '../mission-control/mission-control.store';
import { InventoryOperationsPage } from './inventory-operations.page';

describe('InventoryOperationsPage', () => {
  const mutate = vi.fn().mockReturnValue(
    of({ data: { setStockAvailability: { sku: 'VXT-DESK-01', availableQuantity: 18 } } }),
  );
  const store = {
    data: signal({ executions: [], customers: [], stockItems: [], creditProfiles: [] }),
    loading: signal(false),
    error: signal<string | null>(null),
    refresh: vi.fn(),
  };

  beforeEach(async () => {
    mutate.mockClear();
    store.refresh.mockClear();
    await TestBed.configureTestingModule({
      imports: [InventoryOperationsPage],
      providers: [
        provideRouter([]),
        { provide: MissionControlStore, useValue: store },
        { provide: SetStockAvailabilityGQL, useValue: { mutate } },
      ],
    }).compileComponents();
  });

  it('sets availability and refreshes authoritative data', () => {
    const fixture = TestBed.createComponent(InventoryOperationsPage);
    fixture.detectChanges();
    const sku = fixture.nativeElement.querySelector('input[formControlName="sku"]') as HTMLInputElement;
    const quantity = fixture.nativeElement.querySelector(
      'input[formControlName="availableQuantity"]',
    ) as HTMLInputElement;
    sku.value = 'VXT-DESK-01';
    sku.dispatchEvent(new Event('input'));
    quantity.value = '18';
    quantity.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('button[type="submit"]').click();
    fixture.detectChanges();

    expect(mutate).toHaveBeenCalledWith({
      variables: { input: { sku: 'VXT-DESK-01', availableQuantity: 18 } },
    });
    expect(store.refresh).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('VXT-DESK-01 availability was updated.');
  });
});
