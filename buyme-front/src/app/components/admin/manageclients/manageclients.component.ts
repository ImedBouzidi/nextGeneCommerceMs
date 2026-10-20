import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { CustomerSegmentationService, TopCustomer, TopCustomersResponse, CustomerDetail, Promotion, RunSegmentationRequest } from '../../../services/customer-segmentation.service';
import { AuthStorageService } from '../../../core/auth-storage.service';

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
  selector: 'app-manageclients',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './manageclients.component.html',
  styleUrls: ['./manageclients.component.css']
})
export class ManageClientsComponent implements OnInit, OnDestroy {
  // Top customers list
  topCustomers: TopCustomer[] = [];
  totalCustomers = 0;
  loading = false;
  error: string | null = null;
  selectedSort: SortOption = 'engagement_score';
  sortOptions = SORT_OPTIONS;
  showTopCustomers = 20;

  // Selected client detail
  selectedCustomer: TopCustomer | null = null;
  customerDetail: CustomerDetail | null = null;
  detailLoading = false;
  detailError: string | null = null;

  // Segmentation / Promotions
  promotions: Promotion[] = [];
  segmentationLoading = false;
  segmentationError: string | null = null;
  showSegmentationModal = false;
  segmentationRequest: RunSegmentationRequest = { n_clusters: 3, send: false };

  // Coupon assignment
  assigningCoupon = false;
  couponCustomerId: string | null = null;
  couponDiscountPct = 10;
  couponNote = '';

  private subs: Subscription[] = [];

  constructor(
    private segmentationService: CustomerSegmentationService,
    private authStorage: AuthStorageService
  ) {}

  ngOnInit(): void {
    this.loadTopCustomers();
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  // ── Load top customers ──
  loadTopCustomers(): void {
    this.loading = true;
    this.error = null;
    this.subs.push(
      this.segmentationService.getTopCustomers(this.showTopCustomers, this.selectedSort).subscribe({
        next: (res: TopCustomersResponse) => {
          this.topCustomers = res.customers || [];
          this.totalCustomers = res.total_customers || 0;
          this.loading = false;
        },
        error: (err) => {
          this.error = 'Failed to load customer data';
          this.loading = false;
          console.error('[ManageClients] loadTopCustomers failed', err);
        }
      })
    );
  }

  onSortChange(): void {
    this.loadTopCustomers();
  }

  onLimitChange(): void {
    this.loadTopCustomers();
  }

  // ── View client detail ──
  viewCustomerDetail(customer: TopCustomer): void {
    this.selectedCustomer = customer;
    this.detailLoading = true;
    this.detailError = null;
    this.customerDetail = null;

    this.subs.push(
      this.segmentationService.getCustomerDetail(customer.customer_id).subscribe({
        next: (detail: CustomerDetail) => {
          this.customerDetail = detail;
          this.detailLoading = false;
        },
        error: (err) => {
          this.detailError = 'Failed to load customer details';
          this.detailLoading = false;
          console.error('[ManageClients] getCustomerDetail failed', err);
        }
      })
    );
  }

  closeDetail(): void {
    this.selectedCustomer = null;
    this.customerDetail = null;
  }

  // ── Run segmentation ──
  openSegmentationModal(): void {
    this.showSegmentationModal = true;
    this.segmentationError = null;
    this.segmentationRequest = { n_clusters: 3, send: false };
  }

  closeSegmentationModal(): void {
    this.showSegmentationModal = false;
  }

  runSegmentation(): void {
    this.segmentationLoading = true;
    this.segmentationError = null;

    // Attach userId if available (for targeting specific user)
    const token = this.authStorage.getValidToken();
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        if (payload.sub) this.segmentationRequest.userId = payload.sub;
      } catch (e) { /* ignore */ }
    }

    this.subs.push(
      this.segmentationService.runSegmentation(this.segmentationRequest).subscribe({
        next: (res) => {
          this.promotions = res.promotions || [];
          this.segmentationLoading = false;
          this.showSegmentationModal = false;
          // Refresh top customers to see updated scores
          this.loadTopCustomers();
        },
        error: (err) => {
          this.segmentationError = 'Failed to run segmentation';
          this.segmentationLoading = false;
          console.error('[ManageClients] runSegmentation failed', err);
        }
      })
    );
  }

  // ── Assign manual coupon ──
  openCouponModal(customer: TopCustomer): void {
    this.couponCustomerId = customer.customer_id;
    this.couponDiscountPct = 10;
    this.couponNote = `Manual discount for ${customer.customer_id}`;
  }

  closeCouponModal(): void {
    this.couponCustomerId = null;
    this.couponDiscountPct = 10;
    this.couponNote = '';
  }

  assignCoupon(): void {
    if (!this.couponCustomerId) return;

    this.assigningCoupon = true;
    // In a real app, this would call a backend endpoint to create a coupon
    // For now, we simulate by adding to promotions list
    const newPromotion: Promotion = {
      customer_id: this.couponCustomerId,
      segment: 'MANUAL',
      discount_pct: this.couponDiscountPct,
      discount_percent: this.couponDiscountPct,
      loyalty_score: 0,
      display_name: this.couponNote
    };

    // Simulate API call
    setTimeout(() => {
      this.promotions = [newPromotion, ...this.promotions];
      this.assigningCoupon = false;
      this.closeCouponModal();
      // In production, call a service like: this.couponService.createCoupon(...)
    }, 500);
  }

  // ── Helpers ──
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

  getSegmentClass(segment: string): string {
    switch (segment?.toUpperCase()) {
      case 'CHAMPIONS': return 'seg-champions';
      case 'LOYAL': return 'seg-loyal';
      case 'POTENTIAL': return 'seg-potential';
      case 'NEW': return 'seg-new';
      case 'AT_RISK': return 'seg-at-risk';
      case 'HIBERNATING': return 'seg-hibernating';
      case 'MANUAL': return 'seg-manual';
      default: return '';
    }
  }

  getSegmentLabel(customer: TopCustomer): string {
    // Determine segment based on RFM scores (simplified heuristic)
    const { recency, frequency, monetary } = customer;
    if (recency <= 30 && frequency >= 5 && monetary >= 500) return 'CHAMPIONS';
    if (recency <= 60 && frequency >= 3) return 'LOYAL';
    if (recency <= 90 && frequency >= 2) return 'POTENTIAL';
    if (recency <= 30) return 'NEW';
    if (recency <= 180) return 'AT_RISK';
    return 'HIBERNATING';
  }

  getStatusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'APPROVED': return 'ok';
      case 'PENDING': return 'warn';
      case 'DELIVERED': return 'info';
      case 'CANCELLED': return 'bad';
      default: return 'muted';
    }
  }

  getCustomerById(customerId: string): TopCustomer | undefined {
    return this.topCustomers.find(c => c.customer_id === customerId);
  }

  viewCustomerDetailByPromo(promo: Promotion): void {
    const customer = this.topCustomers.find(c => c.customer_id === promo.customer_id);
    if (customer) {
      this.viewCustomerDetail(customer);
    }
  }
}