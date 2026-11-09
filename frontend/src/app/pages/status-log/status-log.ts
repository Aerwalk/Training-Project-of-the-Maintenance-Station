import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import {
  OrderStatus,
  OrderSummary,
  StatusLogEntry,
  plural,
  pluralWord,
  statusClass,
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

interface LogDay {
  key: string;
  label: string;
  entries: StatusLogEntry[];
}

@Component({
  selector: 'app-status-log',
  standalone: true,
  imports: [FormsModule, RouterLink, Icon],
  templateUrl: './status-log.html',
  styleUrl: './status-log.css',
})
export class StatusLogPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly statusClass = statusClass;
  readonly plural = plural;
  readonly pluralWord = pluralWord;

  readonly loading = signal(true);
  readonly log = signal<StatusLogEntry[]>([]);
  readonly statuses = signal<OrderStatus[]>([]);
  readonly orders = signal<OrderSummary[]>([]);

  readonly search = signal('');
  readonly orderFilter = signal<number | null>(null);

  readonly filtered = computed(() => {
    const query = this.search().trim().toLowerCase();
    const orderId = this.orderFilter();
    return this.log().filter((entry) => {
      if (orderId !== null && entry.orderId !== orderId) {
        return false;
      }
      if (!query) {
        return true;
      }
      const order = this.orderOf(entry.orderId);
      return (
        String(entry.orderId).includes(query) ||
        entry.changedBy.toLowerCase().includes(query) ||
        this.statusName(entry.oldStatusId).toLowerCase().includes(query) ||
        this.statusName(entry.newStatusId).toLowerCase().includes(query) ||
        (order?.client ?? '').toLowerCase().includes(query) ||
        (order?.car ?? '').toLowerCase().includes(query)
      );
    });
  });

  readonly groups = computed<LogDay[]>(() => {
    const map = new Map<string, LogDay>();
    for (const entry of this.filtered()) {
      const date = new Date(entry.changedAt);
      const key = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
      const group = map.get(key) ?? {
        key,
        label: new Intl.DateTimeFormat('ru-RU', {
          weekday: 'long',
          day: 'numeric',
          month: 'long',
          year: 'numeric',
        }).format(date),
        entries: [],
      };
      group.entries.push(entry);
      map.set(key, group);
    }
    return [...map.values()];
  });

  readonly uniqueOrders = computed(() => new Set(this.log().map((entry) => entry.orderId)).size);

  readonly uniqueAuthors = computed(() => new Set(this.log().map((entry) => entry.changedBy)).size);

  readonly todayCount = computed(() => {
    const today = new Date();
    return this.log().filter((entry) => {
      const date = new Date(entry.changedAt);
      return (
        date.getFullYear() === today.getFullYear() &&
        date.getMonth() === today.getMonth() &&
        date.getDate() === today.getDate()
      );
    }).length;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({
      log: this.api.statusLog(),
      statuses: this.api.statuses(),
      orders: this.api.orders(),
    }).subscribe({
      next: (data) => {
        this.log.set(data.log);
        this.statuses.set(data.statuses);
        this.orders.set(data.orders);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  resetFilters(): void {
    this.search.set('');
    this.orderFilter.set(null);
  }

  orderOf(orderId: number): OrderSummary | undefined {
    return this.orders().find((order) => order.orderId === orderId);
  }

  statusName(id: number | null): string {
    if (id === null) {
      return '—';
    }
    return this.statuses().find((status) => status.id === id)?.name ?? `статус ${id}`;
  }

  time(value: string): string {
    return new Intl.DateTimeFormat('ru-RU', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date(value));
  }
}
