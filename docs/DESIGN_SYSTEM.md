# Design System — Identidad visual de Vextis

Este documento define la identidad visual global de `apps/web` y las reglas
para no romperla. Cualquier sesión (Codex, Gemini, Claude) que agregue una
vista nueva debe leer esto primero.

## Origen

La identidad se prototipó como referencia en un mockup React aparte (fuera de
este repo, no se commitea) y se validó con el Claude Skill `dataviz`
(`scripts/validate_palette.js`, corrido en modo claro y oscuro). Este
documento es la versión "de verdad" — lo que vive en `apps/web` manda sobre el
mockup si algún día quedan desalineados.

## Los dos sistemas de color, y por qué no se mezclan

1. **Chrome de marca** (`--vxt-brand-*`, `--vxt-surface`, `--vxt-page`,
   `--vxt-text-*`, etc.) — identidad de Vextis y superficie neutra de la UI.
   Cambia entre modo claro/oscuro.
2. **Dataviz** (`--vxt-cat-1..8`, `--vxt-status-*`) — paleta categórica y de
   estado para gráficas y badges de ejecución. **Nunca se toca por marca.**
   Rafa fue explícito: no se elimina el rojo/verde/amarillo de las gráficas
   por darle identidad visual a la app.

Ambos viven como variables CSS en `apps/web/src/styles/_tokens.scss`, con los
mismos valores hex que el mockup de referencia (para que cuando se porten
componentes del mockup al código real, los colores ya calcen).

## Toggle claro/oscuro

- Un solo mecanismo: la clase `dark` en `<html>`.
- `_tokens.scss` define `:root { color-scheme: light; ... }` y
  `html.dark { color-scheme: dark; ... }` con los valores de chrome y dataviz
  para cada modo.
- `styles.scss` configura `mat.theme()` con `theme-type: color-scheme`, así
  que Angular Material también resuelve sus tokens (`--mat-sys-*`) vía
  `light-dark()` según la misma propiedad `color-scheme` — un solo toggle
  cambia Material y los tokens propios de Vextis a la vez.
- El estado lo maneja el componente raíz (`app.ts`): un signal `isDark`,
  persistido en `localStorage` (`vxt-theme`), con fallback a
  `prefers-color-scheme` la primera vez.
- Botón de toggle: ícono `dark_mode`/`light_mode` en el toolbar (`app.html`).

## Marca

- Azul `#2568c9` → violeta `#7c5cff`, gradiente
  `linear-gradient(135deg, #2568c9 0%, #6a4fe0 60%, #7c5cff 100%)`.
- Uso: logo (marca "V" de nodos conectados), wordmark "VEXTIS" con
  `background-clip: text`, acentos de navegación, CTAs, avatares de ícono.
- No reemplaza los colores categóricos ni de estado de las gráficas.
- El SVG de la marca está inline en `app.html` y en `login.page.html` (no hay
  aún un `.svg` real de Rafa — cuando lo tengamos, reemplaza el `<path>`
  reconstruido).

## Login

`apps/web/src/app/features/auth/login.page.{ts,html,scss}`, ruta `/login`.

Deliberadamente **no seguí el toggle claro/oscuro global** — es una pantalla
de acceso con identidad "exótica" propia (hero oscuro con esfera de puntos,
tipografía editorial, footer de stats), inspirada en la referencia que Rafa
compartió. El componente raíz (`app.ts`, `hideChrome`) oculta el toolbar
global cuando la ruta empieza con `/login`.

Pendiente: el formulario navega directo al dashboard (`onSubmit` en
`login.page.ts`), no hay autenticación real todavía — está marcado con
`TODO(auth)` y depende del vertical slice de `services/enterprise-core`.

## Archivos

- `apps/web/src/styles/_tokens.scss` — variables CSS, única fuente de verdad
  de color.
- `apps/web/src/styles.scss` — entrypoint global, `mat.theme()`.
- `apps/web/src/app/app.{ts,html,scss}` — shell: toolbar, logo, toggle de
  tema, oculta chrome en `/login`.
- `apps/web/src/app/features/auth/login.page.*` — pantalla de acceso.
- `apps/web/src/app/features/dashboard/dashboard.page.scss` — primer
  consumidor de los tokens fuera del shell (referencia de cómo usarlos en una
  vista nueva).

## Cómo usar los tokens en una vista nueva

No hardcodear hex. Usar `var(--vxt-...)`:

```scss
.mi-card {
  background: var(--vxt-surface);
  border: 1px solid var(--vxt-border);
  color: var(--vxt-text-primary);
}

.mi-card__secundario {
  color: var(--vxt-text-secondary);
}
```

Para gráficas, usar `--vxt-cat-1..8` en el orden de la serie, y
`--vxt-status-good|warning|serious|critical` para estados de ejecución
(mapeo en `executionStatus` del mockup: `RUNNING`→good, `WAITING_APPROVAL`→
warning, `FAILED`→critical, etc.).
