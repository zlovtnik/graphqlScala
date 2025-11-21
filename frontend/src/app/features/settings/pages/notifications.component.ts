import { Component, OnInit, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzMessageModule } from 'ng-zorro-antd/message';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzMessageService } from 'ng-zorro-antd/message';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { SettingsService } from '../../../core/services/settings.service';

/**
 * Notifications settings page for detailed notification preferences.
 */
@Component({
  selector: 'app-settings-notifications',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    NzCardModule,
    NzFormModule,
    NzCheckboxModule,
    NzRadioModule,
    NzButtonModule,
    NzMessageModule,
    NzAlertModule,
    NzDividerModule
  ],
  template: `
    <div class="notifications-settings">
      <nz-alert
        nzType="info"
        nzMessage="Notification Preferences"
        nzDescription="Control how and when you receive notifications about your account activity"
        [nzCloseable]="false"></nz-alert>

      <nz-card nzTitle="Email Notifications">
        <form [formGroup]="emailForm" (ngSubmit)="submitEmailPreferences()">
          <div nz-form-item>
            <label nz-checkbox formControlName="emailEnabled">
              Enable email notifications
            </label>
          </div>

          <div *ngIf="emailForm.get('emailEnabled')?.value" class="notification-options">
            <div nz-form-item>
              <label nz-checkbox formControlName="emailLoginAlerts">
                Alert me of new login attempts
              </label>
            </div>
            <div nz-form-item>
              <label nz-checkbox formControlName="emailSecurityUpdates">
                Notify me of security updates
              </label>
            </div>
            <div nz-form-item>
              <label nz-checkbox formControlName="emailProductUpdates">
                Send me product update announcements
              </label>
            </div>
          </div>

          <button
            nz-button
            nzType="primary"
            [disabled]="!emailForm.dirty || isSavingEmail">
            {{ isSavingEmail ? 'Saving...' : 'Save Email Preferences' }}
          </button>
        </form>
      </nz-card>

      <nz-card nzTitle="Push Notifications">
        <form [formGroup]="pushForm" (ngSubmit)="submitPushPreferences()">
          <div nz-form-item>
            <label nz-checkbox formControlName="pushEnabled">
              Enable push notifications
            </label>
          </div>

          <div *ngIf="pushForm.get('pushEnabled')?.value" class="notification-options">
            <div nz-form-item>
              <label nz-checkbox formControlName="pushLoginAlerts">
                Alert me of new login attempts
              </label>
            </div>
            <div nz-form-item>
              <label nz-checkbox formControlName="pushSecurityUpdates">
                Notify me of security updates
              </label>
            </div>
            <div nz-form-item>
              <label nz-checkbox formControlName="pushMessages">
                New messages and mentions
              </label>
            </div>
          </div>

          <button
            nz-button
            nzType="primary"
            [disabled]="!pushForm.dirty || isSavingPush">
            {{ isSavingPush ? 'Saving...' : 'Save Push Preferences' }}
          </button>
        </form>
      </nz-card>

      <nz-card nzTitle="Account Activity">
        <form [formGroup]="activityForm" (ngSubmit)="submitActivityPreferences()">
          <div nz-form-item>
            <label nz-checkbox formControlName="activityLogins">
              Notify on successful login
            </label>
          </div>
          <div nz-form-item>
            <label nz-checkbox formControlName="activityFailedLogins">
              Notify on failed login attempts
            </label>
          </div>
          <div nz-form-item>
            <label nz-checkbox formControlName="activityApiKeyCreated">
              Notify when API keys are created
            </label>
          </div>
          <div nz-form-item>
            <label nz-checkbox formControlName="activityApiKeyRevoked">
              Notify when API keys are revoked
            </label>
          </div>

          <button
            nz-button
            nzType="primary"
            [disabled]="!activityForm.dirty || isSavingActivity">
            {{ isSavingActivity ? 'Saving...' : 'Save Activity Preferences' }}
          </button>
        </form>
      </nz-card>

      <nz-card nzTitle="Notification Frequency">
        <form [formGroup]="frequencyForm" (ngSubmit)="submitFrequencyPreferences()">
          <div nz-form-item>
            <label nz-form-label>Digest Frequency</label>
            <div nz-form-control>
              <nz-radio-group formControlName="digestFrequency">
                <label nz-radio nzValue="immediate">
                  Immediate
                </label>
                <label nz-radio nzValue="hourly">
                  Hourly
                </label>
                <label nz-radio nzValue="daily">
                  Daily
                </label>
                <label nz-radio nzValue="weekly">
                  Weekly
                </label>
              </nz-radio-group>
            </div>
          </div>

          <button
            nz-button
            nzType="primary"
            [disabled]="!frequencyForm.dirty || isSavingFrequency">
            {{ isSavingFrequency ? 'Saving...' : 'Save Frequency Preferences' }}
          </button>
        </form>
      </nz-card>
    </div>
  `,
  styles: [`
    .notifications-settings {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    nz-card {
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
    }
    [nz-form-item] {
      margin-bottom: 16px;
    }
    .notification-options {
      margin-left: 32px;
      margin-top: 16px;
      padding-left: 16px;
      border-left: 2px solid #f0f0f0;
    }
    button[nz-button] {
      margin-top: 16px;
    }
    label[nz-checkbox],
    label[nz-radio] {
      margin-left: 8px;
    }
  `]
})
export class NotificationsSettingsComponent implements OnInit, OnDestroy {
  emailForm!: FormGroup;
  pushForm!: FormGroup;
  activityForm!: FormGroup;
  frequencyForm!: FormGroup;

