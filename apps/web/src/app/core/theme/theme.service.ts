import { DOCUMENT } from '@angular/common';
import { effect, inject, Injectable, signal } from '@angular/core';

const THEME_STORAGE_KEY = 'vxt-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  readonly isDark = signal(this.readInitialTheme());

  constructor() {
    effect(() => {
      const isDark = this.isDark();
      this.document.documentElement.classList.toggle('dark', isDark);

      try {
        this.document.defaultView?.localStorage.setItem(
          THEME_STORAGE_KEY,
          isDark ? 'dark' : 'light',
        );
      } catch {
        // Storage may be unavailable; the theme still applies in memory.
      }
    });
  }

  toggle(): void {
    this.isDark.update((value) => !value);
  }

  private readInitialTheme(): boolean {
    const view = this.document.defaultView;
    if (!view) return false;

    try {
      const stored = view.localStorage.getItem(THEME_STORAGE_KEY);
      if (stored) return stored === 'dark';
    } catch {
      // Ignore storage errors and fall back to the OS preference.
    }

    return view.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }
}
