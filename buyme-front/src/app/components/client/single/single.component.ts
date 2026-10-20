import { Component, OnInit, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../../services/product.service';
import { CartService } from '../../../services/cart.service';
import { ReviewService } from '../../../services/review.service';
import { ModalService } from '../../../shared/modal.service';
import { LoginService } from '../../../services/login.service';
import { AuthStorageService } from '../../../core/auth-storage.service';
import { APP_CONFIG } from '../../../../main';
import { productImageBaseUrl, productImageUrl, useImageFallback } from '../../../shared/product-image-url';

@Component({
  selector: 'app-single',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './single.component.html',
  styleUrls: ['./single.component.css']
})
export class SingleComponent implements OnInit {
  product: any = null;
  loading = true;
  error: string | null = null;
  quantity = 1;
  reviews: any[] = [];
  selectedRating = 0;
  isAuthenticated = false;
  discountPercent = 0;
  /** Resolved, display-ready image list for the gallery switcher */
  imageUrls: string[] = [];
  /** Currently selected gallery image (falls back to the first image) */
  activeImage: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private productService: ProductService,
    private cartService: CartService,
    private router: Router,
    private reviewService: ReviewService,
    private modalService: ModalService,
    private loginService: LoginService,
    private authStorage: AuthStorageService,
    @Inject(APP_CONFIG) private config: any
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id || isNaN(id)) {
      this.error = 'Invalid product ID';
      this.loading = false;
      return;
    }

    this.productService.getById(id).subscribe({
      next: (p) => {
        this.product = p;
        this.buildGallery(p);
        this.loading = false;

        // subscribe to current discount percent from cart service
        try {
          this.cartService.discount$.subscribe(pct => this.discountPercent = pct || 0);
        } catch (e) {
          this.discountPercent = 0;
        }

        // set authentication flag
        this.isAuthenticated = this.authStorage.isAuthenticated();

        // load existing reviews for product
        this.loadReviews();

        // Initialize carousel after images are loaded
        setTimeout(() => {
          this.initializeCarousel();
        }, 100);
      },
      error: (err) => {
        this.error = err.error?.message || err.message || 'Unable to load product.';
        this.loading = false;
      }
    });
  }

  // Build the gallery image list (supports string entries and {url|data|src|path} objects)
  private buildGallery(product: any): void {
    const images: any[] = Array.isArray(product?.images) ? product.images : [];
    this.imageUrls = images
      .map(img => {
        if (typeof img === 'string') return img ? this.getImageUrl(img) : '';
        if (img && typeof img === 'object') {
          const raw = img.url || img.data || img.src || img.path || img.value;
          return raw ? this.getImageUrl(raw) : '';
        }
        return '';
      })
      .filter(url => !!url);
    this.activeImage = this.imageUrls[0] || null;
  }

  selectImage(url: string): void {
    this.activeImage = url;
  }

  increase(): void {
    const max = this.product?.quantity ?? Infinity;
    if (this.quantity < max) this.quantity += 1;
  }

  decrease(): void {
    if (this.quantity > 1) this.quantity -= 1;
  }

  addToCart(): void {
    if (!this.product) return;

    const available = this.product.quantity ?? Infinity;
    if (this.quantity > available) {
      this.modalService.showAlert('Cannot add to cart: requested quantity exceeds available stock.', 'Warning');
      return;
    }

    this.cartService.addToCartProduct(
      {
        id: this.product.id,
        name: this.product.name,
        price: this.product.price,
        images: this.product.images,
        stock: this.product.quantity
      },
      this.quantity
    );

    this.quantity = 1;
  }

  logout(): void {
    this.loginService.logout();
  }

  // Updated method to match HomeComponent's logic
  getImageUrl(img: string): string {
    if (!img) return '/assets/img/product-placeholder.svg';

    return productImageUrl(productImageBaseUrl(this.config), img) || '/assets/img/product-placeholder.svg';
  }

  // Method to get product image - same as HomeComponent
  getProductImage(product: any): string {
    if (product?.images && product.images.length > 0) {
      // In HomeComponent, images are already base64 strings
      // Return the first image as-is
      const firstImg = product.images[0];
      if (typeof firstImg === 'string') {
        return firstImg;
      }
      // If it's an object with properties, try to extract the image data
      if (typeof firstImg === 'object') {
        return (firstImg as any).url || (firstImg as any).data || (firstImg as any).src || 
               (firstImg as any).path || (firstImg as any).value || '/assets/img/product-placeholder.svg';
      }
    }
    return '/assets/img/product-placeholder.svg';
  }

  onImageError(event: Event): void {
    useImageFallback(event.target as HTMLImageElement);
  }

  onImageLoaded(): void {
    // Reinitialize carousel when images are loaded
    setTimeout(() => {
      this.initializeCarousel();
    }, 50);
  }

  loadReviews(): void {
    if (!this.product?.id) return;
    this.reviewService.getByProduct(this.product.id).subscribe({
      next: (r) => this.reviews = r || [],
      error: () => { /* ignore errors for now */ }
    });
  }

  setRating(n: number): void {
    this.selectedRating = n;
  }

  reviewComment = '';

  submitReview(): void {
    if (!this.isAuthenticated) {
      this.modalService.showAlert('You must be logged in to submit a rating.', 'Login required');
      return;
    }
    if (!this.selectedRating || this.selectedRating < 1 || this.selectedRating > 5) {
      this.modalService.showAlert('Please select a rating between 1 and 5 stars.', 'Invalid rating');
      return;
    }
    if (!this.reviewComment || this.reviewComment.trim().length < 5) {
      this.modalService.showAlert('Please write a review comment (at least 5 characters).', 'Invalid comment');
      return;
    }
    const payload = { rating: this.selectedRating, comment: this.reviewComment.trim(), productId: this.product.id };
    this.reviewService.addReview(this.product.id, payload).subscribe({
      next: () => {
        this.selectedRating = 0;
        this.reviewComment = '';
        this.loadReviews();
        this.productService.getById(this.product.id).subscribe(p => this.product = p);
        this.showSuccessModal();
      },
      error: (err) => this.modalService.showAlert(err.error || err.error?.message || err.message || 'Unable to submit review.', 'Error')
    });
  }

  showSuccessModal(): void {
    const el = document.getElementById('ratingSuccessModal');
    if (!el) return;
    try {
      const modal = (window as any).bootstrap?.Modal?.getOrCreateInstance(el) || (window as any).bootstrap?.Modal?.new(el);
      modal.show();
    } catch (e) {
      // fallback to simple alert modal
      this.modalService.showAlert('Rating submitted successfully', 'Success', 2000);
    }
  }

  private initializeCarousel(): void {
    try {
      window.dispatchEvent(new Event('carousel:refresh'));
      const $ = (window as any).$;
      if ($ && $.fn && typeof $.fn.owlCarousel !== 'undefined') {
        const el = $('.single-carousel');
        if (el && el.length) {
          // Destroy and reinitialize if needed
          if (el.data('owl.carousel')) {
            el.trigger('destroy.owl.carousel');
            el.removeClass('owl-loaded');
          }
          el.owlCarousel({
            items: 1,
            dots: true,
            nav: true,
            loop: true,
            autoplay: true,
            autoplayTimeout: 3000,
            dotsData: true
          });
        }
      }
    } catch (e) {
      console.warn('Could not initialize carousel:', e);
    }
  }
}