import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'dark' | 'light';

const STORAGE_KEY = 'buyme.theme';

/**
 * Theme is driven by a `data-theme` attribute on <html>, because the whole
 * stylesheet is built from CSS custom properties. Keeping it as an attribute
 * (rather than a class) means the theme can be applied by a tiny inline script
 * before Angular boots, which avoids a flash of the wrong theme.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _theme = signal<ThemeMode>('dark');

  readonly theme = this._theme.asReadonly();

  constructor() {
    // Trust what the DOM already has (set by the inline bootstrap script),
    // fall back to the stored preference, then to the OS preference.
    let mode = this.readDom();
    if (!mode) {
      mode = this.readStored();
    }
    if (!mode) {
      mode = this.prefersLight() ? 'light' : 'dark';
    }
    this._theme.set(mode);
    this.apply(mode);
  }

  isDark(): boolean {
    return this._theme() === 'dark';
  }

  toggle(): void {
    this.set(this._theme() === 'dark' ? 'light' : 'dark');
  }

  set(mode: ThemeMode): void {
    this._theme.set(mode);
    this.apply(mode);
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // private mode / storage disabled — the toggle still works for this session
    }
  }

  private apply(mode: ThemeMode): void {
    const root = document.documentElement;
    root.setAttribute('data-theme', mode);
    // Let the UA style form controls, scrollbars and the like to match.
    root.style.colorScheme = mode;
    const meta = document.querySelector('meta[name="color-scheme"]');
    if (meta) {
      meta.setAttribute('content', mode === 'dark' ? 'dark' : 'light');
    }
  }

  private readDom(): ThemeMode | null {
    const attr = document.documentElement.getAttribute('data-theme');
    return attr === 'light' || attr === 'dark' ? attr : null;
  }

  private readStored(): ThemeMode | null {
    try {
      const v = localStorage.getItem(STORAGE_KEY);
      return v === 'light' || v === 'dark' ? v : null;
    } catch {
      return null;
    }
  }

  private prefersLight(): boolean {
    return typeof window !== 'undefined'
      && typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-color-scheme: light)').matches;
  }
}