import { Component, OnDestroy, OnInit, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterModule, NavigationEnd, Router } from '@angular/router';
import { timeout, catchError, filter } from 'rxjs/operators';
import { throwError, Subscription } from 'rxjs';
import { ProductService } from '../../../services/product.service';
import { CategoryService } from '../../../services/category.service';
import { ModalService } from '../../../shared/modal.service';
import { LoginService } from '../../../services/login.service';
import { APP_CONFIG } from '../../../../main';
import { productImageBaseUrl, productImageUrl, useImageFallback } from '../../../shared/product-image-url';

type AlertType = 'success' | 'error' | 'info';

interface Category {
  id: number | null;
  name: string;
  description: string;
}

interface Product {
  id: number | null;
  name: string;
  description: string;
  price: number;
  /** Nullable: rows created before quantity was persisted come back as null. */
  quantity?: number | null;
review?: string;  // ← Still string for UI binding
  images?: string[];
  rate?: number;
  categoryId: number | null;
  categoryName?: string;
}

@Component({
  selector: 'app-manageproduct',
  standalone: true,
  templateUrl: './manageproduct.component.html',
  styleUrls: ['./manageproduct.component.css'],
  imports: [CommonModule, FormsModule, RouterModule]
})
export class ManageproductComponent implements OnInit, OnDestroy {
  autoRefreshPending = false;
  operationInProgress = false;
  products: Product[] = [];
  categories: Category[] = [];

  /**
   * Products store bare filenames (e.g. "abc-123.jpg") in the `images` list, so the
   * stored value is not a usable URL on its own. Prefixing with the gateway's
   * upload path is what turns them into something the browser can load.
   */
  productImageBaseUrl = '';

  newCategory: Category = this.getEmptyCategory();
  newProduct: Product = this.getEmptyProduct();
  selectedProductFiles: File[] = [];
  productImagePreviews: string[] = [];

  showCategoryModal = false;
  showProductModal = false;
  isEditingCategory = false;
  isEditingProduct = false;
  categoryFormValid = false;
  productFormValid = false;
  categorySearchTerm = '';
  productSearchTerm = '';

  categoriesLoading = false;
  productsLoading = false;
  savingCategory = false;
  savingProduct = false;

  alertMessage = '';
  alertType: AlertType = 'info';
  showAlert = false;
  private alertTimeout?: ReturnType<typeof setTimeout>;
  private autoRefreshTimeout?: ReturnType<typeof setTimeout>;
  private subscriptions: Subscription[] = [];

  activeSection: 'categories' | 'products' = 'categories';

  constructor(
    private productService: ProductService,
    private categoryService: CategoryService,
    private router: Router,
    private modalService: ModalService,
    private loginService: LoginService,
    @Inject(APP_CONFIG) config: any
  ) {
    this.productImageBaseUrl = productImageBaseUrl(config);
  }

  ngOnInit(): void {
    // /admin/categories and /admin/products are two sidebar entries pointing at this
    // one page, so open the tab that matches the route the admin actually clicked.
    this.activeSection = this.sectionForUrl(this.router.url);

    const navSub = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        this.activeSection = this.sectionForUrl(event.urlAfterRedirects);
      });
    this.subscriptions.push(navSub);

    this.loadCategories();
    this.loadProducts();
  }

  private sectionForUrl(url: string): 'categories' | 'products' {
    return url.includes('/categories') ? 'categories' : 'products';
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    if (this.alertTimeout) clearTimeout(this.alertTimeout);
    if (this.autoRefreshTimeout) clearTimeout(this.autoRefreshTimeout);
  }

