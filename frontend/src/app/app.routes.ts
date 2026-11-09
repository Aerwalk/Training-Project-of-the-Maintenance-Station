import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Shell } from './layout/shell';
import { Login } from './pages/login/login';
import { Dashboard } from './pages/dashboard/dashboard';
import { Orders } from './pages/orders/orders';
import { Clients } from './pages/clients/clients';
import { Cars } from './pages/cars/cars';
import { ServicesPage } from './pages/services/services';
import { Masters } from './pages/masters/masters';
import { StatusLogPage } from './pages/status-log/status-log';

export const routes: Routes = [
  { path: 'login', component: Login, title: 'Вход — СТО' },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard, title: 'Обзор — СТО' },
      { path: 'orders', component: Orders, title: 'Заявки — СТО' },
      { path: 'clients', component: Clients, title: 'Клиенты — СТО' },
      { path: 'cars', component: Cars, title: 'Автомобили — СТО' },
      { path: 'services', component: ServicesPage, title: 'Услуги — СТО' },
      { path: 'masters', component: Masters, title: 'Мастера — СТО' },
      { path: 'log', component: StatusLogPage, title: 'Журнал — СТО' },
    ],
  },
  { path: '**', redirectTo: '' },
];
