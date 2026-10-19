import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { OrderService, OrderItem } from '../../../services/order.service';
import { ProductService } from '../../../services/product.service';
import { CategoryService } from '../../../services/category.service';
import { CustomerSegmentationService, TopCustomer } from '../../../services/customer-segmentation.service';

interface Stat {
  label: string;
  value: string;
  icon: string;
  tone: 'accent' | 'green' | 'amber' | 'red' | 'violet';
  note: string;
}

type SortOption = 'engagement_score' | 'buying_score' | 'rating_score' | 'interaction_score' | 'frequency' | 'monetary' | 'recency' | 'avg_rating' | 'total_ratings' | 'total_interactions';

const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: 'engagement_score', label: 'Overall Engagement' },
  { value: 'buying_score', label: 'Buying Activity' },
  { value: 'rating_score', label: 'Ratings Given' },
  { value: 'interaction_score', label: 'Interactions' },
  { value: 'frequency', label: 'Order Frequency' },
  { value: 'monetary', label: 'Total Spend' },
  { value: 'recency', label: 'Recency (Recent First)' },
  { value: 'avg_rating', label: 'Average Rating' },
  { value: 'total_ratings', label: 'Total Ratings' },
  { value: 'total_interactions', label: 'Total Interactions' },
];

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  loading = true;
  loadErrors: string[] = [];

  orders: OrderItem[] = [];
  products: any[] = [];
  categories: any[] = [];

  // Customer segmentation
  topCustomers: TopCustomer[] = [];
  segmentationLoading = false;
  segmentationError: string | null = null;
  selectedSort: SortOption = 'engagement_score';
  sortOptions = SORT_OPTIONS;
  showTopCustomers = 10;

  private subs: Subscription[] = [];

  constructor(
    private orderService: OrderService,
    private productService: ProductService,
    private categoryService: CategoryService,
    private segmentationService: CustomerSegmentationService
  ) {}

  ngOnInit(): void {
    this.loadOrders();
    this.loadProducts();
    this.loadCategories();
    this.loadTopCustomers();
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

  get failedPayments(): number {
    return this.orders.filter(o => (o.paymentStatus || '').toUpperCase() === 'FAILED').length;
  }

  get outOfStock(): number {
    return this.products.filter(p => (p.quantity ?? 0) <= 0).length;
  }

  get lowStock(): number {
    return this.products.filter(p => (p.quantity ?? 0) > 0 && p.quantity <= 3).length;
  }

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

  private loadTopCustomers() {
    this.segmentationLoading = true;
    this.segmentationError = null;
    this.segmentationService.getTopCustomers(this.showTopCustomers, this.selectedSort).subscribe({
      next: (res) => {
        this.topCustomers = res.customers || [];
        this.segmentationLoading = false;
      },
      error: (err) => {
        this.segmentationError = 'Failed to load customer segmentation data';
        this.segmentationLoading = false;
      }
    });
  }

  onSortChange() {
    this.loadTopCustomers();
  }

  generateTestData() {
    this.segmentationLoading = true;
    this.segmentationError = null;
    this.segmentationService.generateData({
      n_customers: 50,
      max_orders_per_customer: 15,
      days_span: 365,
      products_count: 20
    }).subscribe({
      next: () => {
        this.loadTopCustomers();
      },
      error: (err) => {
        this.segmentationError = 'Failed to generate test data';
        this.segmentationLoading = false;
      }
    });
  }

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

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
  }

  formatNumber(value: number): string {
    return new Intl.NumberFormat('en-US').format(value);
  }

  getScoreClass(score: number): string {
    if (score >= 0.7) return 'score-high';
    if (score >= 0.4) return 'score-medium';
    return 'score-low';
  }
}