displayAlert(message: string, type: AlertType = 'info', refreshTarget?: 'categories' | 'products' | 'all') {
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

private scheduleCloseAndRefresh(target: 'categories' | 'products' | 'all') {
  // Clear any existing auto-refresh timeouts
  if (this.autoRefreshTimeout) {
    clearTimeout(this.autoRefreshTimeout);
    this.autoRefreshTimeout = undefined;
  }

  // Show success alert immediately
  this.showAlert = true;
  this.alertType = 'success';
  this.alertMessage = `Success! Modal closing and page refreshing in 3 seconds...`;

  // Close modals after 3 seconds
  setTimeout(() => {
    if (target === 'categories' || target === 'all') this.showCategoryModal = false;
    if (target === 'products' || target === 'all') this.showProductModal = false;
    // Ensure loader overlay is hidden once modal is closed
    this.operationInProgress = false;
    this.savingCategory = false;
    this.savingProduct = false;
  }, 3000);

  // Reload page after 4 seconds to reflect backend state (after modal fully closed)
  this.autoRefreshTimeout = setTimeout(() => {
    globalThis.location.reload();
  }, 4000);
}

  closeAlert() {
    if (this.alertTimeout) clearTimeout(this.alertTimeout);
    this.showAlert = false;
  }

  /* ========= CATEGORY CRUD ========= */
  loadCategories(showLoader: boolean = true) {
    this.categoriesLoading = showLoader;
    const sub = this.categoryService.getAll().subscribe({
      next: (res) => {
        this.categories = res ?? [];
        this.categoriesLoading = false;
      },
      error: (err) => {
        this.categoriesLoading = false;
        this.displayAlert('Error loading categories: ' + (err.message || 'Unknown error'), 'error');
      },
    });
    this.subscriptions.push(sub);
  }

  openCategoryModal(category?: Category) {
    this.isEditingCategory = !!category;
    this.newCategory = category ? { ...category } : this.getEmptyCategory();
    this.savingCategory = false;
    this.showCategoryModal = true;
    this.validateCategoryForm();
  }

  validateCategoryForm() {
    this.categoryFormValid = !!this.newCategory.name.trim() && !!this.newCategory.description.trim();
  }
saveCategory() {
  if (!this.categoryFormValid || this.savingCategory) return;

  this.savingCategory = true;
  this.operationInProgress = true;

  const request$ = (this.isEditingCategory
    ? this.categoryService.update(this.newCategory.id!, this.newCategory)
    : this.categoryService.create(this.newCategory)
  ).pipe(timeout(10000));

  const sub = request$.subscribe({
    next: () => {
      // Success: close modal and reload page immediately
      this.showCategoryModal = false;
      this.operationInProgress = false;
      this.savingCategory = false;
      globalThis.location.reload();
    },
    error: (err) => {
      this.savingCategory = false;
      this.operationInProgress = false;
      if (err.name === 'TimeoutError') {
        this.displayAlert('Request timed out (10s). The change may already have been saved — refresh to check.', 'error');
        return;
      }
      // Optional: console log for debugging, no alert shown to user
      console.error('Category operation failed:', err);
      // Modal stays open so user can retry
    },
  });
  this.subscriptions.push(sub);
}
  async deleteCategory(id: number) {
    const ok = await this.modalService.showConfirm('Delete this category? This cannot be undone.');
    if (!ok) return;

    this.operationInProgress = true;

    const sub = this.categoryService.delete(id).pipe(
      timeout(3000),
      catchError(err => throwError(() => err))
    ).subscribe({
      next: () => {
        this.operationInProgress = false;
        this.displayAlert('Category deleted successfully! Refreshing in 3 seconds...', 'success', 'categories');
      },
      error: (err) => {
        this.operationInProgress = false;
        const errorMsg = err.name === 'TimeoutError'
          ? 'Request timed out (3s). Please try again.'
          : this.extractErrorMessage(err);
        this.displayAlert(`Error deleting category: ${errorMsg}`, 'error');
      }
    });
    this.subscriptions.push(sub);
  }

  /* ========= PRODUCT CRUD ========= */
  loadProducts(showLoader: boolean = true) {
    this.productsLoading = showLoader;
    const sub = this.productService.getAll().subscribe({
      next: (res) => {
        this.products = res ?? [];
        this.productsLoading = false;
      },
      error: (err) => {
        this.productsLoading = false;
        this.displayAlert('Error loading products: ' + (err.message || 'Unknown error'), 'error');
      },
    });
    this.subscriptions.push(sub);
  }

  openProductModal(product?: Product) {
    this.isEditingProduct = !!product;
    this.newProduct = product
      ? {
          id: product.id,
          name: product.name,
          description: product.description,
          price: product.price,
          quantity: product.quantity ?? 1,
          review: Array.isArray(product.review) 
  ? (product.review[0] ?? '') 
  : (product.review ?? ''),
          images: [...(product.images || [])],
          rate: product.rate ?? 0,
          categoryId: product.categoryId,
          categoryName: product.categoryName
        }
      : this.getEmptyProduct();
    this.productImagePreviews = [];
    this.selectedProductFiles = [];
    this.savingProduct = false;
    this.showProductModal = true;
    this.validateProductForm();
  }

  validateProductForm() {
    const hasBasics = !!this.newProduct.name.trim() &&
      !!this.newProduct.description.trim() &&
      this.newProduct.price > 0 &&
      // 0 is valid stock (out of stock), so only reject a missing/negative value —
      // a new product still needs an explicit quantity.
      (this.newProduct.quantity ?? 0) >= 0 &&
      !!this.newProduct.categoryId;

    const hasImages = this.isEditingProduct
      ? (this.newProduct.images?.length ?? 0) > 0 || this.selectedProductFiles.length > 0
      : this.selectedProductFiles.length > 0;

    this.productFormValid = hasBasics && hasImages;
  }

  onProductFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;

    this.selectedProductFiles = Array.from(input.files);
    this.productImagePreviews = [];
    this.selectedProductFiles.forEach(file => {
      const reader = new FileReader();
      reader.onload = () => this.productImagePreviews.push(reader.result as string);
      reader.readAsDataURL(file);
    });
    this.validateProductForm();
  }

  removeImageChip(index: number, existing: boolean) {
    if (existing && this.newProduct.images) {
      this.newProduct.images.splice(index, 1);
    } else {
      this.selectedProductFiles.splice(index, 1);
      this.productImagePreviews.splice(index, 1);
    }
    this.validateProductForm();
  }

  saveProduct() {
  if (!this.productFormValid || this.savingProduct) return;

  this.savingProduct = true;
  this.operationInProgress = true;

  const payload = this.isEditingProduct
    ? this.buildProductUpdatePayload()
    : this.buildProductCreatePayload();

  const request$ = (this.isEditingProduct
    ? this.productService.update(this.newProduct.id!, payload)
    : this.productService.create(payload as FormData)
  ).pipe(timeout(20000));

  const sub = request$.subscribe({
    next: () => {
      // Success: close modal and reload page immediately
      this.showProductModal = false;
      this.operationInProgress = false;
      this.savingProduct = false;
      globalThis.location.reload();
    },
    error: (err) => {
      this.savingProduct = false;
      this.operationInProgress = false;
      if (err.name === 'TimeoutError') {
        this.displayAlert('Upload timed out (20s). The product may already have been saved — refresh to check.', 'error');
        return;
      }
      console.error('Product operation failed:', err);
      // Modal stays open so user can retry
    },
  });
  this.subscriptions.push(sub);
}
  async deleteProduct(id: number) {
    const ok = await this.modalService.showConfirm('Delete this product? This cannot be undone.');
    if (!ok) return;

    this.operationInProgress = true;

    const sub = this.productService.delete(id).pipe(
      timeout(3000),
      catchError(err => throwError(() => err))
    ).subscribe({
      next: () => {
        this.operationInProgress = false;
        this.displayAlert('Product deleted successfully! Refreshing in 3 seconds...', 'success', 'products');
      },
      error: (err) => {
        this.operationInProgress = false;
        const errorMsg = err.name === 'TimeoutError'
          ? 'Request timed out (3s). Please try again.'
          : this.extractErrorMessage(err);
        this.displayAlert(`Error deleting product: ${errorMsg}`, 'error');
      }
    });
    this.subscriptions.push(sub);
  }

  /* ========= HELPERS ========= */
  filteredCategories(): Category[] {
    const term = this.categorySearchTerm.trim().toLowerCase();
    if (!term) return this.categories;
    return this.categories.filter(cat =>
      cat.name.toLowerCase().includes(term) ||
      cat.description.toLowerCase().includes(term)
    );
  }
  onImageError(event: any) {
    useImageFallback(event.target as HTMLImageElement);
  }

  /** Turns a stored image filename into a loadable URL. */
  imageUrl(filename: string | null | undefined): string {
    return productImageUrl(this.productImageBaseUrl, filename);
  }

  filteredProducts(): Product[] {
    const term = this.productSearchTerm.trim().toLowerCase();
    if (!term) return this.products;
    return this.products.filter(prod =>
      prod.name.toLowerCase().includes(term) ||
      prod.description.toLowerCase().includes(term) ||
      (prod.categoryName?.toLowerCase().includes(term) ?? false)
    );
  }

  /** Stock as a plain number. Legacy rows persisted with NULL quantity read as 0. */
  stockOf(product: Product): number {
    return Number(product.quantity) || 0;
  }

  getCategoryName(catId: number) {
    return this.categories.find(c => c.id === catId)?.name || 'N/A';
  }

  setSection(section: 'categories' | 'products') {
    this.activeSection = section;
  }

  refreshAll() {
    this.loadCategories();
    this.loadProducts();
  }

  logout() {
    this.loginService.logout();
  }

  private buildProductCreatePayload(): FormData {
    const formData = new FormData();
    formData.append('name', this.newProduct.name.trim());
    formData.append('description', this.newProduct.description.trim());
    formData.append('price', this.newProduct.price.toString());
    formData.append('categoryId', String(this.newProduct.categoryId));
    
    // Ensure quantity is properly converted to string
    const quantity = Number(this.newProduct.quantity) || 1;
    formData.append('quantity', quantity.toString());
    
    if (this.newProduct.review) formData.append('review', this.newProduct.review.trim());
    this.selectedProductFiles.forEach(file => formData.append('images', file, file.name));
    return formData;
  }

