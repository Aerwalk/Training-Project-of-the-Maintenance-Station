import { Component, HostListener, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import {
  CarView,
  Client,
  Master,
  OrderDetail,
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

type OrderFilter = 'all' | 'новая' | 'в работе' | 'завершена' | 'отменена';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [FormsModule, Icon],
  templateUrl: './orders.html',
  styleUrl: './orders.css',
})
export class Orders {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

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

  readonly filter = signal<OrderFilter>('all');
  readonly search = signal('');

  readonly createOpen = signal(false);
  readonly createError = signal('');
  readonly creating = signal(false);

  readonly detailOpen = signal(false);
  readonly detailLoading = signal(false);
  readonly detail = signal<OrderDetail | null>(null);
  readonly detailLog = signal<StatusLogEntry[]>([]);
  readonly addServiceError = signal('');
  readonly addingService = signal(false);
  readonly confirmClose = signal(false);
  readonly closing = signal(false);

  newOrder = {
    clientId: null as number | null,
    carId: null as number | null,
    masterId: null as number | null,
    description: '',
  };

  newService = {
    serviceId: null as number | null,
    quantity: 1,
  };

  private pendingClientId: number | null = null;

  readonly tabs = [
    { key: 'all' as OrderFilter, label: 'Все' },
    { key: 'новая' as OrderFilter, label: 'Новые' },
    { key: 'в работе' as OrderFilter, label: 'В работе' },
    { key: 'завершена' as OrderFilter, label: 'Завершённые' },
    { key: 'отменена' as OrderFilter, label: 'Отменённые' },
  ];

