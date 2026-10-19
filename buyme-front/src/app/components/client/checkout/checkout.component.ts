import { Component, AfterViewInit, OnDestroy, ViewChild, HostListener, ElementRef } from '@angular/core';
import { CommonModule, CurrencyPipe, AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CartService } from '../../../services/cart.service';
import { OrderService, CreateOrderRequest } from '../../../services/order.service';
import { PaymentService, PaymentDto, PaymentMethod, DOMESTIC_METHODS } from '../../../services/payment.service';
import { Router, RouterModule } from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { ModalService } from '../../../shared/modal.service';
import { LoginService } from '../../../services/login.service';
import { CardFieldComponent } from './card-field.component';
import { EdinarFieldComponent } from './edinar-field.component';

interface CartItem {
  productId: number;
  quantity: number;
  price: number;
  // add more if needed (name, image, etc.)
}

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, CurrencyPipe, AsyncPipe, RouterModule, CardFieldComponent, EdinarFieldComponent],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.css']
})
export class CheckoutComponent implements AfterViewInit, OnDestroy {
  mobile = '';
  paymentMethod: PaymentMethod = 'COD';
  processing = false;
  /** Progress of the currently awaited payment, shown while the saga resolves. */
  statusMessage = '';

  /**
   * Outcome requested from the simulated issuer, or null when Stripe decides.
   *
   * Only ever set in test mode; the server ignores it when a real acquirer is
   * configured.
   */
  simulatedOutcome: string | null = null;

  /** Whether the card form is complete enough to submit. */
  cardReady = false;
  paymentDetailsOpen = false;

  @ViewChild('cardField') cardField?: CardFieldComponent;
  @ViewChild('edinarField') edinarField?: EdinarFieldComponent;
  @ViewChild('paymentDialog') paymentDialog?: ElementRef<HTMLElement>;

  /** Whether the e-Dinar form has a valid token. */
  edinarReady = false;

  /** Token received from e-Dinar iframe. */
  edinarToken: string | null = null;

  items$!: Observable<CartItem[]>;  // ← Will be assigned in constructor

  /** How long to wait for the Kafka-driven saga before returning the current status. */
  private readonly PAYMENT_POLL_TIMEOUT_MS = 30_000;
  private readonly PAYMENT_POLL_INTERVAL_MS = 1_000;

  public cartService!: CartService;  // ← Exposed for template access via getters
  private orderService!: OrderService;
  private paymentService!: PaymentService;
  private router!: Router;

  constructor(
    cartService: CartService,
    orderService: OrderService,
    paymentService: PaymentService,
    router: Router,
    private modalService: ModalService,
    private loginService: LoginService
  ) {
    this.cartService = cartService;
    this.orderService = orderService;
    this.paymentService = paymentService;
    this.router = router;

    this.items$ = this.cartService.items$;  // ← Safe: assigned after injection
  }

  getSubtotal(items: CartItem[]): number {
    return items.reduce((sum, item) => sum + item.price * item.quantity, 0);
  }

  getTotalWithDiscount(items: CartItem[]): number {
    const subtotal = this.getSubtotal(items);
    const pct = this.getDiscountPercent();
    return Math.max(0, subtotal * (1 - (pct / 100)));
  }

  getDiscountPercent(): number {
    return this.cartService && typeof this.cartService.getDiscountPercent === 'function'
      ? this.cartService.getDiscountPercent()
      : 0;
  }

