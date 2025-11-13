import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { BehaviorSubject } from 'rxjs';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform?: string }>;
}

@Injectable({
  providedIn: 'root'
})
export class PwaService {
  private deferredPrompt: BeforeInstallPromptEvent | null = null;
  private installPrompt$ = new BehaviorSubject<boolean>(false);
  private platformId = inject(PLATFORM_ID);

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.initPwaPrompt();
    }
  }

  private initPwaPrompt(): void {
    window.addEventListener('beforeinstallprompt', (e) => {
      // Prevent the mini-infobar from appearing on mobile
      e.preventDefault();
      // Stash the event so it can be triggered later
      this.deferredPrompt = e as BeforeInstallPromptEvent;
      // Update UI to notify the user they can install the PWA
      this.installPrompt$.next(true);
    });

    window.addEventListener('appinstalled', () => {
      // Hide the app-provided install promotion
      this.installPrompt$.next(false);
      // Clear the deferredPrompt so it can be garbage collected
      this.deferredPrompt = null;
    });
  }

  get installPromptVisible$() {
    return this.installPrompt$.asObservable();
  }

  async installPwa(): Promise<void> {
    if (!this.deferredPrompt) {
      return;
    }

    // Show the install prompt
    this.deferredPrompt.prompt();

    // Wait for the user to respond to the prompt
    await this.deferredPrompt.userChoice;

    // We've used the prompt, and can't use it again, throw it away
    this.deferredPrompt = null;
    this.installPrompt$.next(false);
  }
}