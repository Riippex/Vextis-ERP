import { Routes } from '@angular/router';
import { authenticatedGuard, anonymousGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [anonymousGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'purchase-orders/new',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/purchase-orders/receive-purchase-order.page').then(
        (module) => module.ReceivePurchaseOrderPage,
      ),
  },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.page').then((module) => module.DashboardPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
