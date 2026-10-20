import { Component, Inject, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { CategoryService } from '../../../services/category.service';
import { ProductService } from '../../../services/product.service';
import { CartService, CartItem } from '../../../services/cart.service';
import { RecommenderService } from '../../../services/recommender.service';
import { UserService } from '../../../services/user.service';
import { CustomerSegmentationService, Promotion } from '../../../services/customer-segmentation.service';
import { OrderService } from '../../../services/order.service';
import { APP_CONFIG } from '../../../../main';
import { PRODUCT_PLACEHOLDER, productImageBaseUrl, productImageUrl, useImageFallback } from '../../../shared/product-image-url';
import { ModalService } from '../../../shared/modal.service';
import { AuthStorageService } from '../../../core/auth-storage.service';

interface Category {
  id: number;
  name: string;
  description: string;
}

interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  quantity: number;
  images?: string[];
  categoryId: number;
  categoryName?: string;
  rate?: number;
}

interface CategoryGroup {
  category: Category;
  products: Product[];
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent implements OnInit, OnDestroy {
  cartProductIds = new Set<number>();

  navigateToProfile() {
    this.router.navigate(['/client/profile']);
  }

  categoriesWithProducts: CategoryGroup[] = [];
  allProducts: Product[] = [];
  loading = true;
  error: string | null = null;
  // Non-fatal: recommendations/promotions failing must never hide the product catalogue.
  softError: string | null = null;
  selectedCategoryId: number | 'all' = 'all';
  private readonly productImageBaseUrl: string;
  
  // Recommendations and Segmentation
  recommendedProducts: Product[] = [];
  loadingRecommendations = false;
  customerPromotions: Promotion[] = [];
  userDisplayName: string | null = null;
  loadingPromotions = false;
  currentPromotion: Promotion | null = null;
  discountPercent = 0;
  currentRecommendationIndex = 0;
  currentPromotionIndex = 0;
  promotionRotationInterval: any = null;
  recommendationRotationInterval: any = null;
  recommendationFade = true;
  // Grid pagination for recommendations
  recommendationPage = 0;
  readonly recsPerPage = 4;


  // user id extracted from JWT 'sub' claim (if available)
  userId: string | null = null;
  // search term for product filtering
  searchTerm: string = '';
  // product search term (admin-style input on manageproduct)
  productSearchTerm: string = '';

  constructor(
    private readonly categoryService: CategoryService,
    private readonly productService: ProductService,
    private readonly cartService: CartService,
    private readonly recommenderService: RecommenderService,
    private readonly userService: UserService,
    private readonly segmentationService: CustomerSegmentationService,
    private readonly orderService: OrderService,
    private readonly router: Router,
    private readonly modalService: ModalService,
    private readonly authStorage: AuthStorageService,
    @Inject(APP_CONFIG) config: any
  ) {
    this.productImageBaseUrl = productImageBaseUrl(config);
  }

  onImageError(event: Event) {
    useImageFallback(event.target as HTMLImageElement);
  }

  ngOnInit(): void {
    // Determine display name early for promotions
    try {
      const token = this.authStorage.getValidToken() || '';
      if (token) {
        const parts = token.split('.');
        if (parts.length >= 2) {
          const payloadJson = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
            this.userDisplayName = payloadJson.preferred_username || payloadJson.name || payloadJson.email || null;
            // keep raw sub (can be UUID). backend will match by string equality when filtering orders
            this.userId = payloadJson.sub || null;
        }
      }
    } catch (e) {
      this.userDisplayName = null;
    }
    this.fetchCatalogue();
    // keep an updated set of product ids currently in the cart
    this.cartService.items$.subscribe((items: CartItem[]) => {
      this.cartProductIds.clear();
      for (const it of items) {
        this.cartProductIds.add(it.productId);
      }
      // Fetch recommendations when cart updates (based on user history)
      // Wait a bit to ensure products are loaded
      setTimeout(() => {
        if (this.allProducts.length > 0) {
          this.fetchRecommendations();
        }
      }, 500);
    });
    
    // Fetch promotions immediately
    this.fetchCustomerSegmentation();
  }

  /**
   * The catalogue stores bare filenames (or legacy data:/http URLs), so a raw
   * `images[0]` is not a loadable URL — the browser would request it from the app
   * origin and 404. Resolve it through the shared helper (same as the single
   * product page, which is why the image appears there but not on the cards).
   */
  getProductImage(product: Product): string {
    const first = product?.images?.[0];
    if (!first) return PRODUCT_PLACEHOLDER;
    const raw = typeof first === 'string' ? first : (first as any).url || (first as any).data || (first as any).src || (first as any).path;
    return productImageUrl(this.productImageBaseUrl, raw) || PRODUCT_PLACEHOLDER;
  }
  trackByCategory(_: number, group: CategoryGroup) {
    return group.category.id;
  }

  trackByProduct(_: number, product: Product) {
    return product.id;
  }

  /** False when we're showing the synthetic single "All Products" group (no categories available). */
  hasCategoryFilter(): boolean {
    return this.categoriesWithProducts.some(g => g.category.id !== 0);
  }

  selectCategory(categoryId: number | 'all'): void {
    this.selectedCategoryId = categoryId;
  }

  scrollToProducts(): void {
    document.getElementById('products-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  getProductsForActiveTab(): Product[] {
    // Prefer the admin-style productSearchTerm if filled, otherwise fall back to the general searchTerm
    const termSource = (this.productSearchTerm && this.productSearchTerm.trim()) ? this.productSearchTerm : this.searchTerm;
    const term = termSource ? termSource.toLowerCase().trim() : '';
    let products: Product[] = [];
    if (this.selectedCategoryId === 'all') {
      products = this.allProducts;
    } else {
      const group = this.categoriesWithProducts.find(g => g.category.id === this.selectedCategoryId);
      products = group ? group.products : [];
    }

    if (!term) return products;

    return products.filter(p => {
      const name = (p.name || '').toString().toLowerCase();
      const desc = (p.description || '').toString().toLowerCase();
      const cat = (p.categoryName || '').toString().toLowerCase();
      return name.includes(term) || desc.includes(term) || cat.includes(term);
    });
  }

  filteredProductsForGroup(group: CategoryGroup): Product[] {
    const termSource = (this.productSearchTerm && this.productSearchTerm.trim()) ? this.productSearchTerm : this.searchTerm;
    const term = termSource ? termSource.toLowerCase().trim() : '';
    if (!term) return group.products;
    return (group.products || []).filter(p => {
      const name = (p.name || '').toString().toLowerCase();
      const desc = (p.description || '').toString().toLowerCase();
      const cat = (p.categoryName || '').toString().toLowerCase();
      return name.includes(term) || desc.includes(term) || cat.includes(term);
    });
  }

  hasAnyMatchingProducts(): boolean {
    if (!this.categoriesWithProducts || this.categoriesWithProducts.length === 0) return false;
    const termSource = (this.productSearchTerm && this.productSearchTerm.trim()) ? this.productSearchTerm : this.searchTerm;
    const term = termSource ? termSource.toLowerCase().trim() : '';
    if (!term) return this.categoriesWithProducts.some(g => (g.products || []).length > 0);
    return this.categoriesWithProducts.some(g => this.filteredProductsForGroup(g).length > 0);
  }

  cardAnimationDelay(index: number): string {
    const step = (index % 4) * 0.2;
    return `${0.1 + step}s`;
  }

  isInCart(product: Product): boolean {
    return this.cartProductIds.has(product.id);
  }

  addToCart(product: Product): void {
    const items = this.cartService.getItems();
    const existing = items.find(i => i.productId === product.id);
    const existingQty = existing ? existing.quantity : 0;
    // validate against stock
    const available = product.quantity ?? 0;
    if (existingQty + 1 > available) {
      this.modalService.showAlert('Cannot add to cart: requested quantity exceeds available stock.', 'Warning');
      return;
    }

    // include stock so cart keeps track of limits
    this.cartService.addToCartProduct({ id: product.id, name: product.name, price: product.price, images: product.images, stock: available }, 1);
    console.log('Product added to cart:', product.id);
  }

  applyPromotion(promo: Promotion | null): void {
    if (!promo) return;
    const target = this.userDisplayName || promo.customer_id || '';
    const pct = (promo as any).discount_percent ?? (promo as any).discount ?? 0;
    const msg = pct ? `Applied ${pct}% discount for ${target}` : `Promotion for ${target}`;
    // For now show a simple confirmation — integrators can wire this to checkout/coupon logic
    this.modalService.showAlert(msg, 'Promotion Applied', 2500);
  }

  private fetchCatalogue(): void {
    this.loading = true;
    // Categories are optional metadata: if the categories endpoint fails we still
    // want the products to render, so it must never break the whole catalogue.
    forkJoin({
      categories: this.categoryService.getAll().pipe(catchError(() => of([] as any[]))),
      products: this.productService.getAll().pipe(catchError((err) => {
        this.error = err?.error?.message || err?.message || 'Unable to load products.';
        return of([] as any[]);
      }))
    }).subscribe({
      next: ({ categories, products }) => {
        // include all products (show out-of-stock items too) so categories display even when stock is zero
        const availableProducts = (products ?? []);
        const baseGroups: CategoryGroup[] = (categories ?? [])
          .map((category) => ({
            category,
            products: availableProducts
              .filter(p => p.categoryId === category.id)
              .sort((a, b) => a.name.localeCompare(b.name))
          }))
          .filter(group => group.products.length > 0)
          .sort((a, b) => a.category.name.localeCompare(b.category.name));

        // No categories returned (endpoint down, or products carry no category):
        // fall back to a synthetic "All Products" group so the index still lists everything.
        const grouped: CategoryGroup[] = baseGroups.length
          ? baseGroups
          : [{
              category: { id: 0, name: 'All Products', description: 'Everything in the catalogue' },
              products: [...availableProducts].sort((a, b) => a.name.localeCompare(b.name))
            }];

        this.categoriesWithProducts = grouped;
        this.allProducts = grouped
          .flatMap(group => group.products.map(product => ({
            ...product,
            categoryName: group.category.name
          })))
          .sort((a, b) => {
            if (a.categoryName === b.categoryName) {
              return a.name.localeCompare(b.name);
            }
            return (a.categoryName || '').localeCompare(b.categoryName || '');
          });

        if (!grouped.length) {
          this.selectedCategoryId = 'all';
        } else if (this.selectedCategoryId !== 'all') {
          const exists = grouped.some(group => group.category.id === this.selectedCategoryId);
          if (!exists) {
            this.selectedCategoryId = 'all';
          }
        }

        this.loading = false;
        // After products are loaded, fetch recommendations
        // Use setTimeout to ensure DOM is ready
        setTimeout(() => {
          this.fetchRecommendations();
        }, 300);
      },
      error: (err) => {
        this.error = err.error?.message || err.message || 'Unable to load catalogue.';
        this.loading = false;
      }
    });
  }

  private fetchRecommendations(): void {
    if (this.loadingRecommendations || this.allProducts.length === 0) {
      return;
    }
    
    this.loadingRecommendations = true;
    
    // Build history from cart + orders (for authenticated users)
    const cartItems = this.cartService.getItems();
    const cartHistory = cartItems.map(item => item.productId);
    
    const loadRecommendations = (userId?: number | string, orderHistory: number[] = []): void => {
      // Combine cart history with order history (deduplicate)
      const combinedHistory = [...new Set([...cartHistory, ...orderHistory])];
      
      this.recommenderService.getRecommendations(combinedHistory, 8, userId).subscribe({
        next: (response) => {
          const recommendedIds = response.recommendations || [];
          this.recommendedProducts = recommendedIds
            .map((id: number) => this.allProducts.find(p => String(p.id) === String(id)))
            .filter((p: Product | undefined): p is Product => p !== undefined)
            .slice(0, 8); // Show up to 8 for grid
          this.loadingRecommendations = false;
          this.recommendationFade = true;
          this.recommendationPage = 0; // Reset to first page
          this.startRecommendationRotation();
        },
        error: (err) => {
          console.error('[Home] recommendations failed', { history: combinedHistory, userId, error: err });
          this.recommendedProducts = [];
          this.loadingRecommendations = false;
          this.softError = 'Unable to load recommendations.';
        }
      });
    };

    if (!this.authStorage.getValidToken()) {
      loadRecommendations();
      return;
    }

    this.userService.getCurrentUser().subscribe({
      next: (user) => {
        // Fetch order history to enrich recommendations
        if (user?.id) {
          this.orderService.getMyOrders().subscribe({
            next: (orders) => {
              const orderProductIds = orders
                .filter(o => o.productId)
                .map(o => o.productId);
              loadRecommendations(user.id, orderProductIds);
            },
            error: () => loadRecommendations(user.id)
          });
        } else {
          loadRecommendations();
        }
      },
      error: () => loadRecommendations()
    });
  }

  fetchCustomerSegmentation(): void {
    if (this.loadingPromotions || !this.userId) {
      return;
    }
    
    this.loadingPromotions = true;
    
    const payload: any = { n_clusters: 3, send: false };
    if (this.userId) payload.userId = this.userId;
    this.segmentationService.runSegmentation(payload).subscribe({
      next: (response) => {
        this.customerPromotions = (response.promotions || []).map((p: any) => ({
          ...p,
          display_name: this.userDisplayName || p.customer_id
        }));

        // Only apply promotions that explicitly target the current logged-in user.
        // This prevents a promotion computed for another customer from being shown
        // to everyone (which previously caused new users to see 10% by default).
        const promosForUser = this.customerPromotions.filter((p: any) => {
          try {
            return this.userId && String(p.customer_id) === String(this.userId);
          } catch (e) {
            return false;
          }
        });

        if (promosForUser.length > 0) {
          this.currentPromotion = promosForUser[0];
          this.currentPromotionIndex = 0;
          const pct = Number((this.currentPromotion as any).discount_percent ?? (this.currentPromotion as any).discount ?? 0) || 0;
          this.discountPercent = pct;
          try { this.cartService.setDiscountPercent(pct); } catch (e) { /* ignore if service not available */ }
          // Start auto-rotating promotions (only meaningful when user has promos)
          this.startPromotionRotation();
        } else {
          // No promotions for this user — ensure we don't show any discount
          this.currentPromotion = null;
          this.currentPromotionIndex = 0;
          this.discountPercent = 0;
          try { this.cartService.setDiscountPercent(0); } catch (e) { /* ignore */ }
        }
        this.loadingPromotions = false;
      },
      error: (err) => {
        console.error('[Home] fetchCustomerSegmentation failed', { payload: { n_clusters: 3, send: false }, error: err });
        // Service already handles errors gracefully, but just in case
        this.customerPromotions = [];
        this.currentPromotion = null;
        this.loadingPromotions = false;
        this.softError = 'Unable to load customer promotions.';
      }
    });
  }

  // Navigation methods for recommendations carousel
  nextRecommendation(): void {
    if (this.recommendedProducts.length > 0) {
      this.recommendationFade = false;
      setTimeout(() => {
        this.currentRecommendationIndex = (this.currentRecommendationIndex + 1) % this.recommendedProducts.length;
        this.recommendationFade = true;
      }, 160);
    }
  }

  previousRecommendation(): void {
    if (this.recommendedProducts.length > 0) {
      this.recommendationFade = false;
      setTimeout(() => {
        this.currentRecommendationIndex = (this.currentRecommendationIndex - 1 + this.recommendedProducts.length) % this.recommendedProducts.length;
        this.recommendationFade = true;
      }, 160);
    }
  }

  goToRecommendation(index: number): void {
    if (index >= 0 && index < this.recommendedProducts.length) {
      this.recommendationFade = false;
      setTimeout(() => {
        this.currentRecommendationIndex = index;
        this.recommendationFade = true;
      }, 120);
    }
  }

  getCurrentRecommendation(): Product | null {
    if (this.recommendedProducts.length > 0 && this.currentRecommendationIndex < this.recommendedProducts.length) {
      return this.recommendedProducts[this.currentRecommendationIndex];
    }
    return null;
  }

  // Grid pagination for recommendations
  get totalRecommendationPages(): number {
    return Math.ceil(this.recommendedProducts.length / this.recsPerPage);
  }

  getVisibleRecommendations(): Product[] {
    const start = this.recommendationPage * this.recsPerPage;
    return this.recommendedProducts.slice(start, start + this.recsPerPage);
  }

  nextRecommendationPage(): void {
    if (this.recommendationPage < this.totalRecommendationPages - 1) {
      this.recommendationFade = false;
      setTimeout(() => {
        this.recommendationPage++;
        this.recommendationFade = true;
      }, 160);
    }
  }

  prevRecommendationPage(): void {
    if (this.recommendationPage > 0) {
      this.recommendationFade = false;
      setTimeout(() => {
        this.recommendationPage--;
        this.recommendationFade = true;
      }, 160);
    }
  }

  // Navigation methods for promotions
  nextPromotion(): void {
    if (this.customerPromotions.length > 0) {
      this.currentPromotionIndex = (this.currentPromotionIndex + 1) % this.customerPromotions.length;
      this.currentPromotion = this.customerPromotions[this.currentPromotionIndex];
    }
  }

  previousPromotion(): void {
    if (this.customerPromotions.length > 0) {
      this.currentPromotionIndex = (this.currentPromotionIndex - 1 + this.customerPromotions.length) % this.customerPromotions.length;
      this.currentPromotion = this.customerPromotions[this.currentPromotionIndex];
    }
  }

  startRecommendationRotation(): void {
    if (this.recommendationRotationInterval) {
      clearInterval(this.recommendationRotationInterval);
    }
    if (this.recommendedProducts.length > 1) {
      this.recommendationRotationInterval = setInterval(() => {
        this.nextRecommendation();
      }, 5000);
    }
  }

  stopRecommendationRotation(): void {
    if (this.recommendationRotationInterval) {
      clearInterval(this.recommendationRotationInterval);
      this.recommendationRotationInterval = null;
    }
  }

  goToPromotion(index: number): void {
    if (index >= 0 && index < this.customerPromotions.length) {
      this.currentPromotionIndex = index;
      this.currentPromotion = this.customerPromotions[index];
    }
  }

  startPromotionRotation(): void {
    // Clear existing interval
    if (this.promotionRotationInterval) {
      clearInterval(this.promotionRotationInterval);
    }
    // Auto-rotate promotions every 5 seconds
    if (this.customerPromotions.length > 1) {
      this.promotionRotationInterval = setInterval(() => {
        this.nextPromotion();
      }, 5000);
    }
  }

  stopPromotionRotation(): void {
    if (this.promotionRotationInterval) {
      clearInterval(this.promotionRotationInterval);
      this.promotionRotationInterval = null;
    }
  }

  ngOnDestroy(): void {
    this.stopPromotionRotation();
    this.stopRecommendationRotation();
  }
}