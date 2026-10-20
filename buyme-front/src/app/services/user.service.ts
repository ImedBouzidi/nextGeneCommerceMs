import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { APP_CONFIG } from '../../main';

export interface UserProfile {
  id?: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: number;
  dob?: string;
  country?: string;
  city?: string;
  zip?: number;
  address?: string;
  profilePic?: string;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private apiUrl: string;
  private profilePicBaseUrl: string;

  constructor(
    private http: HttpClient,
    @Inject(APP_CONFIG) private config: any
  ) {
    this.apiUrl = `${this.config.apiUrl}/api/auth`;
    this.profilePicBaseUrl = `${this.config.apiUrl}/api/uploads/profiles-pics`;
  }

  // Try common endpoints for fetching the current user. Some backends expose /auth/me, others use /auth/profile or /users/me.
  getCurrentUser(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.apiUrl}/me`).pipe(
      catchError(() => this.http.get<UserProfile>(`${this.apiUrl}/profile`)),
      catchError(() => this.http.get<UserProfile>(`${this.config.apiUrl.replace(/\/\/$/, '')}/users/me`))
    );
  }

  // Try to resolve an arbitrary user by id (best-effort across possible auth API shapes)
  // Returns Observable<UserProfile | null> so callers can gracefully handle missing profiles.
  getUserById(userId: number): Observable<UserProfile | null> {
    // Call the auth service user-by-id endpoint we just added.
    return this.http.get<UserProfile>(`${this.apiUrl}/users/${userId}`).pipe(
      catchError(() => of(null))
    );
  }

  resolveProfileImage(filename?: string | null): string {
    if (!filename) {
      return 'assets/img/default-avatar.svg';
    }
    return `${this.profilePicBaseUrl}/${filename}`;
  }
}

