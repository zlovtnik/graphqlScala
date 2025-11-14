import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

/**
 * Shared lazy loader for placeholder component
 */
const loadPlaceholder = () =>
  import('./shared/components/placeholder.component').then(
    (m) => m.PlaceholderComponent
  );

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: '/login' },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register.component').then(
        (m) => m.RegisterComponent
      ),
  },
  {
    path: 'main',
    loadComponent: () =>
      import('./pages/main.component').then((m) => m.MainComponent),
    canActivate: [authGuard],
  },
  {
    path: 'dynamic-crud',
    loadComponent: () =>
      import(
        './features/dynamic-crud/pages/table-browser/table-browser.component'
      ).then((m) => m.TableBrowserComponent),
    canActivate: [authGuard],
  },
  {
    path: 'users',
    loadComponent: loadPlaceholder,
    canActivate: [authGuard],
    data: { title: 'Users' },
  },
  {
    path: 'settings',
    loadComponent: loadPlaceholder,
    canActivate: [authGuard],
    data: { title: 'Settings' },
  },
  {
    path: 'analytics',
    loadComponent: loadPlaceholder,
    canActivate: [authGuard],
    data: { title: 'Analytics' },
  },
  {
    path: 'monitor',
    loadComponent: loadPlaceholder,
    canActivate: [authGuard],
    data: { title: 'Monitor' },
  },
];
