import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { timeout, catchError } from 'rxjs/operators';
import { throwError, Subscription } from 'rxjs';
import { OrderService, OrderItem } from '../../../services/order.service';
import { ProductService } from '../../../services/product.service';
import { PaymentService } from '../../../services/payment.service';
import { UserService, UserProfile } from '../../../services/user.service';
import { Router } from '@angular/router';
import { ModalService } from '../../../shared/modal.service';
import { LoginService } from '../../../services/login.service';

type AlertType = 'success' | 'error' | 'info';

@Component({
  selector: 'app-manageorders',
  standalone: true,
  templateUrl: './manageorders.component.html',
  styleUrls: ['./manageorders.component.css'],
  imports: [CommonModule, FormsModule, RouterModule]
})
export class ManageordersComponent implements OnInit, OnDestroy {
  autoRefreshPending = false;
  operationInProgress = false;
  orders: OrderItem[] = [];

  // user cache: userId -> UserProfile
  userMap: Record<number, UserProfile | undefined> = {};
  // product cache: productId -> productName
  productMap: Record<number, string | undefined> = {};
  // pagination
  pageSize = 10;
  currentPage = 1;

  searchTerm = '';
  ordersLoading = false;
  approvingOrder = false;

  alertMessage = '';
  alertType: AlertType = 'info';
  showAlert = false;
  private alertTimeout?: ReturnType<typeof setTimeout>;
  private autoRefreshTimeout?: ReturnType<typeof setTimeout>;
  private subscriptions: Subscription[] = [];

  constructor(
    private orderService: OrderService,
    private router: Router,
    private paymentService: PaymentService,
    private userService: UserService
    , private productService: ProductService,
    private modalService: ModalService,
    private loginService: LoginService
  ) {}

  ngOnInit(): void {
    this.loadOrders();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    if (this.alertTimeout) clearTimeout(this.alertTimeout);
    if (this.autoRefreshTimeout) clearTimeout(this.autoRefreshTimeout);
  }

  displayAlert(message: string, type: AlertType = 'info') {
    this.alertMessage = message;
    this.alertType = type;
    this.showAlert = true;

    // Auto-close success/info alerts after 5 seconds (errors stay until manual close)
    if (type !== 'error') {
      this.alertTimeout = setTimeout(() => {
        this.showAlert = false;
      }, 5000);
    }
  }

  closeAlert() {
    this.showAlert = false;
    if (this.alertTimeout) {
      clearTimeout(this.alertTimeout);
      this.alertTimeout = undefined;
    }
  }

  loadOrders() {
    this.ordersLoading = true;
    const sub = this.orderService.getAllOrders()
      .pipe(
        timeout(10000),
        catchError(error => {
          this.displayAlert('Failed to load orders. Please try again.', 'error');
          return throwError(() => error);
        })
      )
      .subscribe({
        next: (orders) => {
          this.orders = orders || [];
          this.ordersLoading = false;
          this.loadUsersForOrders();
          this.loadProductsForOrders();
        },
        error: () => {
          this.ordersLoading = false;
        }
      });
    this.subscriptions.push(sub);
  }

  refreshAll() {
    this.autoRefreshPending = true;
    this.loadOrders();
    setTimeout(() => this.autoRefreshPending = false, 1000);
  }

