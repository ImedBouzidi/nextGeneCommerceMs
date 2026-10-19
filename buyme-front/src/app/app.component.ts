import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { NavbarComponent } from './components/nav-bar/nav-bar.component';
import { SharedModalComponent } from './shared/shared-modal.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, SharedModalComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'NextGenTech';

  /**
   * The admin area supplies its own topbar and sidebar, so the storefront navbar is
   * suppressed there — otherwise the two shells stack and the admin rail loses its
   * full-height layout.
   */
  isAdminRoute = false;

  private routerSub?: Subscription;

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.isAdminRoute = this.checkAdmin(this.router.url);
    this.routerSub = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        this.isAdminRoute = this.checkAdmin(event.urlAfterRedirects);
      });
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
  }

  /** Matches /admin and /admin/... but not /administration or a client path. */
  private checkAdmin(url: string): boolean {
    return /^\/admin(\/|$)/.test(url.split('?')[0]);
  }
}
