import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'purchase-orders/new',
    loadComponent: () =>
      import('./features/purchase-orders/receive-purchase-order.page').then(
        (module) => module.ReceivePurchaseOrderPage,
      ),
  },
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/dashboard/dashboard.page').then((module) => module.DashboardPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
