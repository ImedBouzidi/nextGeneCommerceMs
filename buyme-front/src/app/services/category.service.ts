import { Injectable, Inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../main';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private apiUrl: string;

  constructor(private http: HttpClient, @Inject(APP_CONFIG) private config: any) {
    this.apiUrl = `${this.config.apiUrl}/categories`;
  }

  getAll(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/all`);
  }

  getById(id: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}`);
  }

  create(category: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/create`, category);
  }

  update(id: number, category: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/update/${id}`, category);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/delete/${id}`);
  }
}
