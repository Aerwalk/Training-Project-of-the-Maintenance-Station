import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { initials, OrderSummary } from '../core/models';
import { Icon } from '../shared/icon';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  exact?: boolean;
}

const PAGE_META: Record<string, { title: string; sub: string }> = {
  dashboard: { title: 'Обзор', sub: 'Главные показатели и события сервиса' },
  orders: { title: 'Заявки', sub: 'Приём, сопровождение и закрытие заказов' },
  clients: { title: 'Клиенты', sub: 'База владельцев автомобилей' },
  cars: { title: 'Автомобили', sub: 'Автопарк клиентов сервиса' },
  services: { title: 'Услуги', sub: 'Прайс-лист и длительность работ' },
  masters: { title: 'Мастера', sub: 'Команда и специализации' },
  log: { title: 'Журнал статусов', sub: 'История изменений, записанная триггерами БД' },
};

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly initials = initials;
  readonly menuOpen = signal(false);
  readonly search = signal('');
  readonly orders = signal<OrderSummary[]>([]);
  readonly databaseOnline = signal<boolean | null>(null);

  readonly title = signal(PAGE_META['dashboard'].title);
  readonly subtitle = signal(PAGE_META['dashboard'].sub);

  readonly today = new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());

  readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Обзор', icon: 'dashboard', exact: true },
    { path: '/orders', label: 'Заявки', icon: 'orders' },
    { path: '/clients', label: 'Клиенты', icon: 'clients' },
    { path: '/cars', label: 'Автомобили', icon: 'car' },
    { path: '/services', label: 'Услуги', icon: 'services' },
    { path: '/masters', label: 'Мастера', icon: 'masters' },
    { path: '/log', label: 'Журнал', icon: 'history' },
  ];

  readonly activeCount = computed(
    () =>
      this.orders().filter((order) => order.status !== 'завершена' && order.status !== 'отменена')
        .length,
  );

  constructor() {
    this.refresh();
    this.api.health().subscribe({
      next: () => this.databaseOnline.set(true),
      error: () => this.databaseOnline.set(false),
    });
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.menuOpen.set(false);
        this.updateMeta(event.urlAfterRedirects);
        this.refresh();
      });
    this.updateMeta(this.router.url);
  }

  refresh(): void {
    this.api.orders().subscribe({
      next: (orders) => this.orders.set(orders),
      error: () => this.orders.set([]),
    });
  }

  goSearch(event: Event): void {
    event.preventDefault();
    const query = this.search().trim();
    this.search.set('');
    void this.router.navigate(['/orders'], { queryParams: query ? { q: query } : {} });
  }

  logout(): void {
    this.auth.logout();
  }

  roleLabel(role: string | undefined): string {
    switch (role) {
      case 'admin':
        return 'Администратор';
      case 'manager':
        return 'Менеджер';
      case 'master':
        return 'Мастер';
      case 'client':
        return 'Клиент';
      default:
        return 'Пользователь';
    }
  }

  private updateMeta(url: string): void {
    const segment = url.split('?')[0].split('/').filter(Boolean)[0] ?? 'dashboard';
    const meta = PAGE_META[segment] ?? PAGE_META['dashboard'];
    this.title.set(meta.title);
    this.subtitle.set(meta.sub);
  }
}
