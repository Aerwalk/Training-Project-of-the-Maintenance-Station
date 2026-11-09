import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../../core/api.service';
import { Master, initials, plural, pluralWord } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

const AVATAR_GRADIENTS = [
  'linear-gradient(135deg, #3b82f6, #06b6d4)',
  'linear-gradient(135deg, #8b5cf6, #6366f1)',
  'linear-gradient(135deg, #0d9488, #22d3ee)',
  'linear-gradient(135deg, #f97316, #f59e0b)',
  'linear-gradient(135deg, #ec4899, #8b5cf6)',
];

@Component({
  selector: 'app-masters',
  standalone: true,
  imports: [Icon],
  templateUrl: './masters.html',
  styleUrl: './masters.css',
})
export class Masters {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly initials = initials;
  readonly plural = plural;
  readonly pluralWord = pluralWord;

  readonly loading = signal(true);
  readonly masters = signal<Master[]>([]);
  readonly workload = signal<Record<string, number>>({});
  readonly search = signal('');

  readonly filtered = computed(() => {
    const query = this.search().trim().toLowerCase();
    if (!query) {
      return this.masters();
    }
    return this.masters().filter(
      (master) =>
        master.fullName.toLowerCase().includes(query) ||
        (master.specialization ?? '').toLowerCase().includes(query),
    );
  });

  readonly activeMasters = computed(() => this.masters().filter((master) => master.active).length);

  readonly specializationCount = computed(
    () => new Set(this.masters().map((master) => master.specialization).filter(Boolean)).size,
  );

  readonly totalWorkload = computed(() =>
    this.masters().reduce((sum, master) => sum + this.workloadOf(master.id), 0),
  );

  readonly maxWorkload = computed(() =>
    Math.max(1, ...this.masters().map((master) => this.workloadOf(master.id))),
  );

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({ masters: this.api.masters(), workload: this.api.masterWorkload() }).subscribe({
      next: (data) => {
        this.masters.set(data.masters);
        this.workload.set(data.workload);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  workloadOf(masterId: number): number {
    return Number(this.workload()[String(masterId)] ?? 0);
  }

  workloadPercent(masterId: number): number {
    return Math.round((this.workloadOf(masterId) / this.maxWorkload()) * 100);
  }

  avatarGradient(masterId: number): string {
    return AVATAR_GRADIENTS[masterId % AVATAR_GRADIENTS.length];
  }
}
