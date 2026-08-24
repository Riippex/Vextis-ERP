import { Routes } from '@angular/router';
import { authenticatedGuard, anonymousGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/landing/landing.page').then((module) => module.LandingPage),
  },
  {
    path: 'login',
    canActivate: [anonymousGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'app/purchase-orders/new',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/purchase-orders/receive-purchase-order.page').then(
        (module) => module.ReceivePurchaseOrderPage,
      ),
  },
  {
    path: 'app/crm',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/crm-sales/crm-sales.page').then((module) => module.CrmSalesPage),
  },
  {
    path: 'app/inventory',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/inventory-operations/inventory-operations.page').then(
        (module) => module.InventoryOperationsPage,
      ),
  },
  {
    path: 'app/finance',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/finance-billing/finance-billing.page').then(
        (module) => module.FinanceBillingPage,
      ),
  },
  {
    path: 'app',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.page').then((module) => module.DashboardPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
