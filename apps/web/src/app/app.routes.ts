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
