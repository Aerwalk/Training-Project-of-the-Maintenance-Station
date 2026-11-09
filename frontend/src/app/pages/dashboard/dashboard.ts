import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import {
  CarView,
  Client,
  Master,
  OrderStatus,
  OrderSummary,
  Service,
  StatusLogEntry,
  dateTime,
  money,
  plural,
  pluralWord,
  statusClass,
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

const STATUS_COLORS: Record<string, string> = {
  'новая': '#2463eb',
  'в работе': '#f59e0b',
  'завершена': '#10b981',
  'отменена': '#ef4444',
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, Icon],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly auth = inject(AuthService);

  readonly money = money;
  readonly plural = plural;
  readonly pluralWord = pluralWord;
  readonly statusClass = statusClass;
  readonly dateTime = dateTime;

  readonly loading = signal(true);
  readonly orders = signal<OrderSummary[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly cars = signal<CarView[]>([]);
  readonly services = signal<Service[]>([]);
  readonly masters = signal<Master[]>([]);
  readonly statuses = signal<OrderStatus[]>([]);
  readonly log = signal<StatusLogEntry[]>([]);

  readonly greeting = computed(() => {
    const parts = (this.auth.user()?.fullName ?? '').split(' ').filter(Boolean);
    return parts[1] ?? parts[0] ?? 'коллега';
  });

  readonly totalOrders = computed(() => this.orders().length);

  readonly newOrders = computed(() => this.countByStatus('новая'));

  readonly inProgress = computed(() => this.countByStatus('в работе'));

  readonly completed = computed(() => this.countByStatus('завершена'));

  readonly revenue = computed(() =>
    this.orders().reduce((sum, order) => sum + Number(order.totalCost ?? 0), 0),
  );

  readonly avgCheck = computed(() => {
    const paid = this.orders().filter((order) => Number(order.totalCost) > 0);
    if (paid.length === 0) {
      return 0;
    }
    return paid.reduce((sum, order) => sum + Number(order.totalCost), 0) / paid.length;
  });

  readonly activeOrders = computed(() =>
    this.orders()
      .filter((order) => order.status !== 'завершена' && order.status !== 'отменена')
      .sort((a, b) => b.orderId - a.orderId)
      .slice(0, 7),
  );

  readonly completionRate = computed(() =>
    this.totalOrders() === 0 ? 0 : Math.round((this.completed() / this.totalOrders()) * 100),
  );

  readonly statusStats = computed(() =>
    this.statuses().map((status) => ({
      id: status.id,
      name: status.name,
      count: this.countByStatus(status.name),
      color: STATUS_COLORS[status.name] ?? '#94a3b8',
      className: statusClass(status.name),
    })),
  );

  readonly donutGradient = computed(() => {
    const total = this.totalOrders();
    if (total === 0) {
      return 'conic-gradient(#eef2f8 0 100%)';
    }
    let cursor = 0;
    const segments: string[] = [];
    for (const item of this.statusStats()) {
      if (item.count === 0) {
        continue;
      }
      const start = cursor;
      cursor += (item.count / total) * 100;
      segments.push(`${item.color} ${start}% ${cursor}%`);
    }
    return `conic-gradient(${segments.join(', ')})`;
  });

  readonly logPreview = computed(() =>
    this.log()
      .slice(0, 6)
      .map((entry) => ({
        ...entry,
        fromName: this.statusName(entry.oldStatusId) ?? '—',
        toName: this.statusName(entry.newStatusId) ?? '—',
      })),
  );

  readonly activeMasters = computed(() => this.masters().filter((master) => master.active).length);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({
      orders: this.api.orders(),
      clients: this.api.clients(),
      cars: this.api.cars(),
      services: this.api.services(),
      masters: this.api.masters(),
      statuses: this.api.statuses(),
      log: this.api.statusLog(),
    }).subscribe({
      next: (data) => {
        this.orders.set(data.orders);
        this.clients.set(data.clients);
        this.cars.set(data.cars);
        this.services.set(data.services);
        this.masters.set(data.masters);
        this.statuses.set(data.statuses);
        this.log.set(data.log);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  compactMoney(value: number): string {
    return (
      new Intl.NumberFormat('ru-RU', { notation: 'compact', maximumFractionDigits: 1 }).format(
        value ?? 0,
      ) + ' ₽'
    );
  }

  statusName(id: number | null): string | null {
    if (id === null) {
      return null;
    }
    return this.statuses().find((status) => status.id === id)?.name ?? null;
  }

  private countByStatus(status: string): number {
    return this.orders().filter((order) => order.status === status).length;
  }
}
