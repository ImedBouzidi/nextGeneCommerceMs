import { Component, Inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ProductService } from '../../../services/product.service';
import { CategoryService } from '../../../services/category.service';
import { CartService, CartItem } from '../../../services/cart.service';
import { LoginService } from '../../../services/login.service';
import { PRODUCT_PLACEHOLDER, productImageBaseUrl, productImageUrl, useImageFallback } from '../../../shared/product-image-url';
import { APP_CONFIG } from '../../../../main';

interface Category {
  id: number;
  name: string;
  description?: string;
}

interface Product {
  id: number;
  name: string;
  description?: string;
  price: number;
  quantity: number;
  images?: string[];
  categoryId?: number;
  categoryName?: string;
  rate?: number;
}

type SortKey = 'newest' | 'price-asc' | 'price-desc' | 'rating';

@Component({
  selector: 'app-shop',
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './shop.component.html',
  styleUrl: './shop.component.css'
})
export class ShopComponent implements OnInit {
  products: Product[] = [];
  categories: Category[] = [];
  loading = true;
  error: string | null = null;

  searchTerm = '';
  selectedCategoryId: number | 'all' = 'all';
  sortKey: SortKey = 'newest';

  cartProductIds = new Set<number>();

  readonly sortOptions: { value: SortKey; label: string }[] = [
    { value: 'newest', label: 'Newest first' },
    { value: 'price-asc', label: 'Price: low → high' },
    { value: 'price-desc', label: 'Price: high → low' },
    { value: 'rating', label: 'Top rated' }
  ];

  private readonly productImageBaseUrl: string;

  constructor(
    private router: Router,
    private productService: ProductService,
    private categoryService: CategoryService,
    private cartService: CartService,
    private loginService: LoginService,
    @Inject(APP_CONFIG) config: any
  ) {
    this.productImageBaseUrl = productImageBaseUrl(config);
  }

  ngOnInit(): void {
    this.cartService.items$.subscribe((items: CartItem[]) => {
      this.cartProductIds = new Set<number>(items.map(i => i.productId));
    });
    this.loadCatalogue();
  }

  loadCatalogue(): void {
    this.loading = true;
    this.error = null;
    forkJoin({
      // Categories are optional metadata — never let them break the catalogue.
      categories: this.categoryService.getAll().pipe(catchError(() => of([] as any[]))),
      products: this.productService.getAll().pipe(
        catchError((err) => {
          this.error = err?.error?.message || err?.message || 'Unable to load products.';
          return of([] as any[]);
        })
      )
    }).subscribe({
      next: ({ categories, products }) => {
        this.categories = (categories ?? []).slice().sort((a, b) => (a.name || '').localeCompare(b.name || ''));
        this.products = (products ?? []).map(p => ({
          ...p,
          categoryName: p.categoryName || this.categoryName(p.categoryId)
        }));
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Unable to load catalogue.';
        this.loading = false;
      }
    });
  }

  private categoryName(categoryId?: number): string {
    if (categoryId == null) return 'Uncategorised';
    const found = this.categories.find(c => c.id === categoryId);
    return found?.name || 'Uncategorised';
  }

  getProductImage(product: Product): string {
    const first = product?.images?.[0];
    if (!first) return PRODUCT_PLACEHOLDER;
    const raw = typeof first === 'string' ? first : (first as any).url || (first as any).data || (first as any).src || (first as any).path;
    return productImageUrl(this.productImageBaseUrl, raw) || PRODUCT_PLACEHOLDER;
  }

  onImageError(event: Event): void {
    useImageFallback(event.target as HTMLImageElement);
  }

  selectCategory(id: number | 'all'): void {
    this.selectedCategoryId = id;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedCategoryId = 'all';
    this.sortKey = 'newest';
  }

  get filteredProducts(): Product[] {
    const term = (this.searchTerm || '').toLowerCase().trim();
    let list = this.products.filter(p => {
      if (this.selectedCategoryId !== 'all' && p.categoryId !== this.selectedCategoryId) return false;
      if (!term) return true;
      const haystack = `${p.name || ''} ${p.description || ''} ${p.categoryName || ''}`.toLowerCase();
      return haystack.includes(term);
    });

    list = list.slice();
    switch (this.sortKey) {
      case 'price-asc':  list.sort((a, b) => (a.price ?? 0) - (b.price ?? 0)); break;
      case 'price-desc': list.sort((a, b) => (b.price ?? 0) - (a.price ?? 0)); break;
      case 'rating':     list.sort((a, b) => (b.rate ?? 0) - (a.rate ?? 0)); break;
      default:           list.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
    }
    return list;
  }

  starsFor(rate?: number): boolean[] {
    const value = Math.max(0, Math.min(5, Math.round(Number(rate) || 0)));
    return [0, 1, 2, 3, 4].map(i => i < value);
  }

  isInStock(product: Product): boolean {
    return (product.quantity ?? 0) > 0;
  }

  isLowStock(product: Product): boolean {
    const q = product.quantity ?? 0;
    return q > 0 && q <= 5;
  }

  isInCart(product: Product): boolean {
    return this.cartProductIds.has(product.id);
  }

  addToCart(product: Product): void {
    if (!this.isInStock(product)) return;
    this.cartService.addToCartProduct({
      id: product.id,
      name: product.name,
      price: product.price,
      images: product.images,
      stock: product.quantity
    }, 1);
  }

  trackByProduct(_: number, product: Product): number {
    return product.id;
  }

  trackByCategory(_: number, category: Category): number {
    return category.id;
  }

  logout() {
    this.loginService.logout();
  }
}