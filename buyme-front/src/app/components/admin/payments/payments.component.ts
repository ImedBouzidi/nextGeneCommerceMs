import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { PaymentService, PaymentDto } from '../../../services/payment.service';

@Component({
  selector: 'app-admin-payments',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payments.component.html',
  styleUrls: ['./payments.component.css']
})
export class PaymentsComponent implements OnInit {
  payments: PaymentDto[] = [];
  loading = true;
  failed = false;
  errorMessage = '';
  searchTerm = '';

  private sub?: Subscription;

  constructor(private paymentService: PaymentService) {}

  ngOnInit(): void {
    this.load();
  }

  load() {
    this.loading = true;
    this.failed = false;
    this.errorMessage = '';

    this.sub = this.paymentService.getAll().subscribe({
      next: (res) => {
        this.payments = Array.isArray(res) ? res : [];
        this.loading = false;
      },
      error: (err) => {
        this.payments = [];
        this.loading = false;
        this.failed = true;
        this.errorMessage =
          'The payments service did not respond. It may not be running, or the ' +
          'route may not be reachable through the gateway.';
        console.error('[AdminPayments] load failed', err);
      },
    });
  }

  get filtered(): PaymentDto[] {
    const term = this.searchTerm.trim().toLowerCase();
    if (!term) return this.payments;
    return this.payments.filter(p =>
      String(p.id ?? '').includes(term) ||
      String(p.orderId ?? '').includes(term) ||
      String(p.paymentMethod ?? '').toLowerCase().includes(term) ||
      String(p.paymentStatus ?? '').toLowerCase().includes(term) ||
      String(p.transactionId ?? '').toLowerCase().includes(term)
    );
  }

  get paidTotal(): number {
    return this.payments
      .filter(p => (p.paymentStatus || '').toUpperCase() === 'PAID')
      .reduce((sum, p) => sum + (Number(p.amount) || 0), 0);
  }

  get failedCount(): number {
    return this.payments.filter(p => (p.paymentStatus || '').toUpperCase() === 'FAILED').length;
  }

  tone(status?: string): string {
    switch ((status || '').toUpperCase()) {
      case 'PAID': return 'ok';
      case 'FAILED': return 'bad';
      case 'PENDING': return 'warn';
      default: return 'muted';
    }
  }
}
