import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DOCUMENT } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

const THEME_STORAGE_KEY = 'vxt-theme';

@Component({
  selector: 'vxt-root',
  imports: [MatIconModule, MatToolbarModule, RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);

  /** Tema activo. Persistido en localStorage; cae a `prefers-color-scheme` si no hay nada guardado. */
  protected readonly isDark = signal(this.readInitialTheme());

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** El login tiene su propia identidad visual "exótica" de pantalla completa: sin toolbar ni padding del shell. */
  protected readonly hideChrome = computed(() => this.url().startsWith('/login'));

  constructor() {
    effect(() => {
      const root = this.document.documentElement;
      root.classList.toggle('dark', this.isDark());
      try {
        this.document.defaultView?.localStorage.setItem(
          THEME_STORAGE_KEY,
          this.isDark() ? 'dark' : 'light',
        );
      } catch {
        // Storage puede no estar disponible (modo privado); el tema sigue aplicando en memoria.
      }
    });
  }

  protected toggleTheme(): void {
    this.isDark.update((value) => !value);
  }

  private readInitialTheme(): boolean {
    if (typeof window === 'undefined') return false;
    try {
      const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
      if (stored) return stored === 'dark';
    } catch {
      // Ignorar errores de storage y usar la preferencia del sistema operativo.
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }
}
