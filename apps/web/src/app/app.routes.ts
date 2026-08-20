import { Routes } from '@angular/router';

export const routes: Routes = [
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
