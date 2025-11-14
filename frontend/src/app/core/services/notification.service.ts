import { Injectable, inject } from '@angular/core';
import { Observable, BehaviorSubject, Subject } from 'rxjs';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzNotificationService } from 'ng-zorro-antd/notification';

export type NotificationType = 'success' | 'info' | 'warning' | 'error';

export interface ToastOptions {
  duration?: number;
  persist?: boolean; // Don't auto-dismiss
}

export interface NotificationAction<T = unknown> {
  id: string;
  label: string;
  callback?: (payload?: T) => void;
  payload?: T;
}

export interface NotificationOptions extends ToastOptions {
  actions?: NotificationAction[];
}

export interface NotificationHistory {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  timestamp: Date;
  read: boolean;
  actions?: NotificationAction[];
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private messageService = inject(NzMessageService);
  private notificationService = inject(NzNotificationService);

  private history$ = new BehaviorSubject<NotificationHistory[]>([]);
  private notificationEvents$ = new Subject<NotificationHistory>();
  private actionEvents$ = new Subject<{ notification: NotificationHistory; action: NotificationAction }>();
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
    options: NotificationOptions = {}
  ): void {
    const { duration = 4500, persist = false, actions } = options;
    const notificationId = `notification-${this.nextId++}`;

    // Add to history (actions will be cloned inside addToHistory)
    this.addToHistory({
      id: notificationId,
      type,
      title,
      message,
      timestamp: new Date(),
      read: false,
      actions
    });

    // Show notification
    this.notificationService.create(type, title, message, {
      nzDuration: persist ? 0 : duration,
      nzKey: notificationId // Use the same ID as history
    });
    // Actions are rendered via NotificationCenterComponent using history stream
  }

  /**
   * Get notification history
   */
  getHistory(): Observable<NotificationHistory[]> {
    return this.history$.asObservable();
  }

  onNotification(): Observable<NotificationHistory> {
    return this.notificationEvents$.asObservable();
  }

  onAction(): Observable<{ notification: NotificationHistory; action: NotificationAction }> {
    return this.actionEvents$.asObservable();
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

  triggerAction(notificationId: string, actionId: string): void {
    const notification = this.history$.value.find(n => n.id === notificationId);
    if (!notification?.actions || notification.actions.length === 0) {
      return;
    }

    const action = notification.actions.find(item => item.id === actionId);
    if (!action) {
      return;
    }

    try {
      action.callback?.(action.payload);
    } catch (error) {
      console.error('Notification action callback threw an error:', error);
    }

    // Mark as read first, then emit action event with updated notification state
    this.markAsRead(notificationId);
    
    // Get the updated notification from history (now marked as read)
    const updatedNotification = this.history$.value.find(n => n.id === notificationId);
    if (updatedNotification) {
      this.actionEvents$.next({ notification: updatedNotification, action });
    }
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
      default:
        // Exhaustive check: if a new NotificationType is added, this will fail at compile time
        const _exhaustive: never = type;
        throw new Error(`Unhandled notification type: ${_exhaustive}`);
    }
  }

  private readonly MAX_HISTORY_ITEMS = 100;

  private addToHistory(notification: NotificationHistory): void {
    const history = this.history$.value;
    const notificationRecord = {
      ...notification,
      actions: notification.actions?.map(action => ({ ...action })),
    };

    this.history$.next([notificationRecord, ...history].slice(0, this.MAX_HISTORY_ITEMS));
    this.notificationEvents$.next(notificationRecord);
  }
}