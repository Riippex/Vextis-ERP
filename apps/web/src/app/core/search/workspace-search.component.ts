import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { MissionControlStore } from '../../features/mission-control/mission-control.store';
import { WorkspaceSearchStore } from './workspace-search.store';

interface WorkspaceSearchResult {
  icon: string;
  title: string;
  detail: string;
  route: string;
  searchValue?: string;
}

const DESTINATIONS: readonly WorkspaceSearchResult[] = [
  { icon: 'dashboard', title: 'Overview', detail: 'Mission Control dashboard', route: '/app' },
  { icon: 'move_to_inbox', title: 'Order intake', detail: 'Upload a purchase order', route: '/app/purchase-orders/new' },
  { icon: 'handshake', title: 'CRM & Sales', detail: 'Customers and commercial readiness', route: '/app/crm' },
  { icon: 'inventory_2', title: 'Inventory', detail: 'Stock and reservations', route: '/app/inventory' },
  { icon: 'account_balance', title: 'Finance & Billing', detail: 'Credit profiles and invoices', route: '/app/finance' },
];

function matches(query: string, ...values: (string | number)[]): boolean {
  return values.some((value) => String(value).toLowerCase().includes(query));
}

@Component({
  selector: 'vxt-workspace-search',
  imports: [MatIconModule],
  templateUrl: './workspace-search.component.html',
  styleUrl: './workspace-search.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspaceSearchComponent {
  private readonly element = inject(ElementRef<HTMLElement>);
  private readonly router = inject(Router);
  private readonly missionControl = inject(MissionControlStore);
  private readonly search = inject(WorkspaceSearchStore);

  protected readonly query = this.search.query;
  protected readonly open = signal(false);
  protected readonly loading = this.missionControl.loading;

  protected readonly results = computed<readonly WorkspaceSearchResult[]>(() => {
    const query = this.query().trim().toLowerCase();
    if (!query) {
      return DESTINATIONS;
    }

    const data = this.missionControl.data();
    const results: WorkspaceSearchResult[] = DESTINATIONS.filter((destination) =>
      matches(query, destination.title, destination.detail),
    );

    for (const customer of data?.customers ?? []) {
      if (matches(query, customer.legalName, customer.id)) {
        results.push({
          icon: 'person',
          title: customer.legalName,
          detail: customer.active ? 'Active customer' : 'Inactive customer',
          route: '/app/crm',
          searchValue: customer.legalName,
        });
      }
    }

    for (const execution of data?.executions ?? []) {
      if (matches(query, execution.purchaseOrderNumber, execution.customerName, execution.state)) {
        results.push({
          icon: 'receipt_long',
          title: execution.purchaseOrderNumber,
          detail: `${execution.customerName} · ${execution.state.replaceAll('_', ' ')}`,
          route: '/app',
          searchValue: execution.purchaseOrderNumber,
        });
      }
    }

    for (const item of data?.stockItems ?? []) {
      if (matches(query, item.sku)) {
        results.push({
          icon: 'inventory_2',
          title: item.sku,
          detail: `${item.availableQuantity} units available`,
          route: '/app/inventory',
          searchValue: item.sku,
        });
      }
    }

    for (const profile of data?.creditProfiles ?? []) {
      if (matches(query, profile.customerName, profile.customerId, profile.standing)) {
        results.push({
          icon: 'credit_score',
          title: profile.customerName,
          detail: `${profile.standing.replaceAll('_', ' ')} · ${profile.maxPaymentTermsDays} day terms`,
          route: '/app/finance',
          searchValue: profile.customerName,
        });
      }
    }

    for (const invoice of data?.invoices ?? []) {
      if (matches(query, invoice.id, invoice.orderId, invoice.customerName, invoice.status)) {
        results.push({
          icon: 'request_quote',
          title: invoice.id,
          detail: `${invoice.customerName} · ${invoice.currency} ${invoice.total}`,
          route: '/app/finance',
          searchValue: invoice.id,
        });
      }
    }

    return results.slice(0, 8);
  });

  protected onInput(event: Event): void {
    this.search.setQuery((event.target as HTMLInputElement).value);
    this.open.set(true);
  }

  protected onFocus(): void {
    this.open.set(true);
  }

  protected onFocusOut(event: FocusEvent): void {
    const nextTarget = event.relatedTarget;
    if (!(nextTarget instanceof Node) || !this.element.nativeElement.contains(nextTarget)) {
      this.open.set(false);
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.open.set(false);
      return;
    }
    if (event.key === 'Enter' && this.results()[0]) {
      event.preventDefault();
      void this.select(this.results()[0]);
    }
  }

  protected clear(): void {
    this.search.clear();
    this.open.set(true);
  }

  protected async select(result: WorkspaceSearchResult): Promise<void> {
    if (result.searchValue) {
      this.search.setQuery(result.searchValue);
    } else {
      this.search.clear();
    }
    this.open.set(false);
    await this.router.navigateByUrl(result.route);
  }
}
