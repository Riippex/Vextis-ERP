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
 * Deterministic point cloud (golden-angle spiral over a sphere), recreated
 * from the reference hero Rafa shared — in Vextis's own identity (blue →
 * violet) instead of the original's palette. No Math.random: the same seed
 * always produces the same sphere.
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
    if (z <= -0.08) continue; // front hemisphere + a rim of the far side

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
  protected readonly email = signal('admin@vextis.io');
  protected readonly password = signal('admin123');
  protected readonly error = signal<string | null>(null);

  constructor() {
    // TODO(auth): this is a cosmetic hero clock, not a real session heartbeat.
    const id = setInterval(() => this.now.set(new Date()), 1000);
    this.destroyRef.onDestroy(() => clearInterval(id));
  }

  protected get clockLabel(): string {
    return `${this.now().toLocaleTimeString('en-US', { hour12: false })} BOG`;
  }

  protected updateEmail(value: string): void {
    this.email.set(value);
    this.error.set(null);
  }

  protected updatePassword(value: string): void {
    this.password.set(value);
    this.error.set(null);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    if (!this.email().trim() || !this.password().trim()) {
      this.error.set('Enter your email and password to continue.');
      return;
    }
    // TODO(auth): replace with the real flow against Enterprise Core once the
    // authentication endpoint exists (see documents/AI_HANDOFF.md — pending the
    // vertical slice). For now it just navigates to the dashboard so it doesn't
    // block the demo.
    void this.router.navigateByUrl('/');
  }
}
