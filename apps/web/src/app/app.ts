import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { FirebaseAuthService } from './core/auth/firebase-auth.service';
import { WorkspaceSearchComponent } from './core/search/workspace-search.component';
import { ThemeService } from './core/theme/theme.service';
import { AskVextisPanelComponent } from './shared/ask-vextis/ask-vextis-panel.component';
import { AskVextisTriggerComponent } from './shared/ask-vextis/ask-vextis-trigger.component';

export function isApplicationRoute(url: string): boolean {
  const primaryPath = url.split(/[?#]/, 1)[0] ?? '';
  return primaryPath === '/app' || primaryPath.startsWith('/app/');
}

export interface WorkspaceRouteContext {
  title: string;
  subtitle: string;
}

export function workspaceRouteContext(url: string): WorkspaceRouteContext {
  const primaryPath = url.split(/[?#]/, 1)[0] ?? '';
  if (primaryPath.startsWith('/app/crm')) {
    return { title: 'CRM & Sales', subtitle: 'Customers and commercial readiness' };
  }
  if (primaryPath.startsWith('/app/inventory')) {
    return { title: 'Inventory', subtitle: 'Availability and operational evidence' };
  }
  if (primaryPath.startsWith('/app/finance')) {
    return { title: 'Finance & Billing', subtitle: 'Credit, terms, and financial control' };
  }
  if (primaryPath.startsWith('/app/purchase-orders')) {
    return { title: 'Order intake', subtitle: 'Start a governed agent workflow' };
  }
  return { title: 'Overview', subtitle: 'One view across every department' };
}

export function sidebarToggleIcon(collapsed: boolean): string {
  return collapsed ? 'menu' : 'menu_open';
}

@Component({
  selector: 'vxt-root',
  imports: [
    AskVextisPanelComponent,
    AskVextisTriggerComponent,
    MatButtonModule,
    MatIconModule,
    MatToolbarModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    WorkspaceSearchComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly router = inject(Router);
  private readonly auth = inject(FirebaseAuthService);
  private readonly theme = inject(ThemeService);

  protected readonly isDark = this.theme.isDark;
  protected readonly sidebarCollapsed = signal(
    globalThis.matchMedia?.('(max-width: 58rem)').matches ?? false,
  );

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Only authenticated application routes use the workspace shell. */
  protected readonly hideChrome = computed(() => !isApplicationRoute(this.url()));
  protected readonly routeContext = computed(() => workspaceRouteContext(this.url()));

  protected toggleSidebar(): void {
    this.sidebarCollapsed.update((collapsed) => !collapsed);
  }

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  protected sidebarToggleIcon(collapsed: boolean): string {
    return sidebarToggleIcon(collapsed);
  }

  protected async signOut(): Promise<void> {
    await this.auth.signOut();
    await this.router.navigateByUrl('/login');
  }
}
