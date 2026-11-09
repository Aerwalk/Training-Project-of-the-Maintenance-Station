export interface User {
  id: number;
  username: string;
  fullName: string;
  role: string;
}

export interface OrderSummary {
  orderId: number;
  client: string;
  car: string;
  status: string;
  totalCost: number;
}

export interface Client {
  id: number;
  fullName: string;
  phone: string;
  email: string | null;
  createdAt: string;
}

export interface ClientOrder {
  orderId: number;
  carPlate: string;
  status: string;
  totalCost: number;
  createdAt: string;
}

export interface ClientDetail {
  client: Client;
  cars: Car[];
  orders: ClientOrder[];
}

export interface Car {
  id: number;
  clientId: number;
  brand: string;
  model: string;
  year: number | null;
  plateNum: string;
  vin: string | null;
}

export interface CarView extends Car {
  clientName: string;
}

export interface Service {
  id: number;
  name: string;
  price: number;
  durationMin: number | null;
}

export interface Master {
  id: number;
  userId: number;
  fullName: string;
  specialization: string | null;
  active: boolean;
}

export interface OrderStatus {
  id: number;
  name: string;
}

export interface Order {
  id: number;
  clientId: number;
  carId: number;
  masterId: number | null;
  statusId: number;
  statusName: string;
  description: string;
  createdAt: string;
  closedAt: string | null;
}

export interface OrderServiceItem {
  id: number;
  serviceId: number;
  serviceName: string;
  quantity: number;
  priceAt: number;
  lineTotal: number;
}

export interface OrderDetail {
  order: Order;
  client: Client;
  car: Car;
  masterName: string | null;
  total: number;
  services: OrderServiceItem[];
}

export interface StatusLogEntry {
  id: number;
  orderId: number;
  oldStatusId: number | null;
  newStatusId: number | null;
  changedAt: string;
  changedBy: string;
}

export interface Health {
  status: string;
  database: string;
}

export interface OrderRow extends OrderSummary {
  description?: string;
}

export const STATUS_CLASS: Record<string, string> = {
  'новая': 'new',
  'в работе': 'progress',
  'завершена': 'done',
  'отменена': 'cancel',
};

export function statusClass(status: string): string {
  return STATUS_CLASS[status] ?? 'neutral';
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

/**
 * Выбирает форму слова для русского счётного оборота.
 * pluralWord(1, 'заявка', 'заявки', 'заявок') → «заявка»
 */
export function pluralWord(
  count: number,
  one: string,
  few: string,
  many: string,
): string {
  const abs = Math.abs(count) % 100;
  const last = abs % 10;
  if (abs > 10 && abs < 20) {
    return many;
  }
  if (last === 1) {
    return one;
  }
  return last >= 2 && last <= 4 ? few : many;
}

/** Счётчик вместе с правильной формой слова: plural(2, 'услуга', 'услуги', 'услуг') → «2 услуги». */
export function plural(
  count: number,
  one: string,
  few: string,
  many: string,
): string {
  return `${count} ${pluralWord(count, one, few, many)}`;
}

export function money(value: number | null | undefined): string {
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 0,
  }).format(value ?? 0);
}

export function shortDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(value));
}

export function dateTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
