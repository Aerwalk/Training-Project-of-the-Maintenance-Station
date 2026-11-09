import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CarView,
  Client,
  ClientDetail,
  Health,
  Master,
  OrderDetail,
  OrderStatus,
  OrderSummary,
  Service,
  StatusLogEntry,
  User,
} from './models';

export const API_BASE = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  health(): Observable<Health> {
    return this.http.get<Health>(`${API_BASE}/health`);
  }

  login(username: string, password: string): Observable<User> {
    return this.http.post<User>(`${API_BASE}/login`, { username, password });
  }

  orders(): Observable<OrderSummary[]> {
    return this.http.get<OrderSummary[]>(`${API_BASE}/orders`);
  }

  order(id: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${API_BASE}/orders/${id}`);
  }

  createOrder(payload: {
    clientId: number;
    carId: number;
    masterId: number | null;
    description: string;
  }): Observable<{ orderId: number }> {
    return this.http.post<{ orderId: number }>(`${API_BASE}/orders`, payload);
  }

  addService(orderId: number, serviceId: number, quantity: number): Observable<unknown> {
    return this.http.post(`${API_BASE}/orders/${orderId}/services`, { serviceId, quantity });
  }

  closeOrder(orderId: number): Observable<unknown> {
    return this.http.post(`${API_BASE}/orders/${orderId}/close`, {});
  }

  clients(): Observable<Client[]> {
    return this.http.get<Client[]>(`${API_BASE}/clients`);
  }

  client(id: number): Observable<ClientDetail> {
    return this.http.get<ClientDetail>(`${API_BASE}/clients/${id}`);
  }

  cars(): Observable<CarView[]> {
    return this.http.get<CarView[]>(`${API_BASE}/cars`);
  }

  createCar(payload: {
    clientId: number;
    brand: string;
    model: string;
    year: number | null;
    plateNum: string;
    vin: string | null;
  }): Observable<CarView> {
    return this.http.post<CarView>(`${API_BASE}/cars`, payload);
  }

  services(): Observable<Service[]> {
    return this.http.get<Service[]>(`${API_BASE}/services`);
  }

  masters(): Observable<Master[]> {
    return this.http.get<Master[]>(`${API_BASE}/masters`);
  }

  masterWorkload(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${API_BASE}/masters/workload`);
  }

  statuses(): Observable<OrderStatus[]> {
    return this.http.get<OrderStatus[]>(`${API_BASE}/statuses`);
  }

  statusLog(orderId?: number): Observable<StatusLogEntry[]> {
    const suffix = orderId ? `?orderId=${orderId}` : '';
    return this.http.get<StatusLogEntry[]>(`${API_BASE}/log${suffix}`);
  }

  createClient(payload: { fullName: string; phone: string; email: string | null }): Observable<Client> {
    return this.http.post<Client>(`${API_BASE}/clients`, payload);
  }

  createService(payload: {
    name: string;
    price: number;
    durationMin: number | null;
  }): Observable<Service> {
    return this.http.post<Service>(`${API_BASE}/services`, payload);
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error;
    if (body && typeof body === 'object' && 'message' in body) {
      return String((body as { message: unknown }).message);
    }
    if (error.status === 0) {
      return 'Нет связи с API. Запустите: java -jar target/sto-db-module.jar --web';
    }
    return `Ошибка ${error.status}: ${error.statusText}`;
  }
  return error instanceof Error ? error.message : 'Неизвестная ошибка';
}
