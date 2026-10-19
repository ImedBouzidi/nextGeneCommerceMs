import { Injectable, Inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';
import { APP_CONFIG } from '../../main';
import { AuthStorageService } from '../core/auth-storage.service';

export interface SegmentationRequest {
  orders_path?: string;
  n_clusters?: number;
  send?: boolean;
  kafka_bootstrap?: string;
  userId?: string;
  user_id?: string;
}

export interface Promotion {
  customer_id?: string;
  segment?: string;
  discount?: number;
  display_name?: string;
  discount_percent?: number;
  [key: string]: any;
}

export interface SegmentationResponse {
  promotions: Promotion[];
}

@Injectable({
  providedIn: 'root'
})
export class CustomerSegmentationService {
  private gatewayUrl: string;

  constructor(private http: HttpClient, private authStorage: AuthStorageService, @Inject(APP_CONFIG) private config: any) {
    // Get hostname from current location for better compatibility
    const hostname = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
    
    // Gateway URL (preferred)
    const base = (this.config?.apiUrl || '').replace(/\/$/, '');
    const gatewayBase = base.replace(/\/api$/, '');
    this.gatewayUrl = `${gatewayBase}/segmentation/run-segmentation`;
  }

  runSegmentation(request?: SegmentationRequest): Observable<SegmentationResponse> {
    const payload: SegmentationRequest = request || {
      n_clusters: 3,
      send: false
    };
    // try to include current user id (sub) from JWT to help backend fetch user orders
    try {
      const token = this.authStorage.getValidToken() || '';
      if (token) {
        const parts = token.split('.');
        if (parts.length >= 2) {
          const payloadJson = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
          if (payloadJson && payloadJson.sub && !payload['userId'] && !payload['user_id']) {
            // attach userId for backend convenience (backend may ignore it)
            (payload as any).userId = payloadJson.sub;
          }
        }
      }
    } catch (e) {
      // ignore token decode errors
    }

    // The Authorization header is attached by authInterceptor (core/auth.interceptor.ts).
    // Building it here bypassed that logic and sent an empty header when logged out.

    // Use API Gateway only to avoid CORS duplication and direct exposure
    return this.http.post<SegmentationResponse>(this.gatewayUrl, payload).pipe(
      catchError((err) => {
        // Log full response body (err.error) if present and the exact payload sent
        try {
          console.error('Segmentation 400 details:', err && err.error ? err.error : err, 'Payload:', payload);
        } catch (e) {
          console.error('[CustomerSegmentationService] gateway request failed (unable to stringify)', err, 'Payload:', payload);
        }
        return of({ promotions: [] });
      })
    );
  }
}