  isSavingEmail = false;
  isSavingPush = false;
  isSavingActivity = false;
  isSavingFrequency = false;

  private fb = inject(FormBuilder);
  private msg = inject(NzMessageService);
  private settingsService = inject(SettingsService);
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.emailForm = this.fb.group({
      emailEnabled: [true],
      emailLoginAlerts: [true],
      emailSecurityUpdates: [true],
      emailProductUpdates: [false]
    });

    this.pushForm = this.fb.group({
      pushEnabled: [false],
      pushLoginAlerts: [true],
      pushSecurityUpdates: [true],
      pushMessages: [true]
    });

    this.activityForm = this.fb.group({
      activityLogins: [false],
      activityFailedLogins: [true],
      activityApiKeyCreated: [true],
      activityApiKeyRevoked: [true]
    });

    this.frequencyForm = this.fb.group({
      digestFrequency: ['immediate']
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submitEmailPreferences(): void {
    if (!this.emailForm.valid) {
      return;
    }

    this.isSavingEmail = true;
    const preferences = this.emailForm.value;

    // Call GraphQL mutation
    this.settingsService.updateUserPreferences(preferences)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.msg.success('Email preferences saved');
          this.emailForm.markAsPristine();
          this.isSavingEmail = false;
        },
        error: () => {
          this.msg.error('Failed to save email preferences');
          this.isSavingEmail = false;
        }
      });
  }

  submitPushPreferences(): void {
    if (!this.pushForm.valid) {
      return;
    }

    this.isSavingPush = true;
    const preferences = this.pushForm.value;

    // Call GraphQL mutation
    this.settingsService.updateUserPreferences(preferences)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.msg.success('Push preferences saved');
          this.pushForm.markAsPristine();
          this.isSavingPush = false;
        },
        error: () => {
          this.msg.error('Failed to save push preferences');
          this.isSavingPush = false;
        }
      });
  }

  submitActivityPreferences(): void {
    if (!this.activityForm.valid) {
      return;
    }

    this.isSavingActivity = true;
    const preferences = this.activityForm.value;

    // Call GraphQL mutation
    this.settingsService.updateUserPreferences(preferences)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.msg.success('Activity preferences saved');
          this.activityForm.markAsPristine();
          this.isSavingActivity = false;
        },
        error: () => {
          this.msg.error('Failed to save activity preferences');
          this.isSavingActivity = false;
        }
      });
  }

  submitFrequencyPreferences(): void {
    if (!this.frequencyForm.valid) {
      return;
    }

    this.isSavingFrequency = true;
    const preferences = this.frequencyForm.value;

    // Call GraphQL mutation
    this.settingsService.updateUserPreferences(preferences)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.msg.success('Frequency preferences saved');
          this.frequencyForm.markAsPristine();
          this.isSavingFrequency = false;
        },
        error: () => {
          this.msg.error('Failed to save frequency preferences');
          this.isSavingFrequency = false;
        }
      });
  }
}
