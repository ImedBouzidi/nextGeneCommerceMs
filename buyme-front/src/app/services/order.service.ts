import { Injectable, Inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../main';

export interface OrderItem {
  id: number;
  qteOrdered: number;
  orderDate: string;
  productId: number;
  userId: number;
  mobile?: string;
  status: string;
  paymentStatus: string;
  paymentMethod?: string;   // mirrors the payment-service enum, incl. Tunisian methods
  approvedBy?: number;
  approvedAt?: string;
}

export interface CreateOrderRequest {
  productId: number;
  qteOrdered: number;
  mobile: string;
  paymentMethod: string;
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly apiUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) private readonly config: any
  ) {
    const base = (this.config?.apiUrl || '').replace(/\/$/, '');
    this.apiUrl = base.replace(/\/api$/, '') + '/api/orders';
  }

  getMyOrders(): Observable<OrderItem[]> {
    return this.http.get<OrderItem[]>(`${this.apiUrl}/my-orders`);
  }

  // ← ADD THIS METHOD
  createOrder(payload: CreateOrderRequest): Observable<OrderItem> {
    return this.http.post<OrderItem>(`${this.apiUrl}/place-order`, payload);
  }

  getAllOrders(): Observable<OrderItem[]> {
    const url = `${this.apiUrl}/all`;
    return this.http.get<OrderItem[]>(url);
  }

  approveOrder(orderId: number, force: boolean = false): Observable<OrderItem> {
    const url = `${this.apiUrl}/${orderId}/approve${force ? '?force=true' : ''}`;
    return this.http.put<OrderItem>(url, {});
  }

  // Admin declines or deletes (cancel) an order
  // Admin decline -> calls admin decline endpoint
  declineOrder(orderId: number): Observable<OrderItem> {
    const url = `${this.apiUrl}/${orderId}/decline`;
    return this.http.put<OrderItem>(url, {});
  }

  // Admin permanent delete
  deleteOrder(orderId: number): Observable<void> {
    const url = `${this.apiUrl}/${orderId}/admin`;
    return this.http.delete<void>(url);
  }

  // Client delete (cancel) their own order
  deleteMyOrder(orderId: number): Observable<void> {
    const url = `${this.apiUrl}/${orderId}`;
    return this.http.delete<void>(url);
  }

  // Client cancel request (soft-cancel) - backend may implement a dedicated cancel endpoint
  cancelMyOrder(orderId: number): Observable<OrderItem> {
    const url = `${this.apiUrl}/${orderId}/cancel`;
    return this.http.put<OrderItem>(url, {});
  }

  // Mark payment status (used by admin to mark COD as paid when delivered)
  markPaymentPaid(orderId: number) {
    const url = `${this.apiUrl}/${orderId}/payment-status?paymentStatus=PAID`;
    return this.http.put<OrderItem>(url, {});
  }

  markAsDelivered(orderId: number): Observable<OrderItem> {
  const url = `${this.apiUrl}/${orderId}/delivered`;
  return this.http.put<OrderItem>(url, {});
}
}
