import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  AfterViewInit
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaymentService } from '../../../services/payment.service';
import { StripeCardElement } from '@stripe/stripe-js';

/**
 * Card details field.
 *
 * <p>Renders one of two things:
 * <ul>
 *   <li><b>Stripe's iframe</b> when a publishable key is configured — the production
 *       path, where card data is captured and tokenised inside Stripe's own frame.</li>
 *   <li><b>A local test-card form</b> when there is no key, so the whole payment flow
 *       can be exercised offline. It uses Stripe's own published test card numbers so
 *       the numbers testers already know behave the same way here.</li>
 * </ul>
 *
 * <p>In simulator mode nothing typed here is ever sent anywhere. Only the resulting
 * <em>outcome</em> (approve / decline / challenge) is passed to the API, and the server
 * honours it exclusively when {@code payment.gateway=fake}.
 */
@Component({
  selector: 'app-card-field',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ck-card-mode" *ngIf="mode === 'stripe'">
      <div #stripeHost class="ck-stripe-element"></div>
      <p class="ck-hint">
        <i class="fa-solid fa-lock"></i>
        Card details are encrypted by Stripe and never touch our servers.
      </p>
    </div>

    <div class="ck-card-mode" *ngIf="mode === 'simulator'">
      <div class="ck-sim-banner">
        <i class="fa-solid fa-flask"></i>
        <span>
          <strong>Test mode</strong> — no Stripe key configured, so this form simulates
          the issuer. Nothing you type here is sent or stored.
        </span>
      </div>

      <div class="bm-field">
        <label class="bm-label" for="ck-card">Card number</label>
        <input id="ck-card" class="bm-input" type="text" inputmode="numeric"
               autocomplete="cc-number" placeholder="4242 4242 4242 4242"
               [(ngModel)]="cardNumber" (ngModelChange)="onCardNumberChange()">
      </div>

      <div class="ck-grid">
        <div class="bm-field">
          <label class="bm-label" for="ck-exp">Expiry</label>
          <input id="ck-exp" class="bm-input" type="text" inputmode="numeric"
                 autocomplete="cc-exp" placeholder="12/34" [(ngModel)]="expiry"
                 (ngModelChange)="onFormChange()">
        </div>
        <div class="bm-field">
          <label class="bm-label" for="ck-cvc">CVC</label>
          <input id="ck-cvc" class="bm-input" type="password" inputmode="numeric"
                 autocomplete="cc-csc" placeholder="123" [(ngModel)]="cvc"
                 (ngModelChange)="onFormChange()">
        </div>
      </div>

      <div class="ck-testcards">
        <span class="ck-testcards-label">Try a test card:</span>
        <button type="button" class="ck-testcard" *ngFor="let card of testCards"
                (click)="useTestCard(card)" [title]="card.description">
          {{ card.number }} <em>{{ card.label }}</em>
        </button>
      </div>

      <p class="ck-outcome" *ngIf="selectedOutcome">
        <i class="fa-solid fa-circle-info"></i> {{ selectedOutcome.description }}
      </p>
    </div>
  `,
  styles: [`
    .ck-card-mode { display: block; }
    .ck-stripe-element {
      min-height: 44px; padding: 12px 14px;
      border: 1px solid var(--bm-border, #e5e7eb); border-radius: 10px; background: #fff;
      transition: border-color .15s ease, box-shadow .15s ease;
    }
    .ck-stripe-element:focus-within {
      border-color: var(--bm-accent, #6366f1);
      box-shadow: 0 0 0 3px rgba(99,102,241,.15);
    }
    .ck-hint { font-size: 12px; color: #6b7280; margin: 8px 0 0; }
    .ck-sim-banner {
      display: flex; gap: 10px; align-items: flex-start;
      padding: 10px 12px; margin-bottom: 14px; font-size: 12.5px; line-height: 1.45;
      color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: 10px;
    }
    .ck-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px; }
    .ck-testcards { margin-top: 14px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .ck-testcards-label { font-size: 12px; color: #6b7280; }
    .ck-testcard {
      font: inherit; font-size: 11.5px; cursor: pointer;
      padding: 4px 8px; border-radius: 999px;
      border: 1px solid #d1d5db; background: #f9fafb; color: #374151;
    }
    .ck-testcard:hover { background: #eef2ff; border-color: #a5b4fc; }
    .ck-testcard em { font-style: normal; color: #6b7280; }
    .ck-outcome { font-size: 12px; color: #374151; margin: 10px 0 0; }
  `]
})
export class CardFieldComponent implements AfterViewInit, OnDestroy {

  @ViewChild('stripeHost') stripeHost?: ElementRef<HTMLDivElement>;

  /** Set by the parent when the customer switches to card. */
  @Input() visible = false;

  /**
   * Emits the outcome the simulated issuer should produce, or null when Stripe is
   * driving (where the real issuer decides and this is meaningless).
   */
  @Output() outcomeChange = new EventEmitter<string | null>();

  /** Validation feedback for the simulator form. */
  @Output() validityChange = new EventEmitter<boolean>();

  mode: 'stripe' | 'simulator' = 'simulator';

  cardNumber = '';
  expiry = '';
  cvc = '';
  selectedOutcome: TestCard | null = null;

  /** Stripe's documented test cards, so testers can reproduce real behaviours offline. */
  readonly testCards: TestCard[] = [
    { number: '4242 4242 4242 4242', label: 'succeeds', outcome: 'AUTHORIZED',
      description: 'Payment is approved.' },
    { number: '4000 0000 0000 9995', label: 'no funds', outcome: 'INSUFFICIENT_FUNDS',
      description: 'The issuer declines: insufficient funds.' },
    { number: '4000 0000 0000 0002', label: 'declined', outcome: 'DECLINED',
      description: 'The issuer declines the card.' },
    { number: '4000 0025 0000 3155', label: '3-D Secure', outcome: 'REQUIRES_ACTION',
      description: 'The bank asks for extra verification before approving.' },
    { number: '4000 0000 0000 0069', label: 'expired', outcome: 'EXPIRED_CARD',
      description: 'The card has expired.' },
    { number: '4000 0000 0000 0119', label: 'processing error', outcome: 'PROCESSING_ERROR',
      description: 'A processing error at the acquirer — the payment is retried, not failed.' }
  ];

  private stripeCardElement: StripeCardElement | null = null;
  private mounted = false;

  constructor(private paymentService: PaymentService) {}

  ngAfterViewInit(): void {
    this.mountIfNeeded();
  }

  ngOnDestroy(): void {
    this.stripeCardElement?.destroy();
    this.stripeCardElement = null;
  }

  /** Called by the parent when the card panel becomes visible. */
  async mountIfNeeded(): Promise<void> {
    if (!this.visible) return;
    if (this.mounted) return;

    this.mode = this.paymentService.cardPaymentsEnabled ? 'stripe' : 'simulator';
    if (this.mode === 'stripe' && this.stripeHost?.nativeElement) {
      this.stripeCardElement = await this.paymentService.mountCardElement(this.stripeHost.nativeElement);
      if (this.stripeCardElement) {
        this.stripeCardElement.on('change', event => this.validityChange.emit(event.complete));
      } else {
        this.validityChange.emit(false);
      }
    }
    this.mounted = true;
  }

  /** True when the form is complete enough to attempt a payment. */
  get ready(): boolean {
    if (this.mode === 'stripe') return true;      // Stripe validates inside its iframe
    return this.cardNumber.replace(/\s+/g, '').length >= 12 && this.expiry.length >= 4 && this.cvc.length >= 3;
  }

  useTestCard(card: TestCard): void {
    this.cardNumber = card.number;
    this.expiry = '12/34';
    this.cvc = '123';
    this.selectedOutcome = card;
    this.outcomeChange.emit(card.outcome);
    this.validityChange.emit(true);
  }

  onCardNumberChange(): void {
    const digits = this.cardNumber.replace(/\D/g, '');
    const match = this.testCards.find(c => c.number.replace(/\s+/g, '') === digits);
    this.selectedOutcome = match || null;
    this.outcomeChange.emit(match ? match.outcome : null);
    this.validityChange.emit(this.ready);
  }

  onFormChange(): void {
    this.validityChange.emit(this.ready);
  }

  /**
   * The outcome to send with the payment, or null for a normal Stripe flow.
   *
   * <p>Unrecognised card numbers yield AUTHORIZED so a tester who types an arbitrary
   * number still gets a successful run rather than a confusing decline.
   */
  resolveOutcome(): string | null {
    if (this.mode === 'stripe') return null;
    return this.selectedOutcome ? this.selectedOutcome.outcome : 'AUTHORIZED';
  }
}

export interface TestCard {
  number: string;
  label: string;
  outcome: string;
  description: string;
}