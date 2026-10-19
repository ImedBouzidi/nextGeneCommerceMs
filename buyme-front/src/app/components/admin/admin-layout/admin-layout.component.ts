import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { AuthStorageService } from '../../../core/auth-storage.service';
import { LoginService } from '../../../services/login.service';

interface AdminNavItem {
  path: string;
  label: string;
  hint: string;
  icon: string;
}

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-layout.component.html',
  styleUrls: ['./admin-layout.component.css']
})
export class AdminLayoutComponent implements OnInit, OnDestroy {
  /**
   * Each entry maps to an admin use case that the backend actually implements.
   * Nothing here is a placeholder — see the route table in app.routes.ts.
   */
  readonly navItems: AdminNavItem[] = [
    { path: '/admin/overview', label: 'Overview', hint: 'Store health at a glance', icon: 'fa-gauge-high' },
    { path: '/admin/orders', label: 'Orders', hint: 'Review and approve orders', icon: 'fa-receipt' },
    { path: '/admin/products', label: 'Products', hint: 'Add, edit and remove items', icon: 'fa-boxes-stacked' },
    { path: '/admin/categories', label: 'Categories', hint: 'Group the catalogue', icon: 'fa-layer-group' },
    { path: '/admin/payments', label: 'Payments', hint: 'Transaction history', icon: 'fa-credit-card' },
  ];

  /** Kept out of navItems so it renders in the sidebar footer, visually separated. */
  readonly accountItem: AdminNavItem = {
    path: '/admin/account', label: 'My account', hint: 'Profile, roles and session', icon: 'fa-user-gear',
  };

  sidebarOpen = false;
  adminName = 'Administrator';

  private routerSub?: Subscription;

  constructor(
    private router: Router,
    private authStorage: AuthStorageService,
    private loginService: LoginService
  ) {}

  ngOnInit(): void {
    // Close the mobile drawer whenever navigation happens, mirroring the
    // behaviour of the storefront navbar's mobile menu.
    this.routerSub = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => (this.sidebarOpen = false));
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  closeSidebar(): void {
    this.sidebarOpen = false;
  }

  logout(): void {
    this.loginService.logout();
  }
}
