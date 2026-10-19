import { Component, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { LoginService } from '../../services/login.service';
import { CommonModule } from '@angular/common';
import { UserProfile, UserService } from '../../services/user.service';
import { ThemeService } from '../../services/theme.service';
import { AuthStorageService } from '../../core/auth-storage.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule
  ],
  templateUrl: './nav-bar.component.html',
  styleUrl: './nav-bar.component.css'
})
export class NavbarComponent implements OnInit, OnDestroy {
  isAuthenticated = false;
  currentUser: UserProfile | null = null;
  userAvatar = 'assets/img/default-avatar.svg';
  mobileMenuOpen = false;
  private routerSub?: Subscription;
  private userSub?: Subscription;
  private cachedToken: string | null = null;

  constructor(
    private router: Router,
    private loginService: LoginService,
    private userService: UserService,
    private themeService: ThemeService,
    private authStorage: AuthStorageService
  ) {
    this.routerSub = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        this.mobileMenuOpen = false;
        this.loadUserProfile();
      });
  }

  ngOnInit(): void {
    this.loadUserProfile();
  }

  isDarkMode(): boolean {
    return this.themeService.isDark();
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  logout() {
    this.loginService.logout();
    this.isAuthenticated = false;
    this.currentUser = null;
    this.userAvatar = 'assets/img/default-avatar.svg';
    this.cachedToken = null;
    this.userSub?.unsubscribe();
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
    this.userSub?.unsubscribe();
  }

  private loadUserProfile(): void {
    // getValidToken() returns null for expired / foreign-realm tokens (and purges them).
    const token = this.authStorage.getValidToken();
    this.isAuthenticated = !!token;
    if (!token) {
      this.currentUser = null;
      this.userAvatar = 'assets/img/default-avatar.svg';
      this.cachedToken = null;
      this.userSub?.unsubscribe();
      return;
    }

    if (token === this.cachedToken && this.currentUser) {
      return;
    }
    this.cachedToken = token;

    this.userSub?.unsubscribe();
    this.userSub = this.userService.getCurrentUser().subscribe({
      next: (user) => {
        this.currentUser = user;
        this.userAvatar = this.userService.resolveProfileImage(user.profilePic);
      },
      error: () => {
        this.currentUser = null;
        this.userAvatar = 'assets/img/default-avatar.svg';
        this.cachedToken = null;
      }
    });
  }
}
