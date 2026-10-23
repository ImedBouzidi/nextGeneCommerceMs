import { Injectable, Inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';
import { APP_CONFIG } from '../../main';

export interface TopCustomer {
  customer_id: string;
  recency: number;
  frequency: number;
  monetary: number;
  avg_rating: number;
  total_ratings: number;
  max_rating: number;
  total_interactions: number;
  total_views: number;
  total_clicks: number;
  total_cart_adds: number;
  total_wishlist: number;
  avg_session_duration: number;
  buying_score: number;
  rating_score: number;
  interaction_score: number;
  engagement_score: number;
}

export interface TopCustomersResponse {
  customers: TopCustomer[];
  total_customers: number;
  sort_by: string;
  limit: number;
}

export interface GenerateDataRequest {
  n_customers?: number;
  max_orders_per_customer?: number;
  days_span?: number;
  products_count?: number;
}

export interface GenerateDataResponse {
  message: string;
  orders_count: number;
  ratings_count: number;
  interactions_count: number;
  customers_count: number;
}

export interface CustomerDetail {
  customer: TopCustomer;
  orders: any[];
  ratings: any[];
  interactions: any[];
}

export interface RunSegmentationRequest {
  orders_path?: string;
  n_clusters?: number;
  send?: boolean;
  userId?: number | string;
}

export interface Promotion {
  customer_id: string;
  segment: string;
  discount_pct: number;
  discount?: number;
  discount_percent?: number;
  loyalty_score: number;
  display_name?: string;
}

export interface RunSegmentationResponse {
  promotions: Promotion[];
}

@Injectable({
  providedIn: 'root'
})
export class CustomerSegmentationService {
  private gatewayUrl: string;

  constructor(private http: HttpClient, @Inject(APP_CONFIG) private config: any) {
    const base = (this.config?.apiUrl || '').replace(/\/$/, '');
    const gatewayBase = base.replace(/\/api$/, '');
    this.gatewayUrl = `${gatewayBase}/segmentation`;
  }

  generateData(request: GenerateDataRequest = {}): Observable<GenerateDataResponse> {
    return this.http.post<GenerateDataResponse>(`${this.gatewayUrl}/generate-data`, request).pipe(
      catchError((err) => {
        console.error('[CustomerSegmentationService] generateData failed', err);
        return of({ message: 'Failed to generate data', orders_count: 0, ratings_count: 0, interactions_count: 0, customers_count: 0 });
      })
    );
  }

  getTopCustomers(limit: number = 10, sortBy: string = 'engagement_score'): Observable<TopCustomersResponse> {
    return this.http.get<TopCustomersResponse>(`${this.gatewayUrl}/top-customers`, {
      params: { limit: limit.toString(), sort_by: sortBy }
    }).pipe(
      catchError((err) => {
        console.error('[CustomerSegmentationService] getTopCustomers failed', err);
        return of({ customers: [], total_customers: 0, sort_by: sortBy, limit });
      })
    );
  }

  getCustomerDetail(customerId: string): Observable<CustomerDetail | null> {
    return this.http.get<CustomerDetail>(`${this.gatewayUrl}/customer/${customerId}`).pipe(
      catchError((err) => {
        console.error('[CustomerSegmentationService] getCustomerDetail failed', err);
        return of(null);
      })
    );
  }

  runSegmentation(request: RunSegmentationRequest = {}): Observable<RunSegmentationResponse> {
    return this.http.post<RunSegmentationResponse>(`${this.gatewayUrl}/run-segmentation`, request).pipe(
      catchError((err) => {
        console.error('[CustomerSegmentationService] runSegmentation failed', err);
        return of({ promotions: [] });
      })
    );
  }
}