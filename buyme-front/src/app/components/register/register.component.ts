import { Component } from '@angular/core';
import {
  FormBuilder,
  ValidatorFn,
  ValidationErrors,
  FormGroup,
  Validators,
  AbstractControl,
  ReactiveFormsModule,
  FormControl
} from '@angular/forms';
import {RegisterService} from "../../services/register.service";
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import {Router, RouterModule} from '@angular/router';
import {RegisterResponse} from '../../models/register-response';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css'],
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    RouterModule
  ]
})
export class RegisterComponent {
  registerForm: FormGroup;
  termsControl: FormControl<boolean>;
  message: string | null = null;
  messageType: 'success' | 'error' | null = null;
  currentStep: number = 1;
  totalSteps: number = 3;
  isSubmitting = false;
  selectedProfileFile: File | null = null;
  profilePreview: string | null = null;

  constructor(private fb: FormBuilder, private registerService: RegisterService, private router: Router) {
    this.termsControl = new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] });

    this.registerForm = this.fb.group({
      firstName: this.fb.control('', { validators: [Validators.required], nonNullable: true }),
      lastName: this.fb.control('', { validators: [Validators.required], nonNullable: true }),
      dob: this.fb.control('', { nonNullable: true }),
      profilePicFile: this.fb.control<File | null>(null),

      email: this.fb.control('', { validators: [Validators.required, Validators.email], nonNullable: true }),
      password: this.fb.control('', { validators: [Validators.required, Validators.minLength(8)], nonNullable: true }),
      matchingPassword: this.fb.control('', { validators: [Validators.required], nonNullable: true }),

      phone: this.fb.control('', { nonNullable: true }),
      country: this.fb.control('', { nonNullable: true }),
      city: this.fb.control('', { nonNullable: true }),
      zip: this.fb.control('', { nonNullable: true }),
      address: this.fb.control('', { nonNullable: true })
    }, { validators: this.passwordMatchValidator });
  }

  passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const password = control.get('password')?.value;
    const confirmPassword = control.get('matchingPassword')?.value;
    return password === confirmPassword ? null : { passwordMismatch: true };
  };

  nextStep() {
    if (this.currentStep < this.totalSteps && this.isCurrentStepValid()) {
      this.currentStep++;
    }
  }

  previousStep() {
    if (this.currentStep > 1) {
      this.currentStep--;
    }
  }

  isCurrentStepValid(): boolean {
    switch (this.currentStep) {
      case 1:
        return !!(this.registerForm.get('firstName')?.valid &&
          this.registerForm.get('lastName')?.valid);
      case 2:
        return !!(this.registerForm.get('email')?.valid &&
          this.registerForm.get('password')?.valid &&
          this.registerForm.get('matchingPassword')?.valid &&
          !this.registerForm.hasError('passwordMismatch'));
      case 3:
        return true;
      default:
        return false;
    }
  }

  onSubmit() {
    if (this.registerForm.invalid || this.termsControl.invalid) {
      this.registerForm.markAllAsTouched();
      this.termsControl.markAsTouched();
      return;
    }

    const formValues = this.registerForm.getRawValue();
    const formData = new FormData();

    formData.append('firstName', formValues.firstName.trim());
    formData.append('lastName', formValues.lastName.trim());
    formData.append('email', formValues.email.trim());
    formData.append('password', formValues.password);

    if (formValues.dob) {
      formData.append('dob', formValues.dob);
    }

    if (formValues.phone) {
      // Normalize phone: send digits only to avoid server validation issues (strip +, spaces, dashes)
      const phoneStr: string = String(formValues.phone || '').trim();
      const digitsOnly = phoneStr.replace(/\D+/g, '');
      if (digitsOnly) {
        formData.append('phone', digitsOnly);
      }
    }

    if (formValues.country) {
      formData.append('country', formValues.country.trim());
    }

    if (formValues.city) {
      formData.append('city', formValues.city.trim());
    }

    if (formValues.zip) {
      formData.append('zip', formValues.zip.toString());
    }

    if (formValues.address) {
      formData.append('address', formValues.address.trim());
    }

    if (this.selectedProfileFile) {
      formData.append('profilePicFile', this.selectedProfileFile, this.selectedProfileFile.name);
    }

    this.isSubmitting = true;
    this.message = null;
    this.messageType = null;

    this.registerService.register(formData).subscribe({
      next: (res: RegisterResponse) => {
        this.isSubmitting = false;
        this.message = res.message;
        this.messageType = 'success';

        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 1500);
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.message = err.error?.message || 'Registration failed';
        this.messageType = 'error';
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      this.selectedProfileFile = null;
      this.profilePreview = null;
      this.registerForm.get('profilePicFile')?.setValue(null);
      return;
    }

    const file = input.files[0];
    this.selectedProfileFile = file;
    this.registerForm.get('profilePicFile')?.setValue(file);

    const reader = new FileReader();
    reader.onload = () => {
      this.profilePreview = reader.result as string;
    };
    reader.readAsDataURL(file);
  }
}