  filteredOrders(): OrderItem[] {
    if (!this.searchTerm.trim()) {
      return this.orders;
    }
    const term = this.searchTerm.toLowerCase();
    return this.orders.filter(order =>
      order.id.toString().includes(term) ||
      order.status.toLowerCase().includes(term) ||
      order.paymentStatus.toLowerCase().includes(term) ||
      order.productId.toString().includes(term) ||
      order.userId.toString().includes(term)
    );
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredOrders().length / this.pageSize));
  }

  get pagedOrders(): OrderItem[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredOrders().slice(start, start + this.pageSize);
  }

  pagesArray(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  changePage(page: number) {
    if (page < 1 || page > this.totalPages) { return; }
    this.currentPage = page;
    try { window.scrollTo({ top: 0, behavior: 'smooth' }); } catch (_) {}
  }

  async approveOrder(order: OrderItem) {
    const id = this.getOrderId(order);
    if (!id) return;
    if (order.status === 'APPROVED' || order.status === 'CANCELLED') return;

    // Only require explicit force when payment FAILED. If payment is PENDING, allow approval.
    const needForce = order.paymentStatus === 'FAILED';
    if (needForce) {
      // ask for confirmation via modal
      const ok = await this.modalService.showConfirm(`Order #${order.id} payment status is '${order.paymentStatus}'. Force-approve?`);
      if (!ok) return;
    }

    this.approvingOrder = true;
    this.operationInProgress = true;

    const sub = this.orderService.approveOrder(id, needForce)
      .pipe(
        timeout(10000),
        catchError(error => {
          this.displayAlert('Failed to approve order. Please try again.', 'error');
          return throwError(() => error);
        })
      )
      .subscribe({
        next: (updatedOrder) => {
          // Update the order in the list
          const index = this.orders.findIndex(o => this.getOrderId(o) === id);
          if (index !== -1) {
            this.orders[index] = updatedOrder;
          }
          this.displayAlert('Order approved successfully!', 'success');
          this.approvingOrder = false;
          this.operationInProgress = false;
        },
        error: () => {
          this.approvingOrder = false;
          this.operationInProgress = false;
        }
      });
    this.subscriptions.push(sub);
  }

async markDelivered(order: OrderItem) {
  const id = this.getOrderId(order);
  if (!id) return;
  const ok = await this.modalService.showConfirm(`Mark order #${id} as delivered?`);
  if (!ok) return;

  this.operationInProgress = true;

  const sub = this.orderService.markAsDelivered(id).subscribe({
    next: (updatedOrder) => {
      // Update local list status
      const idx = this.orders.findIndex(o => this.getOrderId(o) === id);
      if (idx !== -1) {
        this.orders[idx].status = 'DELIVERED';
        // normalize paymentStatus for UI if needed
        if (order.paymentMethod === 'COD') this.orders[idx].paymentStatus = 'PAID_COD';
      }

      // Decrease product stock by ordered quantity (for both CARD and COD)
      try {
        const prodSub = this.productService.getById(order.productId).subscribe({
          next: (prod) => {
            try {
              const currentQty = Number(prod?.quantity) || 0;
              const newQty = Math.max(0, currentQty - (order.qteOrdered || 0));
              const updatePayload = { ...prod, quantity: newQty };
              this.productService.update(prod.id, updatePayload).subscribe({
                next: () => {
                  // If order was paid by CARD, ensure payment status recorded as PAID
                  if (order.paymentMethod === 'CARD') {
                    try {
                      this.orderService.markPaymentPaid(id).subscribe({
                        next: () => {
                          this.displayAlert('Order marked as delivered, payment recorded and stock updated.', 'success');
                        },
                        error: () => {
                          this.displayAlert('Order delivered and stock updated; failed to mark payment as paid.', 'info');
                        }
                      });
                    } catch (e) {
                      this.displayAlert('Order delivered and stock updated; payment mark skipped.', 'info');
                    }
                  } else {
                    this.displayAlert('Order marked as delivered and stock updated.', 'success');
                  }
                },
                error: () => {
                  this.displayAlert('Order marked as delivered but failed to update stock.', 'info');
                }
              });
            } catch (e) {
              this.displayAlert('Order marked as delivered (stock update skipped).', 'info');
            }
          },
          error: () => {
            this.displayAlert('Order marked as delivered but failed to fetch product for stock update.', 'info');
          }
        });
        this.subscriptions.push(prodSub);
      } catch (e) {
        // ignore product update failures
      }

      this.operationInProgress = false;
    },
    error: (err) => {
      this.displayAlert('Failed to mark order as delivered: ' + (err.error?.message || 'Unknown error'), 'error');
      this.operationInProgress = false;
    }
  });
  this.subscriptions.push(sub);
}

// Custom display for Payment Status column
getPaymentInfo(order: OrderItem): string {
  if (order.paymentMethod === 'CARD') {
    return 'Paid by Card';
  }
  if (order.paymentMethod === 'COD') {
    return order.paymentStatus === 'PAID_COD' || order.paymentStatus.includes('PAID') ? 'Delivered' : 'Pending Delivery';
  }
  return 'Unknown';
}

  async declineOrder(order: OrderItem) {
    if (order.status === 'CANCELLED') return;
    const ok = await this.modalService.showConfirm(`Decline order #${order.id}? This will cancel the order.`);
    if (!ok) return;
    this.operationInProgress = true;
    const sub = this.orderService.declineOrder(order.id).subscribe({
      next: () => {
        // mark locally
        const idx = this.orders.findIndex(o => o.id === order.id);
        if (idx !== -1) {
          this.orders[idx].status = 'CANCELLED';
        }
        this.displayAlert('Order declined (cancelled).', 'info');
        this.operationInProgress = false;
      },
      error: () => {
        this.operationInProgress = false;
      }
    });
    this.subscriptions.push(sub);
  }

  async deleteOrder(order: OrderItem) {
    const ok = await this.modalService.showConfirm(`Permanently delete order #${order.id}?`);
    if (!ok) return;
    this.operationInProgress = true;
    const sub = this.orderService.deleteOrder(order.id).subscribe({
      next: () => {
        this.orders = this.orders.filter(o => o.id !== order.id);
        this.displayAlert('Order deleted.', 'success');
        this.operationInProgress = false;
      },
      error: () => {
        this.operationInProgress = false;
      }
    });
    this.subscriptions.push(sub);
  }

  // Fetch user profiles for all unique userIds in current orders
  private loadUsersForOrders() {
    const ids = Array.from(new Set(this.orders.map(o => o.userId)));
    ids.forEach(id => {
      if (this.userMap[id]) return; // already loaded
      const sub = this.userService.getUserById(id).subscribe({
        next: (user) => { if (user) this.userMap[id] = user; },
        error: () => { this.userMap[id] = undefined; }
      });
      this.subscriptions.push(sub);
    });
  }

  // Fetch product names for all unique productIds in current orders
  private loadProductsForOrders() {
    const ids = Array.from(new Set(this.orders.map(o => o.productId)));
    ids.forEach(id => {
      if (this.productMap[id] !== undefined) return; // already requested or loaded
      const sub = this.productService.getById(id).subscribe({
        next: (prod) => { if (prod && prod.name) this.productMap[id] = prod.name; else this.productMap[id] = String(id); },
        error: () => { this.productMap[id] = String(id); }
      });
      this.subscriptions.push(sub);
    });
  }

  getProductName(productId: number): string {
    const name = this.productMap[productId];
    return name != null ? name : String(productId);
  }

  getStatusBadgeClass(status: string): string {
    switch (status.toUpperCase()) {
      case 'PENDING': return 'bg-warning text-dark';
      case 'CONFIRMED': return 'bg-info text-white';
      case 'APPROVED': return 'bg-success text-white';
      case 'SHIPPED': return 'bg-primary text-white';
      case 'CANCELLED': return 'bg-danger text-white';
      default: return 'bg-secondary text-white';
    }
  }

// Badge classes for Payment Status
getPaymentStatusBadgeClass(order: OrderItem): string {
  const info = this.getPaymentInfo(order);
  if (info === 'Paid by Card' || info === 'Delivered') {
    return 'bg-success text-white';  // Green
  }
  if (info === 'Pending Delivery') {
    return 'bg-warning text-dark';   // Orange/Yellow
  }
  return 'bg-secondary text-white';
}

  get pendingApprovalCount(): number {
    return this.orders.filter(o => o.status === 'PENDING' && o.paymentStatus === 'PAID').length;
  }

  get approvedCount(): number {
    return this.orders.filter(o => o.status === 'APPROVED').length;
  }

  logout() {
    this.loginService.logout();
  }
  // Template helper: safely extract an order id from different shapes
  getOrderId(orderOrId: number | OrderItem | any): number | null {
    if (!orderOrId) return null;
    if (typeof orderOrId === 'number') return orderOrId;
    if (orderOrId.id != null) return orderOrId.id;
    if (orderOrId.orderId != null) return orderOrId.orderId;
    if (orderOrId.orderID != null) return orderOrId.orderID;
    return null;
  }
}