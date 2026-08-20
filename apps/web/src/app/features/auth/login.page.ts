import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

const SPHERE_SIZE = 380;
const SPHERE_POINT_COUNT = 260;

interface SpherePoint {
  readonly cx: number;
  readonly cy: number;
  readonly r: number;
  readonly fill: string;
  readonly opacity: number;
}

/**
 * Nube de puntos determinística (espiral de ángulo dorado sobre una esfera),
 * recreada del hero de referencia que compartió Rafa — en la identidad propia
 * de Vextis (azul → violeta) en vez de la paleta del original. Sin Math.random:
 * la misma semilla produce siempre la misma esfera.
 */
function buildSpherePoints(count: number, radius: number): readonly SpherePoint[] {
  const golden = Math.PI * (3 - Math.sqrt(5));
  const light = { x: -0.5, y: -0.6 };
  const points: SpherePoint[] = [];

  for (let i = 0; i < count; i++) {
    const y = 1 - (i / (count - 1)) * 2;
    const r = Math.sqrt(1 - y * y);
    const theta = golden * i;
    const x = Math.cos(theta) * r;
    const z = Math.sin(theta) * r;
    if (z <= -0.08) continue; // hemisferio frontal + un borde del lejano

    const shade = Math.max(0, x * light.x + y * light.y + z * 0.4);
    const t = Math.min(1, shade * 1.3);
    const fill = t > 0.55 ? '#f3ecff' : t > 0.28 ? '#b9a3ff' : '#6a4fe0';
    const depth = (z + 1) / 2;

    points.push({
      cx: x * radius * 0.86,
      cy: y * radius * 0.86,
      r: 1.4 + t * 2.2,
      fill,
      opacity: 0.35 + depth * 0.65,
    });
  }

  return points;
}

@Component({
  selector: 'vxt-login-page',
  imports: [],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly sphereSize = SPHERE_SIZE;
  protected readonly sphereRadius = SPHERE_SIZE * 0.43;
  protected readonly spherePoints = buildSpherePoints(SPHERE_POINT_COUNT, SPHERE_SIZE / 2);
  protected readonly sphereViewBox = `${-SPHERE_SIZE / 2} ${-SPHERE_SIZE / 2} ${SPHERE_SIZE} ${SPHERE_SIZE}`;

  protected readonly now = signal(new Date());
  protected readonly email = signal('rafa@vextis.io');
  protected readonly password = signal('');

  constructor() {
    // TODO(auth): esto es un reloj cosmético del hero, no un heartbeat de sesión real.
    const id = setInterval(() => this.now.set(new Date()), 1000);
    this.destroyRef.onDestroy(() => clearInterval(id));
  }

  protected get clockLabel(): string {
    return `${this.now().toLocaleTimeString('es-CO', { hour12: false })} BOG`;
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    // TODO(auth): reemplazar por el flujo real contra Enterprise Core cuando exista
    // el endpoint de autenticación (ver documents/AI_HANDOFF.md — pendiente el
    // vertical slice). Por ahora solo navega al dashboard para no bloquear la demo.
    void this.router.navigateByUrl('/');
  }
}
