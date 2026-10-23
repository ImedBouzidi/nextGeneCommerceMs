import { Component } from '@angular/core';
//import { AuthService } from '../../services/auth.service';
import {LoginService} from "../../services/login.service";
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NgIf } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
//import {LoginResponseDto} from '../../models/login-response-dto';
import {LoginResponse} from '../../models/login-response';
import {AuthStorageService} from '../../core/auth-storage.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
  standalone: true,
  imports: [
    FormsModule,
    RouterModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    NgIf
  ]
})
export class LoginComponent {
  credentials = {email: '', password: ''};
  message: string | null = null;
  isLoading = false;

  constructor(private loginService: LoginService, private router: Router, private authStorage: AuthStorageService) {
  }

onLogin() {
  this.isLoading = true;
  this.message = null;

  // Drop any leftover session (expired or issued by a different realm) before
  // we authenticate, so a failed login never leaves a stale bearer token behind.
  this.authStorage.clearAllTokens();

  this.loginService.login(this.credentials.email, this.credentials.password)
    .subscribe({
      next: (res: LoginResponse) => {
        this.isLoading = false;

        // A response can carry a truthy but non-JWT `token` (the backend used to
        // leak a user id there on failure). Never store that as a session.
        const isUsable = !!res.token && AuthStorageService.decode(res.token) !== null;

        if (isUsable) {
          // setSession purges first, then stores only this realm's token.
          this.authStorage.setSession(res.token!, res.roles, res.refreshToken ?? null);
          this.message = res.message;

          // Normalize role names (remove "ROLE_" prefix if present)
          const roles = res.roles?.map(r => r.replace('ROLE_', ''));

          // ✅ Redirect based on normalized role
          if (roles?.includes('ADMIN')) {
            this.router.navigate(['/admin/dashboard']);
          } else if (roles?.includes('CLIENT')) {
            this.router.navigate(['/client/home']);
          } else {
            this.router.navigate(['/notfound']);
          }

        } else {
          this.message = res.message || 'Login failed: invalid credentials';
        }
      },
      error: (err: any) => {
        this.isLoading = false;
        console.error('Login failed:', err?.status);
        this.message = err?.error?.message || 'Login failed';
      }
    });
}
}