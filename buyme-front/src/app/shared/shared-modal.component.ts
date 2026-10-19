import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer } from 'rxjs';
import { ModalService, ModalState } from './modal.service';

@Component({
  selector: 'app-shared-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bm-modal-backdrop" *ngIf="state.visible" (click)="onBackdropClick($event)">
      <div class="bm-modal" role="dialog" aria-modal="true" [attr.aria-label]="state.title">
        <div class="bm-modal-head">
          <span class="bm-modal-icon" [class.is-confirm]="state.type === 'confirm'">
            <i class="fa-solid" [class.fa-circle-info]="state.type !== 'confirm'"
               [class.fa-triangle-exclamation]="state.type === 'confirm'"></i>
          </span>
          <h3 class="bm-modal-title">{{ state.title }}</h3>
          <button type="button" class="bm-modal-close" aria-label="Close" (click)="onClose()">
            <i class="fa-solid fa-xmark"></i>
          </button>
        </div>

        <div class="bm-modal-body">
          <p [innerText]="state.message"></p>
        </div>

        <div class="bm-modal-foot">
          <ng-container *ngIf="state.type === 'confirm'; else alertActions">
            <button type="button" class="bm-btn bm-btn-outline" (click)="onConfirm(false)">Cancel</button>
            <button type="button" class="bm-btn bm-btn-primary" (click)="onConfirm(true)">Confirm</button>
          </ng-container>
          <ng-template #alertActions>
            <button type="button" class="bm-btn bm-btn-primary bm-btn-block" (click)="onClose()">Got it</button>
          </ng-template>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .bm-modal-backdrop {
      position: fixed;
      inset: 0;
      z-index: 2000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: var(--scrim);
      backdrop-filter: blur(6px);
      -webkit-backdrop-filter: blur(6px);
      animation: bm-fade .2s ease-out;
    }

    .bm-modal {
      width: 100%;
      max-width: 420px;
      overflow: hidden;
      border: 1px solid var(--border-med);
      border-radius: 18px;
      background: linear-gradient(160deg, var(--bg-surface), var(--bg-surface));
      box-shadow: 0 24px 70px rgba(0, 0, 0, .6);
      animation: bm-pop .24s cubic-bezier(.34, 1.4, .64, 1);
    }

    .bm-modal-head {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 20px 22px 14px;
    }

    .bm-modal-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      flex-shrink: 0;
      border-radius: 10px;
      background: rgba(59, 130, 246, .16);
      border: 1px solid rgba(59, 130, 246, .3);
      color: #93c5fd;
      font-size: 1rem;
    }

    .bm-modal-icon.is-confirm {
      background: rgba(245, 158, 11, .14);
      border-color: rgba(245, 158, 11, .32);
      color: #fcd34d;
    }

    .bm-modal-title {
      flex: 1;
      margin: 0;
      font-size: 1.05rem;
      font-weight: 700;
      letter-spacing: -.3px;
      color: var(--text-primary, var(--text-primary));
    }

    .bm-modal-close {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      flex-shrink: 0;
      border: 1px solid var(--border-med);
      border-radius: 9px;
      background: var(--bg-card);
      color: var(--text-secondary, var(--text-secondary));
      font-size: .85rem;
      cursor: pointer;
      transition: all .2s;
    }

    .bm-modal-close:hover {
      color: #fff;
      background: var(--border-med);
    }

    .bm-modal-body {
      padding: 0 22px 20px;
    }

    .bm-modal-body p {
      margin: 0;
      color: var(--text-secondary, var(--text-secondary));
      font-size: .92rem;
      line-height: 1.6;
      white-space: pre-line;
    }

    .bm-modal-foot {
      display: flex;
      gap: 10px;
      justify-content: flex-end;
      padding: 16px 22px;
      border-top: 1px solid var(--border-subtle);
      background: var(--bg-subtle);
    }

    @keyframes bm-fade {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    @keyframes bm-pop {
      from { opacity: 0; transform: translateY(14px) scale(.96); }
      to { opacity: 1; transform: none; }
    }
  `]
})
export class SharedModalComponent implements OnInit, OnDestroy {
  state: ModalState = { visible: false, type: null };
  sub?: Subscription;
  autoCloseSub?: Subscription;

  constructor(private modal: ModalService) {}

  ngOnInit(): void {
    this.sub = this.modal.stateChanges.subscribe(s => {
      this.state = s;
      if (this.autoCloseSub) {
        this.autoCloseSub.unsubscribe();
        this.autoCloseSub = undefined;
      }
      if (s.visible && s.type === 'alert' && s.autoCloseMs) {
        this.autoCloseSub = timer(s.autoCloseMs).subscribe(() => this.onClose());
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.autoCloseSub?.unsubscribe();
  }

  onConfirm(value: boolean) {
    this.modal.resolveConfirm(value);
  }

  /** Only dismiss when the click lands on the backdrop itself, not inside the dialog. */
  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.onClose();
    }
  }

  onClose() {
    this.modal.close();
  }
}
