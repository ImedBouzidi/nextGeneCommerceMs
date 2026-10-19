import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CartService, CartItem } from '../../../services/cart.service';
import { ModalService } from '../../../shared/modal.service';
import { LoginService } from '../../../services/login.service';
import { Router, RouterModule } from '@angular/router';



@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.css'
})
export class CartComponent {
  items$;
  shippingRate = 0; // Set to 0 since we're removing shipping
  filteredItems: CartItem[] = [];
  searchTerm = '';
  private allItems: CartItem[] = [];
  discountPercent = 0;

  constructor(private readonly cartService: CartService, private readonly router: Router, private readonly modalService: ModalService, private readonly loginService: LoginService) {
    this.items$ = this.cartService.items$;
    this.items$.subscribe(items => {
      this.allItems = items;
      this.filteredItems = items;
    });
    // subscribe to discount updates
    this.cartService.discount$.subscribe(pct => this.discountPercent = pct || 0);
  }

  increase(item: CartItem) {
    // enforce stock limit if available
    if (typeof item.stock === 'number' && item.quantity >= item.stock) {
      this.modalService.showAlert('Cannot add more — product stock limit reached.', 'Warning');
      return;
    }
    this.cartService.addToCartProduct({ id: item.productId, name: item.name, price: item.price, images: item.image ? [item.image] : [], stock: item.stock }, 1);
  }

  decrease(item: CartItem) {
    if (item.quantity > 1) {
      // reduce quantity by 1
      const items = this.cartService.getItems().map(i => i.productId === item.productId ? { ...i, quantity: i.quantity - 1 } : i);
      // update subject and storage via internal methods (use remove/add to keep interface simple)
      this.cartService.clear();
      for (const i of items) {
        this.cartService.addItem(i);
      }
    } else {
      this.remove(item.productId);
    }
  }

  remove(productId: number) {
    this.cartService.removeItem(productId);
  }

  getSubtotal(items: CartItem[]) {
    return items.reduce((s, it) => s + (it.price * it.quantity), 0);
  }

  getTotalWithDiscount(items: CartItem[]) {
    return this.cartService.getTotalWithDiscount(items) + this.shippingRate;
  }

  proceedToCheckout() {
    this.router.navigate(['/client/checkout']);
  }

  // Search functionality
  onSearch() {
    if (!this.searchTerm.trim()) {
      this.filteredItems = [...this.allItems];
      return;
    }
    
    const term = this.searchTerm.toLowerCase().trim();
    this.filteredItems = this.allItems.filter(item => 
      item.name.toLowerCase().includes(term)
    );
  }

  clearSearch() {
    this.searchTerm = '';
    this.filteredItems = [...this.allItems];
  }

  // Navigation methods
  navigateToProfile() {
    this.router.navigate(['/client/profile']);
  }

  logout() {
    this.loginService.logout();
  }

  navigateToHome() {
    this.router.navigate(['/client/home']);
  }
}