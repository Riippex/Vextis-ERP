import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {
  MissionControlGQL,
  type MissionControlQuery,
} from '../../api/generated/graphql';

type MissionControlData = MissionControlQuery['missionControl'];

@Injectable({ providedIn: 'root' })
export class MissionControlStore {
  private readonly query = inject(MissionControlGQL);
  private readonly destroyRef = inject(DestroyRef);

  readonly data = signal<MissionControlData | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.refresh();
  }

  refresh(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.query
      .fetch({ fetchPolicy: 'network-only' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data }) => {
          this.data.set(data?.missionControl ?? null);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Mission Control could not refresh its operational data.');
          this.loading.set(false);
        },
      });
  }
}
