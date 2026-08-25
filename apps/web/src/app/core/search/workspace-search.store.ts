import { Injectable, signal } from '@angular/core';

/** Shared, page-agnostic search text typed into the workspace toolbar. */
@Injectable({ providedIn: 'root' })
export class WorkspaceSearchStore {
  readonly query = signal('');

  setQuery(value: string): void {
    this.query.set(value);
  }

  clear(): void {
    this.query.set('');
  }
}
