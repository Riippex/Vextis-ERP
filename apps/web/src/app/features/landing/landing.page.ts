import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { ThemeService } from '../../core/theme/theme.service';

interface Capability {
  readonly icon: string;
  readonly eyebrow: string;
  readonly title: string;
  readonly description: string;
}

@Component({
  selector: 'vxt-landing-page',
  imports: [MatButtonModule, MatIconModule, RouterLink],
  templateUrl: './landing.page.html',
  styleUrl: './landing.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LandingPage {
  private readonly theme = inject(ThemeService);

  protected readonly isDark = this.theme.isDark;

  protected readonly capabilities: readonly Capability[] = [
    {
      icon: 'forum',
      eyebrow: 'Collaborative partner',
      title: 'Understand before acting',
      description:
        'A contextual assistant retrieves business evidence, resolves ambiguity, and keeps people in control.',
    },
    {
      icon: 'account_tree',
      eyebrow: 'Taskmaster',
      title: 'Coordinate the whole workflow',
      description:
        'Durable agents move work across sales, inventory, and finance instead of automating one isolated task.',
    },
    {
      icon: 'shield',
      eyebrow: 'Fortified enterprise fleet',
      title: 'Govern every decision',
      description:
        'Identity, authorization, approvals, idempotency, and audit remain active for human and agent actions.',
    },
  ];

  protected toggleTheme(): void {
    this.theme.toggle();
  }
}
