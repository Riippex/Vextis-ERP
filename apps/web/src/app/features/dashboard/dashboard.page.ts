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
    { icon: 'handshake', name: 'CRM y Ventas', description: 'Clientes, oportunidades y cotizaciones.' },
    { icon: 'inventory_2', name: 'Inventario', description: 'Disponibilidad, reservas y excepciones.' },
    { icon: 'receipt_long', name: 'Finanzas', description: 'Crédito, facturación y trazabilidad.' },
  ];
}