  async placeOrders() {
    if (this.processing) return;
    this.processing = true;

    try {
      const items: CartItem[] = await firstValueFrom(this.items$);
      if (!items || items.length === 0) {
        this.modalService.showAlert('Your cart is empty.', 'Cart');
        return;
      }

      const payments = [];

      const discountPercent = this.cartService.getDiscountPercent ? this.cartService.getDiscountPercent() : 0;
      for (const item of items) {
        const orderPayload: CreateOrderRequest = {
          productId: item.productId,
          qteOrdered: item.quantity,
          mobile: this.mobile,
          paymentMethod: this.paymentMethod
        };

        const order = await firstValueFrom(this.orderService.createOrder(orderPayload));

        if (this.paymentMethod === 'CARD') {
          const amountForItem = (item.price * item.quantity) * (1 - (discountPercent / 100));
          const cardPayload = {
            orderId: order.id,
            paymentMethod: this.paymentMethod,
            amount: Math.max(0, amountForItem),
            // Test mode only: which issuer decision to simulate. Never the card
            // data itself, and ignored by the server when a real acquirer is wired.
            simulatedOutcome: this.cardField?.resolveOutcome() ?? null
          };
          // PAN/CVV are deliberately NOT sent. The previous code posted them to
          // /api/payments/card, an endpoint that never existed (404), so card
          // payments silently resolved to null and checkout reported success.
          const pay = await firstValueFrom(
            this.paymentService.createPayment(cardPayload, `order-${order.id}`));
          // 202 only means "accepted" — the charge completes over Kafka.
          payments.push(await this.awaitPaymentOutcome(order.id!, pay));
        } else if (this.paymentMethod === 'EDINAR') {
          const amountForItem = (item.price * item.quantity) * (1 - (discountPercent / 100));
          const edinarPayload = {
            orderId: order.id,
            paymentMethod: this.paymentMethod,
            amount: Math.max(0, amountForItem),
            // Token from e-Dinar iframe (card number + PIN tokenized by provider)
            edinarToken: this.edinarToken
          };
          const pay = await firstValueFrom(
            this.paymentService.createPayment(edinarPayload, `order-${order.id}`));
          payments.push(await this.awaitPaymentOutcome(order.id!, pay));
        } else {
          const amountForItem = (item.price * item.quantity) * (1 - (discountPercent / 100));
          const codPayload = {
            orderId: order.id,
            paymentMethod: this.paymentMethod,
            amount: Math.max(0, amountForItem)
          };
          // Stable per order: a retry (reload, timeout, second tab) replays the same
          // key and gets the original payment back instead of a second charge.
          const pay = await firstValueFrom(
            this.paymentService.createPayment(codPayload, `order-${order.id}`));
          payments.push(await this.awaitPaymentOutcome(order.id!, pay));
        }
      }

      const failed = payments.filter(p => p?.paymentStatus === 'FAILED');
      if (failed.length > 0) {
        const reason = failed[0]?.failureReason || 'The payment could not be completed.';
        throw new Error(`${reason} You have not been charged twice — contact support if the order looks wrong.`);
      }

      this.cartService.clear();
      this.modalService.showAlert(
        this.paymentMethod === 'CARD' || this.paymentMethod === 'EDINAR'
          ? 'Order placed and paid successfully — thank you!'
          : 'Order placed successfully. Pay on delivery when you receive the items.',
        'Order placed',
        3000
      );
      this.router.navigate(['/client/home']);
    } catch (err: any) {
      console.error('Checkout failed', err);
      this.modalService.showAlert('Checkout failed: ' + (err.error?.message || err.message || 'Please try again.'), 'Error');
    } finally {
      this.processing = false;
    }
  }

  logout() {
    this.loginService.logout();
  }

  // ------------------------------------------------------------- Stripe mounting

  ngAfterViewInit(): void {
    // The card field only exists once *ngIf has rendered it, and only when the
    // customer picks CARD — mounting is triggered from the change handler.
  }

  ngOnDestroy(): void {
    // The child component destroys its own Stripe element.
  }

  /** Card payments use Stripe when a publishable key exists, otherwise the simulator. */
  get cardPaymentsAvailable(): boolean {
    return this.paymentService.cardPaymentsEnabled;
  }

  /**
   * True for instruments settled by a domestic facilitator.
   *
   * <p>These have no card form: the provider's own checkout collects the details, so
   * nothing sensitive is handled in this app at all.
   */
  isDomestic(method: PaymentMethod): boolean {
    return DOMESTIC_METHODS.includes(method);
  }

  /** Human-readable provider name for the redirect notice. */
  methodLabel(method: PaymentMethod): string {
    switch (method) {
      case 'EDINAR': return 'e-Dinar';
      case 'D17': return 'D17 / La Poste';
      case 'CLICTOPAY': return 'ClicToPay';
      case 'WALLET': return 'your mobile wallet provider';
      default: return 'the payment provider';
    }
  }

