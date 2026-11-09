import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../../core/api.service';
import { Service, money, pluralWord } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

type SortMode = 'name' | 'price-asc' | 'price-desc';

@Component({
  selector: 'app-services',
  standalone: true,
  imports: [FormsModule, Icon],
  templateUrl: './services.html',
  styleUrl: './services.css',
})
export class ServicesPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly money = money;
  readonly pluralWord = pluralWord;

  readonly loading = signal(true);
  readonly services = signal<Service[]>([]);
  readonly search = signal('');
  readonly sort = signal<SortMode>('name');

  readonly createOpen = signal(false);
  readonly createError = signal('');
  readonly creating = signal(false);

  newService = { name: '', price: null as number | null, durationMin: null as number | null };

  readonly filtered = computed(() => {
    const query = this.search().trim().toLowerCase();
    let result = this.services();
    if (query) {
      result = result.filter((service) => service.name.toLowerCase().includes(query));
    }
    return [...result].sort((a, b) => {
      switch (this.sort()) {
        case 'price-asc':
          return Number(a.price) - Number(b.price);
        case 'price-desc':
          return Number(b.price) - Number(a.price);
        default:
          return a.name.localeCompare(b.name, 'ru');
      }
    });
  });

  readonly avgPrice = computed(() => {
    if (this.services().length === 0) {
      return 0;
    }
    return this.services().reduce((sum, service) => sum + Number(service.price), 0) / this.services().length;
  });

  readonly maxPrice = computed(() =>
    this.services().reduce((max, service) => Math.max(max, Number(service.price)), 0),
  );

  readonly avgDuration = computed(() => {
    const withDuration = this.services().filter((service) => !!service.durationMin);
    if (withDuration.length === 0) {
      return 0;
    }
    return Math.round(
      withDuration.reduce((sum, service) => sum + Number(service.durationMin), 0) /
        withDuration.length,
    );
  });

  constructor() {
    this.load();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.createOpen()) {
      this.closeCreate();
    }
  }

  load(): void {
    this.loading.set(true);
    this.api.services().subscribe({
      next: (services) => {
        this.services.set(services);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  openCreate(): void {
    this.newService = { name: '', price: null, durationMin: null };
    this.createError.set('');
    this.createOpen.set(true);
  }

  closeCreate(): void {
    if (this.creating()) {
      return;
    }
    this.createOpen.set(false);
  }

  canCreate(): boolean {
    return (
      !this.creating() &&
      this.newService.name.trim().length > 1 &&
      this.newService.price !== null &&
      Number(this.newService.price) >= 0
    );
  }

  create(): void {
    if (!this.canCreate()) {
      this.createError.set('Укажите название и неотрицательную стоимость услуги');
      return;
    }
    this.creating.set(true);
    this.createError.set('');
    this.api
      .createService({
        name: this.newService.name.trim(),
        price: Number(this.newService.price),
        durationMin: this.newService.durationMin ? Number(this.newService.durationMin) : null,
      })
      .subscribe({
        next: (service) => {
          this.creating.set(false);
          this.createOpen.set(false);
          this.toast.success(`Услуга «${service.name}» добавлена`);
          this.load();
        },
        error: (error) => {
          this.creating.set(false);
          this.createError.set(errorMessage(error));
        },
      });
  }
}
