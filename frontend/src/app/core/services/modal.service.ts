import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ModalService {
  private closeHandlers: (() => void)[] = [];

  /**
   * Register a close handler for modals/drawers
   */
  registerCloseHandler(handler: () => void): void {
    this.closeHandlers.push(handler);
  }

  /**
   * Unregister a close handler
   */
  unregisterCloseHandler(handler: () => void): void {
    const index = this.closeHandlers.indexOf(handler);
    if (index > -1) {
      this.closeHandlers.splice(index, 1);
    }
  }

  /**
   * Close all registered modals/drawers
   */
  closeAll(): void {
    this.closeHandlers.forEach(handler => handler());
    this.closeHandlers = []; // Clear after closing to avoid stale handlers
  }
}