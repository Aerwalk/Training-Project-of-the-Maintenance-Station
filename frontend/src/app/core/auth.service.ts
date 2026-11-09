import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ApiService } from './api.service';
import { User } from './models';

const STORAGE_KEY = 'sto_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly currentUser = signal<User | null>(this.restore());

  readonly user = this.currentUser.asReadonly();

  login(username: string, password: string): Observable<User> {
    return this.api.login(username.trim(), password).pipe(
      tap((user) => {
        this.currentUser.set(user);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
      }),
    );
  }

  logout(): void {
    this.currentUser.set(null);
    localStorage.removeItem(STORAGE_KEY);
    void this.router.navigate(['/login']);
  }

  isAuthenticated(): boolean {
    return this.currentUser() !== null;
  }

  private restore(): User | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  }
}
