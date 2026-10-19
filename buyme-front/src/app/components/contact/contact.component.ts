import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-contact',
  imports: [RouterModule, FormsModule, CommonModule],
  templateUrl: './contact.component.html',
  styleUrl: './contact.component.css'
})
export class ContactComponent {
  model = {
    name: '',
    email: '',
    topic: '',
    order: '',
    message: ''
  };

  submitted = false;
  sending = false;

  get isValid(): boolean {
    return !!this.model.name.trim()
      && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.model.email.trim())
      && !!this.model.topic
      && this.model.message.trim().length >= 10;
  }

  send(): void {
    this.submitted = true;
    if (!this.isValid || this.sending) {
      return;
    }
    this.sending = true;
    // No contact endpoint is wired up yet — acknowledge locally so the UX is complete.
    setTimeout(() => {
      this.sending = false;
      this.submitted = false;
      this.sent = true;
      this.model = { name: '', email: '', topic: '', order: '', message: '' };
    }, 600);
  }

  sent = false;
}