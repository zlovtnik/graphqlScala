import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzListModule } from 'ng-zorro-antd/list';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  NotificationAction,
  NotificationHistory,
  NotificationService,
} from '../../core/services/notification.service';

@Component({
  selector: 'app-notification-center',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    NzBadgeModule,
    NzButtonModule,
    NzDropDownModule,
    NzIconModule,
    NzListModule,
  ],
  styles: [
    `
      .notification-menu {
        max-width: 320px;
        padding: 0 12px;
      }

      .notification-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 8px 0;
        font-weight: 600;
      }

      .notification-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        margin-top: 8px;
      }

      .notification-empty {
        padding: 16px 0;
        text-align: center;
        color: rgba(0, 0, 0, 0.45);
      }

      .notification-content {
        display: block;
      }
    `,
  ],
  template: `
    <div nz-dropdown [nzDropdownMenu]="menu" nzTrigger="click" nzPlacement="bottomRight" aria-label="Notification center dropdown">
      <button nz-button nzType="text" nzShape="circle" class="notification-trigger" aria-label="Open notifications">
        <nz-badge [nzCount]="(unreadCount$ | async) || 0" [nzOverflowCount]="99">
          <span nz-icon nzType="bell" nzTheme="outline" aria-hidden="true"></span>
        </nz-badge>
      </button>
    </div>

    <nz-dropdown-menu #menu="nzDropdownMenu" class="notification-menu" role="menu" aria-label="Notifications menu">
      <div class="notification-header">
        <span id="notifications-title">Notifications</span>
        <button
          nz-button
          nzType="link"
          *ngIf="(notifications$ | async)?.length"
          (click)="clearAll($event)"
          aria-label="Clear all notifications"
        >
          Clear all
        </button>
      </div>

      <ng-container *ngIf="(notifications$ | async) as notifications; else emptyState">
        <nz-list [nzDataSource]="notifications" nzItemLayout="vertical" [attr.aria-labelledby]="'notifications-title'">
          <nz-list-item *ngFor="let notification of notifications; trackBy: trackByNotification">
            <nz-list-item-meta
              [nzTitle]="notification.title"
              [nzDescription]="notification.message"
            ></nz-list-item-meta>
            <ng-container *ngIf="notification.actions?.length">
              <div class="notification-actions" [attr.aria-label]="'Actions for ' + notification.title">
                <button
                  *ngFor="let action of notification.actions; trackBy: trackByAction"
                  nz-button
                  nzSize="small"
                  (click)="handleAction(notification, action, $event)"
                  [attr.aria-label]="action.label"
                >
                  {{ action.label }}
                </button>
              </div>
            </ng-container>
          </nz-list-item>
        </nz-list>
      </ng-container>

      <ng-template #emptyState>
        <div class="notification-empty" role="status" aria-live="polite">No notifications yet</div>
      </ng-template>
    </nz-dropdown-menu>
  `,
})
export class NotificationCenterComponent {
  private readonly notificationService = inject(NotificationService);

  readonly notifications$: Observable<NotificationHistory[]> = this.notificationService
    .getHistory();

  readonly unreadCount$: Observable<number> = this.notifications$.pipe(
    map((notifications) => notifications.filter((item) => !item.read).length)
  );

  /**
   * TrackBy function for notification list rendering.
   * Identifies notifications by their unique ID to optimize change detection.
   */
  trackByNotification(index: number, item: NotificationHistory): string | number {
    return item.id ?? index;
  }

  /**
   * TrackBy function for notification actions rendering.
   * Identifies actions by their unique ID to optimize change detection.
   */
  trackByAction(index: number, item: NotificationAction): string | number {
    return item.id ?? index;
  }

  clearAll(event: MouseEvent): void {
    event.stopPropagation();
    this.notificationService.clearHistory();
  }

  handleAction(
    notification: NotificationHistory,
    action: NotificationAction,
    event: MouseEvent
  ): void {
    event.stopPropagation();
    this.notificationService.triggerAction(notification.id, action.id);
  }
}
