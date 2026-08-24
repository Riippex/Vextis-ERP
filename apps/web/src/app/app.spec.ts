import { TestBed } from '@angular/core/testing';

import { App, isApplicationRoute } from './app';
import { appConfig } from './app.config';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: appConfig.providers,
    }).compileComponents();
  });

  it('creates the application shell', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows workspace chrome only on authenticated application paths', () => {
    expect(isApplicationRoute('/')).toBe(false);
    expect(isApplicationRoute('/#platform')).toBe(false);
    expect(isApplicationRoute('/#/platform')).toBe(false);
    expect(isApplicationRoute('/login?next=%2Fapp')).toBe(false);
    expect(isApplicationRoute('/app')).toBe(true);
    expect(isApplicationRoute('/app/purchase-orders/new')).toBe(true);
    expect(isApplicationRoute('/app/crm')).toBe(true);
    expect(isApplicationRoute('/app/inventory')).toBe(true);
    expect(isApplicationRoute('/app/finance')).toBe(true);
  });
});
