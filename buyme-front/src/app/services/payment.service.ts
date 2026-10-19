import { Injectable, Inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { loadStripe, Stripe, StripeCardElement } from '@stripe/stripe-js';
import { APP_CONFIG } from '../../main';

/**
 * Instruments the customer can pay with.
 *
 * <p>The Tunisian options are first-class because they are what customers actually
 * hold here: Poste Tunisienne's e-Dinar, the D17 wallet, ClicToPay bank cards. They
 * are processed by a domestic facilitator, never by an international acquirer —
 * the server routes on this value.
 */
export type PaymentMethod =
  'COD'
  | 'CARD'
  | 'EDINAR'
  | 'D17'
  | 'CLICTOPAY'
  | 'WALLET';

/** Methods settled by a local Tunisian facilitator rather than a card acquirer. */
export const DOMESTIC_METHODS: PaymentMethod[] = ['EDINAR', 'D17', 'CLICTOPAY', 'WALLET'];

export interface PaymentDto {
  id?: number;
  orderId: number;
  amount?: number;
  currency?: string;
  paymentStatus?: string;
  paymentMethod?: PaymentMethod;
  transactionId?: string;
  idempotencyKey?: string;
  authorizationId?: string;
  /**
   * Test-mode only: which issuer decision the fake acquirer should simulate.
   *
   * Never sent when a Stripe publishable key is configured, and ignored by the server
   * when `payment.gateway=stripe` — the real acquirer decides.
   */
  simulatedOutcome?: string | null;
  /**
   * Returned to the owning client only, and only while the issuer still needs the
   * customer to authenticate (3-D Secure / OTP). Passed straight to Stripe's SDK to
   * finish the payment — never sent to our own API.
   */
  clientSecret?: string;
  gateway?: string;
  failureReason?: string;
  failureCode?: string;
}

/**
 * Client-side half of the card integration.
 *
 * Card data is captured by Stripe's own iframe and tokenised there. This service only
 * ever handles an opaque token or a client secret, which is what keeps the deployment
 * in PCI SAQ-A scope: no PAN, CVV or expiry ever reaches our servers, our logs or our
 * database.
 */
@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly apiUrl: string;
  private readonly publishableKey: string;
  private stripePromise: Promise<Stripe | null> | null = null;

  constructor(private readonly http: HttpClient, @Inject(APP_CONFIG) private readonly config: any) {
    const base = (this.config?.apiUrl || '').replace(/\/$/, '');
    this.apiUrl = base.replace(/\/api$/, '') + '/api/payments';
    this.publishableKey = this.config?.stripePublishableKey || '';
  }

  /** True when a publishable key is configured, i.e. card payments can be offered at all. */
  get cardPaymentsEnabled(): boolean {
    return this.publishableKey.startsWith('pk_');
  }

  /**
   * Creates a payment, at most once per order.
   *
   * Resolves as soon as the server has *accepted* the request (HTTP 202, status
   * PENDING): the saga runs over Kafka, so callers must then poll {@link getByOrder}
   * for the outcome.
   *
   * `idempotencyKey` must stay the same across retries of the *same* logical payment
   * (double click, page reload, network retry) and differ for a new one.
   *
   * Errors are deliberately NOT swallowed — a swallowed failure used to be reported to
   * the customer as "paid successfully".
   */
  createPayment(dto: Partial<PaymentDto>, idempotencyKey: string): Observable<PaymentDto> {
    return this.http.post<PaymentDto>(this.apiUrl, dto, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey })
    });
  }

  /** Polls the payment for an order while the saga runs. */
  getByOrder(orderId: number): Observable<PaymentDto> {
    return this.http.get<PaymentDto>(`${this.apiUrl}/by-order/${orderId}`);
  }

  /** Terminal statuses — no further events will arrive for this payment. */
  isTerminal(status?: string): boolean {
    return status === 'SUCCESS' || status === 'FAILED' || status === 'REFUNDED';
  }

  // ------------------------------------------------------------ Stripe Elements

  /** Loads the Stripe SDK once and reuses the promise. */
  private stripe(): Promise<Stripe | null> {
    if (!this.stripePromise) {
      this.stripePromise = this.cardPaymentsEnabled
        ? loadStripe(this.publishableKey)
        : Promise.resolve(null);
    }
    return this.stripePromise;
  }

  /**
   * Mounts Stripe's card field into {@code hostElement}.
   *
   * <p>The returned element is an iframe Stripe owns. It is what replaces the raw
   * card-number / expiry / CVV inputs that used to live in the checkout template:
   * those values would have to be submitted somewhere, and "somewhere" is exactly the
   * place that creates PCI scope.
   *
   * @returns the mounted element, or null when Stripe is not configured.
   */
  async mountCardElement(hostElement: HTMLElement): Promise<StripeCardElement | null> {
    const stripe = await this.stripe();
    if (!stripe) return null;

    const elements = stripe.elements({
      // Mirrors the server's styling so the iframe does not look bolted on.
      fonts: [{ cssSrc: 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500&display=swap' }]
    });
    const card = elements.create('card', {
      hidePostalCode: false,
      style: {
        base: {
          color: '#1f2937',
          fontFamily: 'Inter, system-ui, sans-serif',
          fontSize: '15px',
          '::placeholder': { color: '#9ca3af' }
        },
        invalid: { color: '#dc2626', iconColor: '#dc2626' }
      }
    });
    card.mount(hostElement);
    return card;
  }

  /**
   * Completes a payment that needs customer authentication (3-D Secure, OTP).
   *
   * <p>`redirect: 'if_required'` keeps the challenge in a modal instead of navigating
   * away, which suits a single-page checkout. Cards that authorise immediately never
   * produce a client secret, so this is not called for them — the saga finishes on its
   * own and the webhook is the authoritative signal.
   *
   * @returns null on success, or a human-readable message on failure.
   */
  async confirmAuthentication(
    clientSecret: string,
    returnUrl: string
  ): Promise<string | null> {
    const stripe = await this.stripe();
    if (!stripe) return 'Card authentication is not configured';

    const result = await stripe.confirmPayment({
      clientSecret,
      confirmParams: { return_url: returnUrl },
      redirect: 'if_required'
    });

    if (result.error) {
      return result.error.message || 'Your card could not be authenticated';
    }
    return null;
  }

  getPayment(id: number): Observable<PaymentDto> {
    return this.http.get<PaymentDto>(`${this.apiUrl}/${id}`).pipe(
      catchError(() => of(null as any))
    );
  }

  /** The caller's own payments. */
  getMine(): Observable<PaymentDto[]> {
    return this.http.get<PaymentDto[]>(`${this.apiUrl}/mine`);
  }

  /** Admin: full transaction list, without swallowing errors. */
  getAll(): Observable<PaymentDto[]> {
    return this.http.get<PaymentDto[]>(this.apiUrl);
  }

  // ------------------------------------------------------------ e-Dinar iframe

  /** e-Dinar provider iframe URL (configured via APP_CONFIG). */
  get edinarIframeUrl(): string {
    return this.config?.edinarIframeUrl || '';
  }

  /** Whether e-Dinar iframe integration is configured. */
  get edinarEnabled(): boolean {
    return this.edinarIframeUrl.startsWith('http');
  }
}