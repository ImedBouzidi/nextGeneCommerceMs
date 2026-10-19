import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ModalType = 'alert' | 'confirm' | null;

export interface ModalState {
  visible: boolean;
  type: ModalType;
  title?: string;
  message?: string;
  autoCloseMs?: number | null;
}

@Injectable({ providedIn: 'root' })
export class ModalService {
  private state$ = new BehaviorSubject<ModalState>({ visible: false, type: null });
  stateChanges = this.state$.asObservable();

  private confirmResolve?: (v: boolean) => void;

  showAlert(message: string, title = 'Notice', autoCloseMs: number | null = 3000) {
    this.state$.next({ visible: true, type: 'alert', title, message, autoCloseMs });
  }

  async showConfirm(message: string, title = 'Please confirm'): Promise<boolean> {
    this.state$.next({ visible: true, type: 'confirm', title, message, autoCloseMs: null });
    return new Promise<boolean>((resolve) => {
      this.confirmResolve = resolve;
    });
  }

  // Called by modal component when user responds
  resolveConfirm(result: boolean) {
    if (this.confirmResolve) {
      this.confirmResolve(result);
      this.confirmResolve = undefined;
    }
    this.close();
  }

  close() {
    this.state$.next({ visible: false, type: null });
  }
}
