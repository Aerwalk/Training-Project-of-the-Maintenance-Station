import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

interface DemoAccount {
  role: string;
  username: string;
  password: string;
  icon: string;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, Icon],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  username = 'admin';
  password = 'admin123';
  showPassword = signal(false);
  loading = signal(false);
  error = signal('');

  readonly demoAccounts: DemoAccount[] = [
    { role: 'Администратор', username: 'admin', password: 'admin123', icon: 'settings' },
    { role: 'Менеджер', username: 'manager', password: 'manager123', icon: 'orders' },
    { role: 'Мастер', username: 'master', password: 'master123', icon: 'masters' },
    { role: 'Клиент', username: 'client', password: 'client123', icon: 'clients' },
  ];

  constructor() {
    if (this.auth.isAuthenticated()) {
      void this.router.navigate(['/dashboard']);
    }
  }

  useAccount(account: DemoAccount): void {
    this.username = account.username;
    this.password = account.password;
    this.error.set('');
  }

  submit(): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.auth.login(this.username, this.password).subscribe({
      next: (user) => {
        this.toast.success(`С возвращением, ${user.fullName.split(' ')[0]}!`);
        void this.router.navigate(['/dashboard']);
      },
      error: (error) => {
        this.loading.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }
}