  constructor() {
    this.load();
    this.route.queryParamMap.subscribe((params) => {
      const query = params.get('q');
      if (query) {
        this.search.set(query);
      }
      if (params.get('new') === '1' && !this.createOpen()) {
        this.openCreate();
        const clientId = Number(params.get('clientId'));
        if (Number.isFinite(clientId) && clientId > 0) {
          this.pendingClientId = clientId;
          this.applyPendingClient();
        }
      }
      const orderId = Number(params.get('order'));
      if (orderId && (!this.detail() || this.detail()?.order.id !== orderId)) {
        this.openDetail(orderId);
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.createOpen()) {
      this.closeCreate();
    } else if (this.detailOpen()) {
      this.closeDetail();
    }
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
    }).subscribe({
      next: (data) => {
        this.orders.set(data.orders);
        this.clients.set(data.clients);
        this.cars.set(data.cars);
        this.services.set(data.services);
        this.masters.set(data.masters);
        this.statuses.set(data.statuses);
        this.loading.set(false);
        this.applyPendingClient();
      },
      error: (error) => {
        this.loading.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  countFor(filter: OrderFilter): number {
    return filter === 'all'
      ? this.orders().length
      : this.orders().filter((order) => order.status === filter).length;
  }

  filteredOrders(): OrderSummary[] {
    const query = this.search().trim().toLowerCase();
    const filter = this.filter();
    return this.orders().filter((order) => {
      if (filter !== 'all' && order.status !== filter) {
        return false;
      }
      if (!query) {
        return true;
      }
      return (
        String(order.orderId).includes(query) ||
        order.client.toLowerCase().includes(query) ||
        order.car.toLowerCase().includes(query) ||
        order.status.toLowerCase().includes(query)
      );
    });
  }

  setFilter(filter: OrderFilter): void {
    this.filter.set(filter);
  }

  openCreate(): void {
    this.newOrder = { clientId: null, carId: null, masterId: null, description: '' };
    this.createError.set('');
    this.detailOpen.set(false);
    this.createOpen.set(true);
  }

  closeCreate(): void {
    if (this.creating()) {
      return;
    }
    this.createOpen.set(false);
    if (this.route.snapshot.queryParamMap.has('new')) {
      void this.router.navigate(['/orders'], { replaceUrl: true });
    }
  }

  carsForSelected(): CarView[] {
    if (!this.newOrder.clientId) {
      return [];
    }
    return this.cars().filter((car) => car.clientId === this.newOrder.clientId);
  }

  selectedClient(): Client | undefined {
    return this.clients().find((client) => client.id === this.newOrder.clientId);
  }

  onClientChange(clientId: number | null): void {
    this.newOrder.clientId = clientId;
    this.newOrder.carId = null;
    const cars = this.carsForSelected();
    if (cars.length === 1) {
      this.newOrder.carId = cars[0].id;
    }
    this.createError.set('');
  }

  private applyPendingClient(): void {
    if (this.pendingClientId !== null && this.clients().length > 0) {
      this.onClientChange(this.pendingClientId);
      this.pendingClientId = null;
    }
  }

  canCreate(): boolean {
    return (
      !this.creating() &&
      this.newOrder.clientId !== null &&
      this.newOrder.carId !== null &&
      this.newOrder.description.trim().length > 0
    );
  }

  create(): void {
    if (!this.canCreate()) {
      this.createError.set('Выберите клиента, автомобиль и опишите неисправность');
      return;
    }
    this.creating.set(true);
    this.createError.set('');
    this.api
      .createOrder({
        clientId: this.newOrder.clientId!,
        carId: this.newOrder.carId!,
        masterId: this.newOrder.masterId,
        description: this.newOrder.description.trim(),
      })
      .subscribe({
        next: (result) => {
          this.creating.set(false);
          this.createOpen.set(false);
          this.toast.success(`Заявка #${result.orderId} создана`);
          this.load();
          this.openDetail(result.orderId);
          if (this.route.snapshot.queryParamMap.has('new')) {
            void this.router.navigate(['/orders'], { replaceUrl: true });
          }
        },
        error: (error) => {
          this.creating.set(false);
          this.createError.set(errorMessage(error));
        },
      });
  }

  openDetail(orderId: number): void {
    this.createOpen.set(false);
    this.detailOpen.set(true);
    this.detailLoading.set(true);
    this.detail.set(null);
    this.detailLog.set([]);
    this.addServiceError.set('');
    this.confirmClose.set(false);
    this.newService = { serviceId: null, quantity: 1 };

    this.api.order(orderId).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.detailLoading.set(false);
      },
      error: (error) => {
        this.detailLoading.set(false);
        this.toast.error(errorMessage(error));
        this.closeDetail();
      },
    });
    this.api.statusLog(orderId).subscribe({
      next: (entries) => this.detailLog.set(entries),
      error: () => this.detailLog.set([]),
    });
  }

  refreshDetail(orderId: number): void {
    this.api.order(orderId).subscribe({
      next: (detail) => this.detail.set(detail),
      error: (error) => this.toast.error(errorMessage(error)),
    });
    this.api.statusLog(orderId).subscribe({
      next: (entries) => this.detailLog.set(entries),
      error: () => this.detailLog.set([]),
    });
  }

  closeDetail(): void {
    if (this.closing()) {
      return;
    }
    this.detailOpen.set(false);
    this.confirmClose.set(false);
    if (this.route.snapshot.queryParamMap.has('order')) {
      void this.router.navigate(['/orders'], { replaceUrl: true });
    }
  }

  statusName(id: number | null): string {
    if (id === null) {
      return '—';
    }
    return this.statuses().find((status) => status.id === id)?.name ?? `статус ${id}`;
  }

  isFinished(status: string): boolean {
    return status === 'завершена' || status === 'отменена';
  }

  addService(): void {
    const detail = this.detail();
    if (!detail) {
      return;
    }
    if (!this.newService.serviceId) {
      this.addServiceError.set('Выберите услугу');
      return;
    }
    this.addingService.set(true);
    this.addServiceError.set('');
    this.api
      .addService(
        detail.order.id,
        this.newService.serviceId,
        Number(this.newService.quantity) || 1,
      )
      .subscribe({
        next: () => {
          this.addingService.set(false);
          this.newService = { serviceId: null, quantity: 1 };
          this.toast.success('Услуга добавлена в заявку');
          this.refreshDetail(detail.order.id);
          this.load();
        },
        error: (error) => {
          this.addingService.set(false);
          this.addServiceError.set(errorMessage(error));
        },
      });
  }

  askClose(): void {
    this.confirmClose.set(true);
  }

  cancelClose(): void {
    this.confirmClose.set(false);
  }

  doClose(): void {
    const detail = this.detail();
    if (!detail || this.closing()) {
      return;
    }
    this.closing.set(true);
    this.api.closeOrder(detail.order.id).subscribe({
      next: () => {
        this.closing.set(false);
        this.confirmClose.set(false);
        this.toast.success(`Заявка #${detail.order.id} завершена`);
        this.refreshDetail(detail.order.id);
        this.load();
      },
      error: (error) => {
        this.closing.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  serviceName(id: number | null): Service | undefined {
    return this.services().find((service) => service.id === Number(id));
  }
}