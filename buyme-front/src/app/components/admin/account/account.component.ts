import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthStorageService } from '../../../core/auth-storage.service';
import { UserService, UserProfile } from '../../../services/user.service';
import { LoginService } from '../../../services/login.service';
import { ModalService } from '../../../shared/modal.service';

interface SessionInfo {
  expiresAt: Date | null;
  issuedAt: Date | null;
  issuer: string | null;
  subject: string | null;
  isExpired: boolean;
}

@Component({
  selector: 'app-admin-account',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './account.component.html',
  styleUrls: ['./account.component.css']
})
export class AccountComponent implements OnInit {
  profile: UserProfile | null = null;
  loading = true;
  failed = false;

  session: SessionInfo = {
    expiresAt: null, issuedAt: null, issuer: null, subject: null, isExpired: false,
  };

  readonly realmRoles: string[] = [];

  constructor(
    private userService: UserService,
    private authStorage: AuthStorageService,
    private loginService: LoginService,
    private modalService: ModalService
  ) {}

  ngOnInit(): void {
    this.readSession();
    this.loadProfile();
  }

  get displayName(): string {
    if (!this.profile) return 'Administrator';
    const name = `${this.profile.firstName ?? ''} ${this.profile.lastName ?? ''}`.trim();
    return name || this.profile.email || 'Administrator';
  }

  get initials(): string {
    const source = this.displayName;
    const parts = source.split(/\s+/).filter(Boolean);
    if (!parts.length) return 'A';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  /** Fields the API can return but that currently have no way to be edited. */
  get emptyProfileFields(): { label: string; value: string | null }[] {
    const p = this.profile;
    if (!p) return [];
    // phone/zip are typed as number in UserProfile, so normalise for display.
    const asText = (v: string | number | null | undefined): string | null =>
      v === null || v === undefined || v === '' ? null : String(v);

    return [
      { label: 'Phone', value: asText(p.phone) },
      { label: 'Date of birth', value: asText(p.dob) },
      { label: 'Country', value: asText(p.country) },
      { label: 'City', value: asText(p.city) },
      { label: 'Zip code', value: asText(p.zip) },
      { label: 'Address', value: asText(p.address) },
    ];
  }

  get hasEmptyProfileFields(): boolean {
    return this.emptyProfileFields.length > 0;
  }

  loadProfile() {
    this.loading = true;
    this.failed = false;

    this.userService.getCurrentUser().subscribe({
      next: (profile) => {
        this.profile = profile ?? null;
        this.loading = false;
        if (!profile) this.failed = true;
      },
      error: () => {
        this.profile = null;
        this.loading = false;
        this.failed = true;
      },
    });
  }

  copy(value: string | null | undefined, label: string) {
    if (!value) return;
    if (!navigator.clipboard?.writeText) {
      this.modalService.showAlert('Clipboard is not available in this browser.', 'Copy failed');
      return;
    }
    navigator.clipboard.writeText(value)
      .then(() => this.modalService.showAlert(`${label} copied to clipboard.`, 'Copied'))
      .catch(() => this.modalService.showAlert('Could not write to the clipboard.', 'Copy failed'));
  }

  signOut() {
    this.loginService.logout();
  }

  /** Reads the stored JWT for session facts; the token is the only source for these. */
  private readSession() {
    const token = this.authStorage.getToken();
    if (!token) return;

    const claims = decodeJwtPayload(token);
    if (!claims) return;

    const exp = typeof claims['exp'] === 'number' ? claims['exp'] : null;
    const iat = typeof claims['iat'] === 'number' ? claims['iat'] : null;

    this.session = {
      expiresAt: exp ? new Date(exp * 1000) : null,
      issuedAt: iat ? new Date(iat * 1000) : null,
      issuer: typeof claims['iss'] === 'string' ? claims['iss'] : null,
      subject: typeof claims['sub'] === 'string' ? claims['sub'] : null,
      isExpired: exp ? exp * 1000 <= Date.now() : false,
    };

    const realmAccess = claims['realm_access'] as { roles?: unknown } | undefined;
    const roles = realmAccess?.roles;
    if (Array.isArray(roles)) {
      this.realmRoles.splice(0, this.realmRoles.length, ...roles.map(r => String(r)));
    }
  }
}

/** Minimal base64url JWT payload decode — no verification, display only. */
function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const part = token.split('.')[1];
    if (!part) return null;
    const padded = part.replace(/-/g, '+').replace(/_/g, '/')
      + '='.repeat((4 - (part.length % 4)) % 4);
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}
