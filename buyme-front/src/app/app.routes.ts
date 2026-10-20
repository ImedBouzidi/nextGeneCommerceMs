import { Routes } from '@angular/router';
import {LoginComponent} from './components/login/login.component';
import {RegisterComponent} from './components/register/register.component';
import {HomeComponent} from './components/client/home/home.component';
import {BestsellerComponent} from './components/client/bestseller/bestseller.component';
import {CartComponent} from './components/client/cart/cart.component';
import {CheckoutComponent} from './components/client/checkout/checkout.component';
import {ShopComponent} from './components/client/shop/shop.component';
import {SingleComponent} from './components/client/single/single.component';
import {ContactComponent} from './components/contact/contact.component';
import {NotfoundComponent} from './components/notfound/notfound.component';
import { ProfileComponent } from './components/client/profile/profile.component';
import { MyOrdersComponent } from './components/client/my-orders/my-orders.component';
import { ManageproductComponent } from './components/admin/manageproduct/manageproduct.component';
import { ManageordersComponent } from './components/admin/manageorders/manageorders.component';
import { AdminDashboardComponent } from './components/admin/dashboard/admin-dashboard.component';
import { AdminLayoutComponent } from './components/admin/admin-layout/admin-layout.component';
import { PaymentsComponent } from './components/admin/payments/payments.component';
import { AccountComponent } from './components/admin/account/account.component';
import { ManageClientsComponent } from './components/admin/manageclients/manageclients.component';
import { adminGuard } from './core/admin.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'client/home', component: HomeComponent },
  { path: 'client/my-orders', component: MyOrdersComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'client/bestseller', component: BestsellerComponent },
  { path: 'client/cart', component: CartComponent },
  { path: 'client/checkout', component: CheckoutComponent },
  { path: 'client/orders', component: MyOrdersComponent },
  { path: 'client/shop', component: ShopComponent },
  { path: 'client/single/:id', component: SingleComponent },
  { path: 'contact', component: ContactComponent },
  { path: 'notfound', component: NotfoundComponent },
  { path: 'client/profile', component: ProfileComponent },

  /*
   * Admin area.
   *
   * Nested under a layout component so the sidebar chrome is declared once instead
   * of being repeated per page, and so the client navbar can be suppressed here
   * (see app.component.ts). The guard is UX/defence-in-depth only — the real
   * authorisation lives in the services' @PreAuthorize checks.
   */
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', component: AdminDashboardComponent },
      { path: 'orders', component: ManageordersComponent },
      { path: 'products', component: ManageproductComponent },
      // Categories share the catalogue page, which owns both sections via a tab.
      { path: 'categories', component: ManageproductComponent },
      { path: 'clients', component: ManageClientsComponent },
      { path: 'payments', component: PaymentsComponent },
      { path: 'account', component: AccountComponent },

      // Old flat paths kept working so bookmarks and in-flight links do not 404.
      { path: 'dashboard', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'manageproduct', pathMatch: 'full', redirectTo: 'products' },
      { path: 'manageorders', pathMatch: 'full', redirectTo: 'orders' },
    ]
  },

  { path: '**', component: NotfoundComponent },
];
