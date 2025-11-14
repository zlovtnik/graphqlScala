import { Injectable, PLATFORM_ID, inject, OnDestroy } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { ModalService } from './modal.service';
import { NzModalService } from 'ng-zorro-antd/modal';
import { ShortcutsModalComponent } from '../../shared/components/shortcuts-modal.component';

export interface KeyboardShortcut {
  key: string;
  ctrlKey?: boolean;
  altKey?: boolean;
  shiftKey?: boolean;
  description: string;
  action: () => void;
}

@Injectable({
  providedIn: 'root',
})
export class KeyboardService implements OnDestroy {
  private shortcuts: KeyboardShortcut[] = [];
  private platformId = inject(PLATFORM_ID);
  private router = inject(Router);
  private modalService = inject(ModalService);
  private nzModalService = inject(NzModalService);
  private keydownListener?: (e: KeyboardEvent) => void;
  private searchInputRef?: HTMLInputElement;

  shortcutTriggered$ = new Subject<KeyboardShortcut>();

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.initKeyboardListener();
      this.registerDefaultShortcuts();
    }
  }

  /**
   * Register a search input element to receive Ctrl+K focus events.
   * The service will focus this input when Ctrl+K is pressed (if the input is not already focused).
   *
   * @param input The HTMLInputElement to register, or null to clear the current registration
   * @returns A cleanup function that unregisters the search target when called
   */
  registerSearchTarget(input: HTMLInputElement | null): (() => void) {
    if (input && input instanceof HTMLInputElement) {
      this.searchInputRef = input;
    } else {
      this.searchInputRef = undefined;
    }

    // Return cleanup function for explicit unregistration
    return () => this.unregisterSearchTarget();
  }

  /**
   * Unregister the search target and clear all references.
   * This ensures event listeners and DOM references are properly released.
   */
  unregisterSearchTarget(): void {
    this.searchInputRef = undefined;
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
    const existingIndex = this.shortcuts.findIndex(
      (s) => this.getShortcutSignature(s) === signature
    );
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
    return `${shortcut.key.toLowerCase()}-${Boolean(
      shortcut.ctrlKey
    )}-${Boolean(shortcut.altKey)}-${Boolean(shortcut.shiftKey)}`;
  }

  /**
   * Unregister a shortcut by key combination
   */
  unregisterShortcut(
    key: string,
    ctrlKey = false,
    altKey = false,
    shiftKey = false
  ): void {
    const normalizedKey = key.toLowerCase();
    const normalizedCtrl = Boolean(ctrlKey);
    const normalizedAlt = Boolean(altKey);
    const normalizedShift = Boolean(shiftKey);

    this.shortcuts = this.shortcuts.filter(
      (s) =>
        !(
          s.key.toLowerCase() === normalizedKey &&
          Boolean(s.ctrlKey) === normalizedCtrl &&
          Boolean(s.altKey) === normalizedAlt &&
          Boolean(s.shiftKey) === normalizedShift
        )
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
      if (
        event.target instanceof HTMLElement &&
        this.isTypingInInput(event.target)
      ) {
        return;
      }

      const normalizedKey = event.key.toLowerCase();
      const ctrlPressed = Boolean(event.ctrlKey || event.metaKey);
      const altPressed = Boolean(event.altKey);
      const shiftPressed = Boolean(event.shiftKey);

      const shortcut = this.shortcuts.find(
        (s) =>
          s.key.toLowerCase() === normalizedKey &&
          Boolean(s.ctrlKey) === ctrlPressed &&
          Boolean(s.altKey) === altPressed &&
          Boolean(s.shiftKey) === shiftPressed
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
        const target = this.searchInputRef;
        if (target && typeof target.focus === 'function') {
          target.focus();
          return;
        }

        let searchInput = document.querySelector(
          '[data-shortcut="search"]'
        ) as HTMLInputElement;
        if (!searchInput) {
          searchInput = document.querySelector(
            'input[placeholder*="search" i], input[placeholder*="Search" i]'
          ) as HTMLInputElement;
        }
        if (searchInput && typeof searchInput.focus === 'function') {
          searchInput.focus();
        }
      },
    });

    // Ctrl+/ : Show shortcuts help
    this.registerShortcut({
      key: '/',
      ctrlKey: true,
      description: 'Show keyboard shortcuts',
      action: () => {
        this.showShortcutsModal();
      },
    });

    // Escape: Close modals/drawers
    this.registerShortcut({
      key: 'Escape',
      description: 'Close modals and drawers',
      action: () => {
        this.modalService.closeAll();
      },
    });

    // Alt+H: Go to dashboard/home
    this.registerShortcut({
      key: 'h',
      altKey: true,
      description: 'Go to dashboard',
      action: () => {
        this.navigateIfRouteExists(['/']);
      },
    });

    // Alt+U: Go to users
    this.registerShortcut({
      key: 'u',
      altKey: true,
      description: 'Go to users',
      action: () => {
        this.navigateIfRouteExists(['/users']);
      },
    });

    // Alt+D: Go to dynamic CRUD
    this.registerShortcut({
      key: 'd',
      altKey: true,
      description: 'Go to dynamic CRUD',
      action: () => {
        this.navigateIfRouteExists(['/dynamic-crud']);
      },
    });
  }

  /**
   * Check if user is typing in an input element
   */
  private isTypingInInput(target: EventTarget | null): boolean {
    if (!(target instanceof HTMLElement)) {
      return false;
    }
    const tagName = target.tagName.toLowerCase();
    const contentEditable = target.contentEditable === 'true';
    const isInput =
      tagName === 'input' || tagName === 'textarea' || tagName === 'select';
    return isInput || contentEditable;
  }

  /**
   * Navigate to route if it exists
   */
  private async navigateIfRouteExists(route: string[]): Promise<void> {
    try {
      const success = await this.router.navigate(route);
      if (!success) {
        console.warn(
          `Navigation blocked or route missing for path: ${route.join('/')}`
        );
      }
    } catch (error) {
      console.warn(
        `Route ${route.join('/')} not found, skipping navigation`,
        error
      );
    }
  }

  /**
   * Show shortcuts modal (placeholder)
   */
  private showShortcutsModal(): void {
    this.nzModalService.create({
      nzTitle: 'Keyboard Shortcuts',
      nzContent: ShortcutsModalComponent,
      nzData: this.getShortcuts(),
      nzFooter: null,
      nzWidth: 600,
    });
  }
}
