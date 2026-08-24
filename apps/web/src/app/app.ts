import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { FirebaseAuthService } from './core/auth/firebase-auth.service';
import { ThemeService } from './core/theme/theme.service';

export function isApplicationRoute(url: string): boolean {
  const primaryPath = url.split(/[?#]/, 1)[0] ?? '';
  return primaryPath === '/app' || primaryPath.startsWith('/app/');
}

@Component({
  selector: 'vxt-root',
  imports: [MatButtonModule, MatIconModule, MatToolbarModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly router = inject(Router);
  private readonly auth = inject(FirebaseAuthService);
  private readonly theme = inject(ThemeService);

  protected readonly isDark = this.theme.isDark;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Only authenticated application routes use the workspace shell. */
  protected readonly hideChrome = computed(() => !isApplicationRoute(this.url()));

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  protected async signOut(): Promise<void> {
    await this.auth.signOut();
    await this.router.navigateByUrl('/login');
  }
}
