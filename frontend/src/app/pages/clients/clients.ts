import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import {
  CarView,
  Client,
  ClientDetail,
  initials,
  money,
  plural,
  pluralWord,
  shortDate,
  statusClass,
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [FormsModule, RouterLink, Icon],
  templateUrl: './clients.html',
  styleUrl: './clients.css',
})
export class Clients {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly initials = initials;
  readonly money = money;
  readonly plural = plural;
  readonly pluralWord = pluralWord;
  readonly shortDate = shortDate;
  readonly statusClass = statusClass;

  readonly loading = signal(true);
  readonly clients = signal<Client[]>([]);
  readonly cars = signal<CarView[]>([]);
  readonly search = signal('');

  readonly createOpen = signal(false);
  readonly createError = signal('');
  readonly creating = signal(false);

  readonly detailOpen = signal(false);
  readonly detailLoading = signal(false);
  readonly detail = signal<ClientDetail | null>(null);

  newClient = { fullName: '', phone: '', email: '' };

  readonly filtered = computed(() => {
    const query = this.search().trim().toLowerCase();
    if (!query) {
      return this.clients();
    }
    return this.clients().filter(
      (client) =>
        client.fullName.toLowerCase().includes(query) ||
        client.phone.toLowerCase().includes(query) ||
        (client.email ?? '').toLowerCase().includes(query),
    );
  });

  readonly withEmail = computed(() => this.clients().filter((client) => !!client.email).length);

  readonly avgCars = computed(() =>
    this.clients().length === 0
      ? '0'
      : (this.cars().length / this.clients().length).toFixed(1).replace('.', ','),
  );

  readonly detailTotal = computed(() =>
    (this.detail()?.orders ?? []).reduce((sum, order) => sum + Number(order.totalCost ?? 0), 0),
  );

  readonly detailLastVisit = computed(() => {
    const orders = this.detail()?.orders ?? [];
    if (orders.length === 0) {
      return null;
    }
    return orders
      .map((order) => order.createdAt)
      .sort()
      .at(-1) ?? null;
  });

  constructor() {
    this.load();
    this.route.queryParamMap.subscribe((params) => {
      if (params.get('new') === '1' && !this.createOpen()) {
        this.openCreate();
      }
      const clientId = Number(params.get('client'));
      if (
        Number.isFinite(clientId) &&
        clientId > 0 &&
        (!this.detail() || this.detail()?.client.id !== clientId)
      ) {
        this.openDetail(clientId);
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
    forkJoin({ clients: this.api.clients(), cars: this.api.cars() }).subscribe({
      next: (data) => {
        this.clients.set(data.clients);
        this.cars.set(data.cars);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  carsOf(clientId: number): CarView[] {
    return this.cars().filter((car) => car.clientId === clientId);
  }

  openCreate(): void {
    this.newClient = { fullName: '', phone: '', email: '' };
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
      void this.router.navigate(['/clients'], { replaceUrl: true });
    }
  }

  canCreate(): boolean {
    return (
      !this.creating() && this.newClient.fullName.trim().length > 1 && this.newClient.phone.trim().length > 4
    );
  }

  create(): void {
    if (!this.canCreate()) {
      this.createError.set('Укажите ФИО и телефон клиента');
      return;
    }
    this.creating.set(true);
    this.createError.set('');
    this.api
      .createClient({
        fullName: this.newClient.fullName.trim(),
        phone: this.newClient.phone.trim(),
        email: this.newClient.email.trim() || null,
      })
      .subscribe({
        next: (client) => {
          this.creating.set(false);
          this.createOpen.set(false);
          this.toast.success(`Клиент ${client.fullName} добавлен`);
          this.load();
        },
        error: (error) => {
          this.creating.set(false);
          this.createError.set(errorMessage(error));
        },
      });
  }

  openDetail(clientId: number): void {
    this.createOpen.set(false);
    this.detailOpen.set(true);
    this.detailLoading.set(true);
    this.detail.set(null);
    this.api.client(clientId).subscribe({
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
  }

  closeDetail(): void {
    this.detailOpen.set(false);
    if (this.route.snapshot.queryParamMap.has('client')) {
      void this.router.navigate(['/clients'], { replaceUrl: true });
    }
  }
}
