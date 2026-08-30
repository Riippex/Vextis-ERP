import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'vxt-workspace-skeleton',
  templateUrl: './workspace-skeleton.component.html',
  styleUrl: './workspace-skeleton.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspaceSkeletonComponent {
  readonly layout = input<'dashboard' | 'department'>('department');
  readonly label = input('Loading workspace data');
}
