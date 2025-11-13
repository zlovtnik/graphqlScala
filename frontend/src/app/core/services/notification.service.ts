import { Injectable, inject } from '@angular/core';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzNotificationService } from 'ng-zorro-antd/notification';
import { BehaviorSubject } from 'rxjs';

export type NotificationType = 'success' | 'info' | 'warning' | 'error';

export interface ToastOptions {
  duration?: number;
  persist?: boolean; // Don't auto-dismiss
}

export interface NotificationHistory {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  timestamp: Date;
  read: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private messageService = inject(NzMessageService);
  private notificationService = inject(NzNotificationService);

  private history$ = new BehaviorSubject<NotificationHistory[]>([]);
  private nextId = 1;

  /**
   * Show a toast message
   */
  showToast(
    type: NotificationType,
    message: string,
    options: ToastOptions = {}
  ): void {
    const { duration = 3000, persist = false } = options;

    // Add to history
    this.addToHistory({
      id: `toast-${this.nextId++}`,
      type,
      title: this.getTypeTitle(type),
      message,
      timestamp: new Date(),
      read: false
    });

    // Show toast
    this.messageService.create(type, message, {
      nzDuration: persist ? 0 : duration
    });
  }

  /**
   * Show a notification (more prominent)
   */
  showNotification(
    type: NotificationType,
    title: string,
    message: string,
    options: ToastOptions = {}
  ): void {
    const { duration = 4500, persist = false } = options;

    // Add to history
    this.addToHistory({
      id: `notification-${this.nextId++}`,
      type,
      title,
      message,
      timestamp: new Date(),
      read: false
    });

    // Show notification
    this.notificationService.create(type, title, message, {
      nzDuration: persist ? 0 : duration,
      nzKey: `notification-${this.nextId - 1}` // Use the same ID as history
    });
    // Note: Action buttons not implemented yet
  }

  /**
   * Get notification history
   */
  getHistory() {
    return this.history$.asObservable();
  }

  /**
   * Mark notification as read
   */
  markAsRead(id: string): void {
    const history = this.history$.value;
    const index = history.findIndex(n => n.id === id);
    if (index !== -1) {
      const updatedHistory = [
        ...history.slice(0, index),
        { ...history[index], read: true },
        ...history.slice(index + 1)
      ];
      this.history$.next(updatedHistory);
    }
  }

  /**
   * Clear all notifications
   */
  clearHistory(): void {
    this.history$.next([]);
  }

  /**
   * Get unread count
   */
  getUnreadCount(): number {
    return this.history$.value.filter(n => !n.read).length;
  }

  /**
   * Convenience methods
   */
  success(message: string, options?: ToastOptions): void {
    this.showToast('success', message, options);
  }

  error(message: string, options?: ToastOptions): void {
    this.showToast('error', message, options);
  }

  warning(message: string, options?: ToastOptions): void {
    this.showToast('warning', message, options);
  }

  info(message: string, options?: ToastOptions): void {
    this.showToast('info', message, options);
  }

  private getTypeTitle(type: NotificationType): string {
    switch (type) {
      case 'success': return 'Success';
      case 'error': return 'Error';
      case 'warning': return 'Warning';
      case 'info': return 'Info';
    }
  }

  private readonly MAX_HISTORY_ITEMS = 100;

  private addToHistory(notification: NotificationHistory): void {
    const history = this.history$.value;
    this.history$.next([notification, ...history].slice(0, this.MAX_HISTORY_ITEMS));
  }
}