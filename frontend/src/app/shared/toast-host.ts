import { Component, inject } from '@angular/core';
import { ToastService } from '../core/toast.service';
import { Icon } from './icon';

@Component({
  selector: 'app-toast-host',
  standalone: true,
  imports: [Icon],
  template: `
    <div class="toast-stack" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class.toast-error]="toast.kind === 'error'" [class.toast-info]="toast.kind === 'info'">
          <span class="toast-icon">
            <app-icon [name]="toast.kind === 'success' ? 'check' : toast.kind === 'error' ? 'alert' : 'spark'" [size]="18" />
          </span>
          <span>{{ toast.text }}</span>
          <button type="button" class="toast-close" (click)="toasts.dismiss(toast.id)" aria-label="Закрыть">
            <app-icon name="close" [size]="15" />
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastHost {
  readonly toasts = inject(ToastService);
}
