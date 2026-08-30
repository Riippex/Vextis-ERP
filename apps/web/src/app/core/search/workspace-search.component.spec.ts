import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MissionControlStore } from '../../features/mission-control/mission-control.store';
import { WorkspaceSearchComponent } from './workspace-search.component';

describe('WorkspaceSearchComponent', () => {
  let fixture: ComponentFixture<WorkspaceSearchComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkspaceSearchComponent],
      providers: [
        provideRouter([]),
        {
          provide: MissionControlStore,
          useValue: {
            loading: signal(false),
            data: signal({
              customers: [{ id: 'customer-1', legalName: 'Acme Retail', active: true }],
              executions: [],
              stockItems: [{ sku: 'SKU-COFFEE-01', availableQuantity: 42 }],
              creditProfiles: [],
              invoices: [],
            }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkspaceSearchComponent);
    fixture.detectChanges();
  });

  it('offers workspace destinations before a query is entered', () => {
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Overview');
    expect(fixture.nativeElement.textContent).toContain('CRM & Sales');
  });

  it('finds operational records across departments', () => {
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = 'coffee';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('SKU-COFFEE-01');
    expect(fixture.nativeElement.textContent).toContain('42 units available');
    expect(fixture.nativeElement.textContent).not.toContain('Acme Retail');
  });
});
