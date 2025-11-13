import { Injectable, PLATFORM_ID, inject, OnDestroy } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { ModalService } from './modal.service';

export interface KeyboardShortcut {
  key: string;
  ctrlKey?: boolean;
  altKey?: boolean;
  shiftKey?: boolean;
  description: string;
  action: () => void;
}

@Injectable({
  providedIn: 'root'
})
export class KeyboardService implements OnDestroy {
  private shortcuts: KeyboardShortcut[] = [];
  private platformId = inject(PLATFORM_ID);
  private router = inject(Router);
  private modalService = inject(ModalService);
  private keydownListener?: (e: KeyboardEvent) => void;

  shortcutTriggered$ = new Subject<KeyboardShortcut>();

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.initKeyboardListener();
      this.registerDefaultShortcuts();
    }
  }

  ngOnDestroy(): void {
    if (this.keydownListener) {
      document.removeEventListener('keydown', this.keydownListener);
    }
    this.shortcutTriggered$.complete();
  }

  /**
   * Register a keyboard shortcut
   */
  registerShortcut(shortcut: KeyboardShortcut): void {
    const signature = this.getShortcutSignature(shortcut);
    const existingIndex = this.shortcuts.findIndex(s => this.getShortcutSignature(s) === signature);
    if (existingIndex === -1) {
      this.shortcuts.push(shortcut);
    } else {
      // Replace existing shortcut
      this.shortcuts[existingIndex] = shortcut;
    }
  }

  /**
   * Get a normalized signature for shortcut comparison
   */
  private getShortcutSignature(shortcut: KeyboardShortcut): string {
    return `${shortcut.key.toLowerCase()}-${!!shortcut.ctrlKey}-${!!shortcut.altKey}-${!!shortcut.shiftKey}`;
  }

  /**
   * Unregister a shortcut by key combination
   */
  unregisterShortcut(key: string, ctrlKey = false, altKey = false, shiftKey = false): void {
    this.shortcuts = this.shortcuts.filter(s =>
      !(s.key === key && s.ctrlKey === ctrlKey && s.altKey === altKey && s.shiftKey === shiftKey)
    );
  }

  /**
   * Get all registered shortcuts
   */
  getShortcuts(): KeyboardShortcut[] {
    return [...this.shortcuts];
  }

  /**
   * Initialize global keyboard listener
   */
  private initKeyboardListener(): void {
    this.keydownListener = (event) => {
      // Skip if typing in input/textarea
      if (this.isTypingInInput(event.target as HTMLElement)) {
        return;
      }

      const shortcut = this.shortcuts.find(s =>
        s.key.toLowerCase() === event.key.toLowerCase() &&
        Boolean(s.ctrlKey) === (event.ctrlKey || event.metaKey) &&
        !!s.altKey === event.altKey &&
        !!s.shiftKey === event.shiftKey
      );

      if (shortcut) {
        event.preventDefault();
        shortcut.action();
        this.shortcutTriggered$.next(shortcut);
      }
    };
    document.addEventListener('keydown', this.keydownListener);
  }

  /**
   * Register default shortcuts
   */
  private registerDefaultShortcuts(): void {
    // Ctrl+K: Focus search (if exists)
    this.registerShortcut({
      key: 'k',
      ctrlKey: true,
      description: 'Focus search',
      action: () => {
        let searchInput = document.querySelector('[data-shortcut="search"]') as HTMLInputElement;
        if (!searchInput) {
          // Fallback to placeholder lookup
          searchInput = document.querySelector('input[placeholder*="search" i], input[placeholder*="Search" i]') as HTMLInputElement;
        }
        if (searchInput) {
          searchInput.focus();
        }
      }
    });

    // Ctrl+/ : Show shortcuts help
    this.registerShortcut({
      key: '/',
      ctrlKey: true,
      description: 'Show keyboard shortcuts',
      action: () => {
        this.showShortcutsModal();
      }
    });

    // Escape: Close modals/drawers
    this.registerShortcut({
      key: 'Escape',
      description: 'Close modals and drawers',
      action: () => {
        this.modalService.closeAll();
      }
    });

    // Alt+H: Go to dashboard/home
    this.registerShortcut({
      key: 'h',
      altKey: true,
      description: 'Go to dashboard',
      action: () => {
        this.navigateIfRouteExists(['/']);
      }
    });

    // Alt+U: Go to users
    this.registerShortcut({
      key: 'u',
      altKey: true,
      description: 'Go to users',
      action: () => {
        this.navigateIfRouteExists(['/users']);
      }
    });

    // Alt+D: Go to dynamic CRUD
    this.registerShortcut({
      key: 'd',
      altKey: true,
      description: 'Go to dynamic CRUD',
      action: () => {
        this.navigateIfRouteExists(['/dynamic-crud']);
      }
    });
  }

  /**
   * Check if user is typing in an input element
   */
  private isTypingInInput(target: HTMLElement): boolean {
    const tagName = target.tagName.toLowerCase();
    const contentEditable = target.contentEditable === 'true';
    const isInput = tagName === 'input' || tagName === 'textarea' || tagName === 'select';
    return isInput || contentEditable;
  }

  /**
   * Navigate to route if it exists
   */
  private navigateIfRouteExists(route: string[]): void {
    // Simple check: try to navigate and catch if it fails
    try {
      this.router.navigate(route);
    } catch (error) {
      console.warn(`Route ${route.join('/')} not found, skipping navigation`);
    }
  }

  /**
   * Show shortcuts modal (placeholder)
   */
  private showShortcutsModal(): void {
    // TODO: Implement shortcuts modal
    console.log('Keyboard shortcuts:', this.getShortcuts());
  }
}