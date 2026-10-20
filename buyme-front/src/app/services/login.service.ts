import {Inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {catchError, Observable, throwError} from 'rxjs';
import {APP_CONFIG} from '../../main';
import {AuthStorageService} from '../core/auth-storage.service';

@Injectable({
  providedIn: 'root'
})
export class LoginService {

  private apiUrl: string;

  constructor(
    private http: HttpClient,
    private router: Router,
    private authStorage: AuthStorageService,
    @Inject(APP_CONFIG) private config: any
  ) {
    this.apiUrl = `${this.config.apiUrl}/api/auth`;
}

login(email: string, password: string): Observable<any> {
  const url = `${this.apiUrl}/login`;
  return this.http.post<any>(url, { email, password }).pipe(
    catchError(err => {
      // Do not log credentials or the token payload.
      console.error('Login failed:', err?.status, err?.error?.message);
      return throwError(err);
    })
  );
}

/**
 * The single logout implementation. Clears every token (including any stored
 * under unknown keys) plus per-user cart/discount state, then navigates home.
 */
  logout(): void {
    this.authStorage.clearSession();
    this.router.navigate(['/login']);
}
}
