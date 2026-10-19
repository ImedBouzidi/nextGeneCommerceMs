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

/**
 * e-Dinar credentials field.
 *
 * <p>Renders the e-Dinar provider's iframe for secure credential collection.
 * Card number and PIN are captured inside the provider's own frame and tokenised there.
 * This service only ever handles an opaque token, which keeps the deployment
 * in PCI SAQ-A scope: no PAN, PIN or sensitive data ever reaches our servers.
 *
 * <p>Integration note: Replace the iframe URL and tokenization logic with the
 * actual e-Dinar / Poste Tunisienne SDK when available.
 */
@Component({
  selector: 'app-edinar-field',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ck-edinar-mode" *ngIf="mode === 'iframe'">
      <div #edinarHost class="ck-edinar-element"></div>
      <p class="ck-hint">
        <i class="fa-solid fa-lock"></i>
        Card number and PIN are encrypted by e-Dinar and never touch our servers.
      </p>
    </div>

    <div class="ck-edinar-mode" *ngIf="mode === 'unavailable'">
      <div class="ck-sim-banner">
        <i class="fa-solid fa-triangle-exclamation"></i>
        <span>
          <strong>e-Dinar integration not configured</strong> — add the provider's
          iframe URL or SDK to <code>PaymentService</code> to enable.
        </span>
      </div>
      <p class="bm-muted">
        Contact your payment facilitator for the secure iframe integration details.
      </p>
    </div>
  `,
  styles: [`
    .ck-edinar-mode { display: block; }
    .ck-edinar-element {
      min-height: 200px;
      border: 1px solid var(--bm-border, #e5e7eb);
      border-radius: 10px;
      background: #fff;
      transition: border-color .15s ease, box-shadow .15s ease;
    }
    .ck-edinar-element:focus-within {
      border-color: var(--bm-accent, #6366f1);
      box-shadow: 0 0 0 3px rgba(99,102,241,.15);
    }
    .ck-hint { font-size: 12px; color: #6b7280; margin: 8px 0 0; }
    .ck-sim-banner {
      display: flex; gap: 10px; align-items: flex-start;
      padding: 10px 12px; margin-bottom: 14px; font-size: 12.5px; line-height: 1.45;
      color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: 10px;
    }
    .ck-sim-banner code {
      background: #fef3c7; padding: 2px 6px; border-radius: 4px; font-size: 11px;
    }
  `]
})
export class EdinarFieldComponent implements AfterViewInit, OnDestroy {

  @ViewChild('edinarHost') edinarHost?: ElementRef<HTMLDivElement>;

  /** Set by the parent when the customer switches to e-Dinar. */
  @Input() visible = false;

  /** Emits the token from the provider, or null if not yet available. */
  @Output() tokenChange = new EventEmitter<string | null>();

  /** Validation feedback for the submit button. */
  @Output() validityChange = new EventEmitter<boolean>();

  mode: 'iframe' | 'unavailable' = 'unavailable';

  private iframeElement: HTMLIFrameElement | null = null;
  private mounted = false;

  constructor(private paymentService: PaymentService) {}

  ngAfterViewInit(): void {
    this.mountIfNeeded();
  }

  ngOnDestroy(): void {
    this.destroyIframe();
  }

  /** Called by the parent when the e-Dinar panel becomes visible. */
  async mountIfNeeded(): Promise<void> {
    if (!this.visible) return;
    if (this.mounted) return;

    // Check if e-Dinar iframe integration is configured
    const iframeUrl = this.paymentService.edinarIframeUrl;
    if (iframeUrl && this.edinarHost?.nativeElement) {
      this.mode = 'iframe';
      await this.mountIframe(iframeUrl);
    } else {
      this.mode = 'unavailable';
      this.validityChange.emit(false);
    }
    this.mounted = true;
  }

  private async mountIframe(url: string): Promise<void> {
    const host = this.edinarHost!.nativeElement;
    host.innerHTML = '';

    this.iframeElement = document.createElement('iframe');
    this.iframeElement.src = url;
    this.iframeElement.style.width = '100%';
    this.iframeElement.style.height = '100%';
    this.iframeElement.style.minHeight = '200px';
    this.iframeElement.style.border = 'none';
    this.iframeElement.style.borderRadius = '8px';
    this.iframeElement.title = 'e-Dinar secure payment form';
    this.iframeElement.allow = 'payment *';

    host.appendChild(this.iframeElement);

    // Listen for token message from iframe (postMessage)
    window.addEventListener('message', this.handleIframeMessage.bind(this));

    // Wait for iframe to signal readiness
    this.iframeElement.onload = () => {
      this.validityChange.emit(false); // Will become true when token received
    };
  }

  private handleIframeMessage(event: MessageEvent): void {
    // Validate origin matches e-Dinar provider
    if (!this.isValidOrigin(event.origin)) return;

    if (event.data?.type === 'edinar_token' && event.data.token) {
      this.tokenChange.emit(event.data.token);
      this.validityChange.emit(true);
    } else if (event.data?.type === 'edinar_ready') {
      // Iframe loaded and ready
      this.validityChange.emit(false);
    } else if (event.data?.type === 'edinar_error') {
      console.error('e-Dinar iframe error:', event.data.message);
      this.validityChange.emit(false);
    }
  }

  private isValidOrigin(origin: string): boolean {
    // TODO: Replace with actual e-Dinar provider origin(s)
    const allowedOrigins = [
      'https://edinar.poste.tn',
      'https://sandbox.edinar.poste.tn',
      // Add production/sandbox origins from provider
    ];
    return allowedOrigins.includes(origin);
  }

  private destroyIframe(): void {
    if (this.iframeElement) {
      this.iframeElement.remove();
      this.iframeElement = null;
    }
    window.removeEventListener('message', this.handleIframeMessage.bind(this));
  }

  /** Called by parent to get the token for payment submission. */
  resolveToken(): string | null {
    // Token is emitted via tokenChange; parent should store it
    return null;
  }
}