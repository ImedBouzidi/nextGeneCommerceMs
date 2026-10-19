import { Injectable, Inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../main';

export interface ReviewDto {
  rating: number;
  comment?: string;
}

@Injectable({ providedIn: 'root' })
export class ReviewService {
  private apiUrl: string;

  constructor(private http: HttpClient, @Inject(APP_CONFIG) private config: any) {
    this.apiUrl = `${this.config.apiUrl}/reviews`;
  }

  getByProduct(productId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/product/${productId}`);
  }

  addReview(productId: number, payload: ReviewDto) {
    return this.http.post<any>(`${this.apiUrl}/product/${productId}`, payload);
  }
}
