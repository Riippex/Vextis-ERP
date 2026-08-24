# Design System — Vextis visual identity

This document defines `apps/web`'s global visual identity and the rules for not breaking it. Any session (Codex, Gemini, Claude) that adds a new view must read this first.

## Origin

The identity was prototyped as a reference in a separate React mockup (outside this repo, not committed) and validated with the Claude Skill `dataviz` (`scripts/validate_palette.js`, run in light and dark mode). This document is the "source of truth" version — what lives in `apps/web` overrides the mockup if they ever drift apart.

## The two color systems, and why they don't mix

1. **Brand chrome** (`--vxt-brand-*`, `--vxt-surface`, `--vxt-page`,
   `--vxt-text-*`, etc.) — Vextis identity and neutral UI surface.
   Changes between light/dark mode.
2. **Dataviz** (`--vxt-cat-1..8`, `--vxt-status-*`) — categorical and
   status palette for charts and execution badges. **Never touched by branding.**
   Rafa was explicit: the red/green/yellow of the charts is not removed
   to give the app a visual identity.

Both live as CSS variables in `apps/web/src/styles/_tokens.scss`, with the
same hex values as the reference mockup (so that when components from the
mockup are ported to real code, the colors already line up).

## Light/dark toggle

- A single mechanism: the `dark` class on `<html>`.
- `_tokens.scss` defines `:root { color-scheme: light; ... }` and
  `html.dark { color-scheme: dark; ... }` with the chrome and dataviz
  values for each mode.
- `styles.scss` configures `mat.theme()` with `theme-type: color-scheme`, so
  Angular Material also resolves its tokens (`--mat-sys-*`) via
  `light-dark()` based on the same `color-scheme` property — a single toggle
  switches Material and Vextis's own tokens at the same time.
- State is managed by the root component (`app.ts`): an `isDark` signal,
  persisted in `localStorage` (`vxt-theme`), falling back to
  `prefers-color-scheme` the first time.
- Toggle button: `dark_mode`/`light_mode` icon in the toolbar (`app.html`).

## Brand

- Blue `#2568c9` → violet `#7c5cff`, gradient
  `linear-gradient(135deg, #2568c9 0%, #6a4fe0 60%, #7c5cff 100%)`.
- Usage: logo (connected-nodes "V" mark), "VEXTIS" wordmark with
  `background-clip: text`, navigation accents, CTAs, icon avatars.
- Does not replace the charts' categorical or status colors.
- The brand SVG is inline in `app.html` and in `login.page.html` (there is
  no real Rafa `.svg` yet — when we have one, replace the reconstructed
  `<path>`).

## Login

`apps/web/src/app/features/auth/login.page.{ts,html,scss}`, route `/login`.

Keeps its own "exotic" identity (hero with a sphere of points, editorial
typography, stats footer), inspired by the reference Rafa shared, rather
than reusing the shell's chrome tokens directly — but as of 2026-08-22 it
**does follow the global light/dark toggle**. `login.page.scss` defines its
own `--login-*` custom properties at `:host`, reusing `--vxt-*` tokens for
the light (default) values, with a `:host-context(html.dark)` override
block holding the original dark-only look byte-for-byte unchanged. No
wiring needed in `login.page.ts` — `app.ts`'s theme effect already toggles
`dark` on `<html>` regardless of route, so the component only needed to
react to the class that was already there. The root component (`app.ts`,
`hideChrome`) still hides the global toolbar when the route starts with
`/login` — that part is unrelated to theme and unchanged.

Pending: the form navigates straight to the dashboard (`onSubmit` in
`login.page.ts`), there's no real authentication yet — it's marked with
`TODO(auth)` and depends on `services/enterprise-core`'s vertical slice.

## Files

- `apps/web/src/styles/_tokens.scss` — CSS variables, the single source of
  truth for color.
- `apps/web/src/styles.scss` — global entrypoint, `mat.theme()`.
- `apps/web/src/app/app.{ts,html,scss}` — shell: toolbar, logo, theme
  toggle, hides chrome on `/login`.
- `apps/web/src/app/features/auth/login.page.*` — access screen.
- `apps/web/src/app/features/dashboard/dashboard.page.scss` — first
  consumer of the tokens outside the shell (reference for how to use them in
  a new view).

## How to use the tokens in a new view

Don't hardcode hex values. Use `var(--vxt-...)`:

```scss
.my-card {
  background: var(--vxt-surface);
  border: 1px solid var(--vxt-border);
  color: var(--vxt-text-primary);
}

.my-card__secondary {
  color: var(--vxt-text-secondary);
}
```

For charts, use `--vxt-cat-1..8` in series order, and
`--vxt-status-good|warning|serious|critical` for execution states (mapped
in the mockup's `executionStatus`: `RUNNING`→good, `WAITING_APPROVAL`→
warning, `FAILED`→critical, etc.).
