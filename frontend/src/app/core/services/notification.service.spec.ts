import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { take } from 'rxjs/operators';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzNotificationService } from 'ng-zorro-antd/notification';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let messageSpy: jasmine.SpyObj<NzMessageService>;
  let notificationSpy: jasmine.SpyObj<NzNotificationService>;

  beforeEach(() => {
    messageSpy = jasmine.createSpyObj('NzMessageService', ['create']);
    notificationSpy = jasmine.createSpyObj('NzNotificationService', ['create']);

    TestBed.configureTestingModule({
      providers: [
        NotificationService,
        { provide: NzMessageService, useValue: messageSpy },
        { provide: NzNotificationService, useValue: notificationSpy },
      ],
    });

    service = TestBed.inject(NotificationService);
  });

  it('should store actions on notification and emit history update', async () => {
    service.showNotification('info', 'Example', 'Action required', {
      actions: [
        {
          id: 'acknowledge',
          label: 'Acknowledge',
        },
      ],
    });

    const history = await firstValueFrom(service.getHistory().pipe(take(1)));
    expect(history[0].actions?.length).toBe(1);
    expect(history[0].actions?.[0].id).toBe('acknowledge');
    expect(notificationSpy.create).toHaveBeenCalledWith(
      'info',
      'Example',
      'Action required',
      jasmine.objectContaining({ nzKey: history[0].id })
    );
  });

  it('should invoke action callback and emit action stream when triggered', async () => {
    const callback = jasmine.createSpy('callback');

    service.showNotification('warning', 'Session expires', 'Extend your session?', {
      actions: [
        {
          id: 'extend',
          label: 'Extend',
          callback,
          payload: { minutes: 15 },
        },
      ],
    });

    const history = await firstValueFrom(service.getHistory().pipe(take(1)));
    const notification = history[0];

    const actionEventPromise = firstValueFrom(service.onAction().pipe(take(1)));

    service.triggerAction(notification.id, 'extend');

    const actionEvent = await actionEventPromise;
    expect(callback).toHaveBeenCalledWith({ minutes: 15 });
    expect(actionEvent.notification.id).toBe(notification.id);
    expect(actionEvent.action.id).toBe('extend');
  });
});
