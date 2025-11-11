import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: '/login' },
  { path: 'login', loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent) },
  { path: 'register', loadComponent: () => import('./features/auth/register.component').then(m => m.RegisterComponent) },
  { path: 'main', loadComponent: () => import('./pages/main.component').then(m => m.MainComponent), canActivate: [authGuard] },
  { path: 'dynamic-crud', loadComponent: () => import('./features/dynamic-crud/pages/table-browser/table-browser.component').then(m => m.TableBrowserComponent), canActivate: [authGuard] },
  { path: 'welcome', loadChildren: () => import('./pages/welcome/welcome.routes').then(m => m.WELCOME_ROUTES) }
];

