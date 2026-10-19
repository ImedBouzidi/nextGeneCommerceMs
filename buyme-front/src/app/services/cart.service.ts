import { Injectable, Inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { APP_CONFIG } from '../../main';

export interface CartItem {
  productId: number;
  name: string;
  price: number;
  quantity: number;
  image?: string | null;
  stock?: number;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly itemsSubject = new BehaviorSubject<CartItem[]>(this.loadFromStorage());
  readonly items$ = this.itemsSubject.asObservable();

  // discount percent applied to the user (0-100)
  private readonly discountSubject = new BehaviorSubject<number>(this.loadDiscountFromStorage());
  readonly discount$ = this.discountSubject.asObservable();

  private readonly apiUrl: string;

  constructor(private readonly http: HttpClient, @Inject(APP_CONFIG) private readonly config: any) {
    const base = (this.config?.apiUrl || '').replace(/\/$/, '');
    // backend orders endpoint is under /api/orders
    this.apiUrl = base.replace(/\/api$/, '') + '/api/orders';
  }

  private saveDiscountToStorage(pct: number) {
    localStorage.setItem('user_discount_percent', String(pct || 0));
  }

  private loadDiscountFromStorage(): number {
    const raw = localStorage.getItem('user_discount_percent');
    const v = raw ? Number(raw) : 0;
    return isNaN(v) ? 0 : v;
  }

  getDiscountPercent(): number {
    return this.discountSubject.value || 0;
  }

  setDiscountPercent(pct: number) {
    const n = Math.max(0, Math.min(100, Number(pct) || 0));
    this.discountSubject.next(n);
    this.saveDiscountToStorage(n);
  }

  getTotalWithDiscount(items: CartItem[]): number {
    const subtotal = items.reduce((s, it) => s + (it.price * it.quantity), 0);
    const pct = this.getDiscountPercent();
    const discounted = subtotal * (1 - pct / 100);
    return Math.max(0, discounted);
  }

  private saveToStorage(items: CartItem[]) {
    localStorage.setItem('cart_items', JSON.stringify(items));
  }

  private loadFromStorage(): CartItem[] {
    const raw = localStorage.getItem('cart_items');
    return raw ? JSON.parse(raw) : [];
  }

  getItems(): CartItem[] {
    return this.itemsSubject.value;
  }

  addToCartProduct(product: { id: number; name: string; price: number; images?: string[]; stock?: number }, qty = 1) {
    const items = [...this.getItems()];
    const idx = items.findIndex(i => i.productId === product.id);
    const image = product.images?.length ? product.images[0] : null;
    if (idx === -1) {
      items.push({ productId: product.id, name: product.name, price: product.price, quantity: qty, image, stock: product.stock });
    } else {
      items[idx].quantity += qty;
      // update stock if provided
      if (typeof product.stock === 'number') {
        items[idx].stock = product.stock;
      }
    }
    this.itemsSubject.next(items);
    this.saveToStorage(items);
  }

  addItem(item: CartItem) {
    this.addToCartProduct({ id: item.productId, name: item.name, price: item.price, images: item.image ? [item.image] : [], stock: item.stock }, item.quantity);
  }

  removeItem(productId: number) {
    const items = this.getItems().filter(i => i.productId !== productId);
    this.itemsSubject.next(items);
    this.saveToStorage(items);
  }

  clear() {
    this.itemsSubject.next([]);
    this.saveToStorage([]);
  }

  // place order using backend
  placeOrder(orderPayload: any) {
    const url = `${this.apiUrl}/place-order`;
    console.debug('[CartService] placeOrder ->', { url, orderPayload });
    return this.http.post<any>(url, orderPayload);
  }
}
