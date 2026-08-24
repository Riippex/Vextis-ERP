import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LandingPage } from './landing.page';

describe('LandingPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LandingPage],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('presents the public product entry point', () => {
    const fixture = TestBed.createComponent(LandingPage);
    fixture.detectChanges();

    const content = fixture.nativeElement.textContent as string;
    expect(content).toContain('Your business,');
    expect(content).toContain('Fortified enterprise fleet');
    expect(content).toContain('Open the workspace');
  });
});