private buildProductUpdatePayload(): any {
  // Convert single review string to array for backend compatibility
  const reviewForBackend = this.newProduct.review?.trim() 
    ? [this.newProduct.review.trim()] 
    : [];

  const quantity = Number(this.newProduct.quantity) || 1;

  return {
    id: this.newProduct.id,
    name: this.newProduct.name.trim(),
    description: this.newProduct.description.trim(),
    price: Number(this.newProduct.price),
    quantity: quantity,
    review: reviewForBackend,        // ← Now allowed: string[] for backend
    rate: this.newProduct.rate ?? 0,
    categoryId: this.newProduct.categoryId,
    categoryName: this.newProduct.categoryName || undefined
    // images intentionally omitted — not updated
  };
}

  private resetProductForm() {
    this.newProduct = this.getEmptyProduct();
    this.selectedProductFiles = [];
    this.productImagePreviews = [];
    this.productFormValid = false;
  }

  private getEmptyCategory(): Category {
    return { id: null, name: '', description: '' };
  }

  private getEmptyProduct(): Product {
    return {
      id: null,
      name: '',
      description: '',
      price: 0,
      quantity: 1,
      review: '',
      images: [],
      rate: 0,
      categoryId: null
    };
  }

  private scheduleAutoRefresh(target: 'categories' | 'products' | 'all') {
    if (this.autoRefreshTimeout) clearTimeout(this.autoRefreshTimeout);

    // Show the overlay and perform a full page reload after 3 seconds
    this.autoRefreshPending = true;
    this.autoRefreshTimeout = setTimeout(() => {
      this.autoRefreshPending = false;
      this.autoRefreshTimeout = undefined;
      // Full reload ensures the UI and any caches are consistent after backend changes
      globalThis.location.reload();
    }, 3000);
  }

  private extractErrorMessage(err: any): string {
    if (err?.error) {
      if (typeof err.error === 'string') return err.error;
      if (err.error.message) return err.error.message;
      if (typeof err.error === 'object') return JSON.stringify(err.error);
    }
    return err?.message || 'Unknown error';
  }
  // Add this method to your component class, right after the existing methods:

updateQuantity(value: any) {
  // 0 is a legitimate value here — it is how an admin marks a product out of stock.
  // The old clamp (anything below 1 became 1) made "out of stock" unreachable.
  const numValue = Number(value);
  this.newProduct.quantity = isNaN(numValue) || numValue < 0 ? 0 : Math.floor(numValue);
  this.validateProductForm();
}
}