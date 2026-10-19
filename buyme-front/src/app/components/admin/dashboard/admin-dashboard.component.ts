import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { OrderService, OrderItem } from '../../../services/order.service';
import { ProductService } from '../../../services/product.service';
import { CategoryService } from '../../../services/category.service';

interface Stat {
  label: string;
  value: string;
  icon: string;
  tone: 'accent' | 'green' | 'amber' | 'red' | 'violet';
  note: string;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  loading = true;
  /** Set when a source endpoint fails; the rest of the page still renders. */
  loadErrors: string[] = [];

  orders: OrderItem[] = [];
  products: any[] = [];
  categories: any[] = [];

  private subs: Subscription[] = [];

  constructor(
    private orderService: OrderService,
    private productService: ProductService,
    private categoryService: CategoryService
  ) {}

  ngOnInit(): void {
    this.loadOrders();
    this.loadProducts();
    this.loadCategories();
  }

  get totalOrders(): number { return this.orders.length; }

  get pendingOrders(): number {
    return this.orders.filter(o => (o.status || '').toUpperCase() === 'PENDING').length;
  }

  get approvedOrders(): number {
    return this.orders.filter(o => (o.status || '').toUpperCase() === 'APPROVED').length;
  }

  get deliveredOrders(): number {
    return this.orders.filter(o => (o.status || '').toUpperCase() === 'DELIVERED').length;
  }

  /** Orders whose payment never completed — the queue an admin should look at first. */
  get failedPayments(): number {
    return this.orders.filter(o => (o.paymentStatus || '').toUpperCase() === 'FAILED').length;
  }

  get outOfStock(): number {
    return this.products.filter(p => (p.quantity ?? 0) <= 0).length;
  }

  get lowStock(): number {
    return this.products.filter(p => (p.quantity ?? 0) > 0 && p.quantity <= 3).length;
  }

  /** Catalogue value at current listed prices. */
  get stockValue(): number {
    return this.products.reduce((sum, p) => sum + (Number(p.price) || 0) * (Number(p.quantity) || 0), 0);
  }

  get stats(): Stat[] {
    return [
      {
        label: 'Total orders', value: String(this.totalOrders),
        icon: 'fa-receipt', tone: 'accent',
        note: `${this.approvedOrders} approved · ${this.deliveredOrders} delivered`
      },
      {
        label: 'Awaiting approval', value: String(this.pendingOrders),
        icon: 'fa-hourglass-half', tone: 'amber',
        note: this.pendingOrders ? 'Needs your review' : 'Nothing waiting'
      },
      {
        label: 'Catalogue items', value: String(this.products.length),
        icon: 'fa-boxes-stacked', tone: 'violet',
        note: `Across ${this.categories.length} categories`
      },
      {
        label: 'Failed payments', value: String(this.failedPayments),
        icon: 'fa-circle-exclamation', tone: this.failedPayments ? 'red' : 'green',
        note: this.failedPayments ? 'Payment did not complete' : 'All payments healthy'
      },
      {
        label: 'Out of stock', value: String(this.outOfStock),
        icon: 'fa-triangle-exclamation', tone: this.outOfStock ? 'red' : 'green',
        note: this.outOfStock ? 'Listed but unbuyable' : 'Everything in stock'
      },
      {
        label: 'Low stock', value: String(this.lowStock),
        icon: 'fa-layer-group', tone: this.lowStock ? 'amber' : 'green',
        note: 'Three or fewer units left'
      }
    ];
  }

  /** Most recent orders, newest first. */
  get recentOrders(): OrderItem[] {
    return [...this.orders]
      .sort((a, b) => new Date(b.orderDate).getTime() - new Date(a.orderDate).getTime())
      .slice(0, 6);
  }

  private loadOrders() {
    const sub = this.orderService.getAllOrders().subscribe({
      next: (res) => {
        this.orders = Array.isArray(res) ? res : [];
        this.finish();
      },
      error: () => {
        this.orders = [];
        this.loadErrors.push('Orders');
        this.finish();
      },
    });
    this.subs.push(sub);
  }

  private loadProducts() {
    const sub = this.productService.getAll().subscribe({
      next: (res) => {
        this.products = Array.isArray(res) ? res : [];
        this.finish();
      },
      error: () => {
        this.products = [];
        this.loadErrors.push('Products');
        this.finish();
      },
    });
    this.subs.push(sub);
  }

  private loadCategories() {
    const sub = this.categoryService.getAll().subscribe({
      next: (res) => {
        this.categories = Array.isArray(res) ? res : [];
        this.finish();
      },
      error: () => {
        this.categories = [];
        this.loadErrors.push('Categories');
        this.finish();
      },
    });
    this.subs.push(sub);
  }

  /** Hide the loader once all three requests have settled, whatever the outcome. */
  private finish() {
    if (this.subs.length === 3) this.loading = false;
  }

  statusTone(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'APPROVED': return 'ok';
      case 'PENDING': return 'warn';
      case 'DELIVERED': return 'info';
      case 'CANCELLED': return 'bad';
      default: return 'muted';
    }
  }

  productName(productId: number): string {
    return this.products.find(p => p.id === productId)?.name ?? `Product #${productId}`;
  }
}
