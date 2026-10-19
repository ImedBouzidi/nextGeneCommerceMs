import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { UserProfile, UserService } from '../../../services/user.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css'],
  standalone: true,
  imports: [CommonModule, RouterModule]
})
export class ProfileComponent implements OnInit {
  userProfile: UserProfile | null = null;
  loading = true;
  error: string | null = null;
  avatarUrl = 'assets/img/default-avatar.svg';

  constructor(
    private router: Router,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.loadUserProfile();
  }

  editProfile(): void {
    this.router.navigate(['/edit-profile']);
  }

  goBack(): void {
    this.router.navigate(['/client/home']);
  }

  private loadUserProfile(): void {
    this.userService.getCurrentUser().subscribe({
      next: (user) => {
        this.userProfile = user;
        this.avatarUrl = this.userService.resolveProfileImage(user.profilePic);
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.message || err.message || 'Unable to load profile data.';
        this.loading = false;
      }
    });
  }
}