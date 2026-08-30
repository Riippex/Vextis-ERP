import { TestBed } from '@angular/core/testing';

import { WorkspaceSkeletonComponent } from './workspace-skeleton.component';

describe('WorkspaceSkeletonComponent', () => {
  it('announces loading state and renders the requested layout', async () => {
    await TestBed.configureTestingModule({ imports: [WorkspaceSkeletonComponent] }).compileComponents();
    const fixture = TestBed.createComponent(WorkspaceSkeletonComponent);
    fixture.componentRef.setInput('layout', 'dashboard');
    fixture.componentRef.setInput('label', 'Loading Mission Control');
    fixture.detectChanges();

    const status = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(status.getAttribute('aria-label')).toBe('Loading Mission Control');
    expect(fixture.nativeElement.querySelectorAll('.workspace-skeleton__chart')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.workspace-skeleton__form')).toBeNull();
  });
});
