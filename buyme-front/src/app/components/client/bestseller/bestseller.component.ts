import { Component, Inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ProductService } from '../../../services/product.service';
import { LoginService } from '../../../services/login.service';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { PRODUCT_PLACEHOLDER, productImageBaseUrl, productImageUrl, useImageFallback } from '../../../shared/product-image-url';
import { APP_CONFIG } from '../../../../main';

interface Product {
  id: number;
  name: string;
  description?: string;
  price: number;
  quantity: number;
  images?: string[];
  categoryName?: string;
  rate?: number;
}

@Component({
  selector: 'app-bestseller',
  imports: [CommonModule, RouterModule],
  templateUrl: './bestseller.component.html',
  styleUrl: './bestseller.component.css'
})
export class BestsellerComponent implements OnInit {

  products: Product[] = [];
  loading = true;
  error: string | null = null;

  readonly maxItems = 8;
  private readonly productImageBaseUrl: string;

  constructor(
    private router: Router,
    private productService: ProductService,
    private loginService: LoginService,
    @Inject(APP_CONFIG) config: any
  ) {
    this.productImageBaseUrl = productImageBaseUrl(config);
  }

  ngOnInit(): void {
    this.loadBestsellers();
  }

  loadBestsellers(): void {
    this.loading = true;
    this.error = null;
    this.productService.getAll().pipe(
      catchError((err) => {
        this.error = err?.error?.message || err?.message || 'Unable to load bestsellers.';
        return of([] as any[]);
      })
    ).subscribe({
      next: (products) => {
        // Rank by customer rating (highest first), then by review volume proxy.
        this.products = (products ?? [])
          .slice()
          .sort((a: Product, b: Product) => {
            const rateDiff = (Number(b.rate) || 0) - (Number(a.rate) || 0);
            if (rateDiff !== 0) return rateDiff;
            return (b.quantity || 0) - (a.quantity || 0);
          })
          .slice(0, this.maxItems);
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Unable to load bestsellers.';
        this.loading = false;
      }
    });
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

  starsFor(rate?: number): boolean[] {
    const value = Math.max(0, Math.min(5, Math.round(Number(rate) || 0)));
    return [0, 1, 2, 3, 4].map(i => i < value);
  }

  medalClass(index: number): string {
    return `rank-${Math.min(index + 1, 4)}`;
  }

  trackByProduct(_: number, product: Product): number {
    return product.id;
  }

  logout() {
    this.loginService.logout();
  }
}