  /** (Re)mounts the card field when the customer switches to card. */
  onPaymentMethodChange(): void {
    this.simulatedOutcome = null;
    this.cardReady = this.paymentMethod !== 'CARD';
    this.edinarReady = this.paymentMethod !== 'EDINAR';
    // Popup no longer auto-opens; user must click the selected option to open it
    queueMicrotask(() => {
      this.cardField?.mountIfNeeded();
      this.edinarField?.mountIfNeeded();
    });
  }

  /** Handles click on a payment option label.
   * If the option is already selected, opens the payment details popup.
   * Otherwise, the radio button selection handles the change. */
  onPaymentOptionClick(method: PaymentMethod): void {
    if (this.paymentMethod === method && this.hasPaymentDetailsPopup) {
      // Already selected — open the popup
      this.paymentDetailsOpen = true;
      queueMicrotask(() => {
        this.paymentDialog?.nativeElement.focus();
      });
    }
    // If not selected, the radio button's ngModelChange will fire and call onPaymentMethodChange
  }

  get hasPaymentDetailsPopup(): boolean {
    return this.paymentMethod === 'CARD'
      || this.paymentMethod === 'EDINAR'
      || this.paymentMethod === 'CLICTOPAY'
      || this.paymentMethod === 'D17'
      || this.paymentMethod === 'WALLET';
  }

  get paymentDialogTitle(): string {
    if (this.paymentMethod === 'CARD') return 'Card details';
    if (this.paymentMethod === 'EDINAR') return 'e-Dinar credentials';
    if (this.paymentMethod === 'D17' || this.paymentMethod === 'WALLET') return `${this.methodLabel(this.paymentMethod)} secure checkout`;
    return `${this.methodLabel(this.paymentMethod)} secure checkout`;
  }

  closePaymentDetails(): void {
    this.paymentDetailsOpen = false;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.paymentDetailsOpen) this.closePaymentDetails();
  }

  /**
   * Waits for an asynchronous payment to reach a terminal state.
   *
   * The server answers POST /api/payments with 202/PENDING immediately and completes
   * the charge over Kafka (validate → authorise → reserve stock → capture). Reporting
   * success at the 202 would claim money moved when nothing has happened yet, so the
   * client follows the saga to its conclusion.
   *
   * For a card that needs 3-D Secure, the saga pauses after authorising and publishes
   * a `clientSecret`; the customer completes the challenge in Stripe's modal and the
   * capture follows. The challenge is attempted once — retrying a 3-D Secure prompt
   * in a loop would hammer the issuer and annoy the customer.
   *
   * On timeout the last known status is returned rather than an error: the payment is
   * still being processed server-side and the order page shows the real state, so
   * claiming failure here would be just as wrong as claiming success.
   */
  private async awaitPaymentOutcome(orderId: number, accepted: PaymentDto): Promise<PaymentDto> {
    if (this.paymentService.isTerminal(accepted?.paymentStatus)) return accepted;

    const deadline = Date.now() + this.PAYMENT_POLL_TIMEOUT_MS;
    let latest = accepted;
    let challengeAttempted = false;

    while (Date.now() < deadline) {
      await this.delay(this.PAYMENT_POLL_INTERVAL_MS);

      try {
        latest = await firstValueFrom(this.paymentService.getByOrder(orderId));
      } catch (err) {
        // A blip while polling must not fail the checkout; the next tick tries again.
        console.warn('Payment status poll failed, retrying', err);
        continue;
      }

      if (this.paymentService.isTerminal(latest?.paymentStatus)) return latest;

      // The issuer wants more from the customer (OTP / 3-D Secure).
      if (latest?.clientSecret && !challengeAttempted) {
        challengeAttempted = true;
        const failure = await this.paymentService.confirmAuthentication(
          latest.clientSecret,
          `${window.location.origin}/client/orders`
        );
        if (failure) {
          this.statusMessage = 'Your bank could not confirm the payment.';
          // Not thrown: the saga owns the final status, and a declined challenge ends
          // as a normal FAILED payment we will see on the next tick.
          console.warn('Card authentication failed:', failure);
        }
        continue;
      }

      this.statusMessage = latest?.paymentStatus === 'PROCESSING'
        ? 'Confirming your card and stock…'
        : 'Processing your payment…';
    }

    console.warn(`Payment for order ${orderId} still ${latest?.paymentStatus} after polling window`);
    return latest;
  }

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}