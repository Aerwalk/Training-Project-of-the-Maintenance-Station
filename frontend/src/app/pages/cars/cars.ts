import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import { CarView, Client, initials, plural, pluralWord } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

const GRADIENTS = [
  'linear-gradient(135deg, #3b82f6, #06b6d4)',
  'linear-gradient(135deg, #8b5cf6, #6366f1)',
  'linear-gradient(135deg, #0d9488, #22d3ee)',
  'linear-gradient(135deg, #f97316, #f59e0b)',
  'linear-gradient(135deg, #ec4899, #8b5cf6)',
  'linear-gradient(135deg, #0ea5e9, #2563eb)',
];

@Component({
  selector: 'app-cars',
  standalone: true,
  imports: [FormsModule, RouterLink, Icon],
  templateUrl: './cars.html',
  styleUrl: './cars.css',
})
export class Cars {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly initials = initials;
  readonly plural = plural;
  readonly pluralWord = pluralWord;

  readonly loading = signal(true);
  readonly cars = signal<CarView[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly search = signal('');

  readonly createOpen = signal(false);
  readonly createError = signal('');
  readonly creating = signal(false);

  newCar = {
    clientId: null as number | null,
    brand: '',
    model: '',
    year: null as number | null,
    plateNum: '',
    vin: '',
  };

  readonly filtered = computed(() => {
    const query = this.search().trim().toLowerCase();
    if (!query) {
      return this.cars();
    }
    return this.cars().filter(
      (car) =>
        car.brand.toLowerCase().includes(query) ||
        car.model.toLowerCase().includes(query) ||
        car.plateNum.toLowerCase().includes(query) ||
        (car.vin ?? '').toLowerCase().includes(query) ||
        car.clientName.toLowerCase().includes(query),
    );
  });

  readonly brands = computed(() => new Set(this.cars().map((car) => car.brand)).size);

  readonly owners = computed(() => new Set(this.cars().map((car) => car.clientId)).size);

  readonly recentCars = computed(
    () => this.cars().filter((car) => (car.year ?? 0) >= new Date().getFullYear() - 5).length,
  );

  constructor() {
    this.load();
    this.route.queryParamMap.subscribe((params) => {
      if (params.get('new') === '1') {
        const clientId = Number(params.get('clientId'));
        this.openCreate(Number.isFinite(clientId) && clientId > 0 ? clientId : null);
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.createOpen()) {
      this.closeCreate();
    }
  }

  load(): void {
    this.loading.set(true);
    forkJoin({ cars: this.api.cars(), clients: this.api.clients() }).subscribe({
      next: (data) => {
        this.cars.set(data.cars);
        this.clients.set(data.clients);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  brandGradient(brand: string): string {
    let hash = 0;
    for (const char of brand) {
      hash = (hash + char.charCodeAt(0)) % GRADIENTS.length;
    }
    return GRADIENTS[hash];
  }

  openCreate(clientId: number | null = null): void {
    this.newCar = { clientId, brand: '', model: '', year: null, plateNum: '', vin: '' };
    this.createError.set('');
    this.createOpen.set(true);
  }

  closeCreate(): void {
    if (this.creating()) {
      return;
    }
    this.createOpen.set(false);
    if (this.route.snapshot.queryParamMap.has('new')) {
      void this.router.navigate(['/cars'], { replaceUrl: true });
    }
  }

  selectedClient(): Client | undefined {
    return this.clients().find((client) => client.id === this.newCar.clientId);
  }

  canCreate(): boolean {
    return (
      !this.creating() &&
      this.newCar.clientId !== null &&
      this.newCar.brand.trim().length > 0 &&
      this.newCar.model.trim().length > 0 &&
      this.newCar.plateNum.trim().length > 0
    );
  }

  create(): void {
    if (!this.canCreate()) {
      this.createError.set('Выберите владельца и заполните марку, модель и гос. номер');
      return;
    }
    this.creating.set(true);
    this.createError.set('');
    this.api
      .createCar({
        clientId: this.newCar.clientId!,
        brand: this.newCar.brand.trim(),
        model: this.newCar.model.trim(),
        year: this.newCar.year ?? null,
        plateNum: this.newCar.plateNum.trim(),
        vin: this.newCar.vin.trim() || null,
      })
      .subscribe({
        next: (car) => {
          this.creating.set(false);
          this.createOpen.set(false);
          this.toast.success(`Автомобиль ${car.brand} ${car.model} добавлен`);
          this.load();
          if (this.route.snapshot.queryParamMap.has('new')) {
            void this.router.navigate(['/cars'], { replaceUrl: true });
          }
        },
        error: (error) => {
          this.creating.set(false);
          this.createError.set(errorMessage(error));
        },
      });
  }
}
