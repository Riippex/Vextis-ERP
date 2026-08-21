import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';

interface BusinessArea {
  readonly icon: string;
  readonly name: string;
  readonly description: string;
}

@Component({
  selector: 'vxt-dashboard-page',
  imports: [MatCardModule, MatChipsModule, MatIconModule],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  protected readonly areas: readonly BusinessArea[] = [
    { icon: 'handshake', name: 'CRM & Sales', description: 'Customers, opportunities, and quotes.' },
    { icon: 'inventory_2', name: 'Inventory', description: 'Availability, reservations, and exceptions.' },
    { icon: 'receipt_long', name: 'Finance', description: 'Credit, billing, and traceability.' },
  ];
}
