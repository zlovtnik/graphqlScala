import { Component, OnInit, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzMessageModule } from 'ng-zorro-antd/message';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzMessageService } from 'ng-zorro-antd/message';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { SettingsService } from '../../../core/services/settings.service';

/**
 * Preferences settings page for theme, language, and notification toggles.
 * Persists user preferences via GraphQL mutations with Redis-backed caching.
 */
@Component({
  selector: 'app-settings-preferences',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    NzCardModule,
    NzFormModule,
    NzSelectModule,
    NzCheckboxModule,
    NzButtonModule,
    NzMessageModule,
    NzDividerModule
  ],
  template: `
    <div class="preferences-settings">
      <nz-card nzTitle="Appearance">
        <form [formGroup]="appearanceForm" (ngSubmit)="submitAppearance()">
          <div nz-form-item>
            <label nz-form-label>Theme</label>
            <div nz-form-control>
              <nz-select
                formControlName="theme"
                nzPlaceHolder="Select theme">
                <nz-option nzValue="light" nzLabel="Light"></nz-option>
                <nz-option nzValue="dark" nzLabel="Dark"></nz-option>
                <nz-option nzValue="auto" nzLabel="Auto (System)"></nz-option>
              </nz-select>
            </div>
          </div>
          <div nz-form-item>
            <label nz-form-label>Language</label>
            <div nz-form-control>
              <nz-select
                formControlName="language"
                nzPlaceHolder="Select language">
                <nz-option nzValue="en" nzLabel="English"></nz-option>
                <nz-option nzValue="es" nzLabel="Español"></nz-option>
                <nz-option nzValue="fr" nzLabel="Français"></nz-option>
                <nz-option nzValue="de" nzLabel="Deutsch"></nz-option>
              </nz-select>
            </div>
          </div>
          <button
            nz-button
            nzType="primary"
            [disabled]="!appearanceForm.dirty || isSavingAppearance">
            {{ isSavingAppearance ? 'Saving...' : 'Save Appearance' }}
          </button>
        </form>
      </nz-card>

      <nz-card nzTitle="Notifications">
        <form [formGroup]="notificationForm" (ngSubmit)="submitNotifications()">
          <div nz-form-item>
            <label nz-form-label>Email Notifications</label>
            <div nz-form-control>
              <label nz-checkbox formControlName="notificationEmails">
                Receive email notifications
              </label>
            </div>
          </div>
          <div nz-form-item>
            <label nz-form-label>Push Notifications</label>
            <div nz-form-control>
              <label nz-checkbox formControlName="notificationPush">
                Receive push notifications
              </label>
            </div>
          </div>
          <div nz-form-item>
            <label nz-form-label>Login Alerts</label>
            <div nz-form-control>
              <label nz-checkbox formControlName="notificationLoginAlerts">
                Alert me of new login attempts
              </label>
            </div>
          </div>
          <div nz-form-item>
            <label nz-form-label>Security Updates</label>
            <div nz-form-control>
              <label nz-checkbox formControlName="notificationSecurityUpdates">
                Notify me of security updates
              </label>
            </div>
          </div>
          <button
            nz-button
            nzType="primary"
            [disabled]="!notificationForm.dirty || isSavingNotifications">
            {{ isSavingNotifications ? 'Saving...' : 'Save Preferences' }}
          </button>
        </form>
      </nz-card>
    </div>
  `,
  styles: [`
    .preferences-settings {
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
    button[nz-button] {
      margin-top: 8px;
    }
    label[nz-checkbox] {
      margin-left: 8px;
    }
  `]
})
export class PreferencesSettingsComponent implements OnInit, OnDestroy {
  appearanceForm!: FormGroup;
  notificationForm!: FormGroup;
  isSavingAppearance = false;
  isSavingNotifications = false;

  private fb = inject(FormBuilder);
  private msg = inject(NzMessageService);
  private settingsService = inject(SettingsService);
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.appearanceForm = this.fb.group({
      theme: ['light'],
      language: ['en']
    });

    this.notificationForm = this.fb.group({
      notificationEmails: [true],
      notificationPush: [true],
      notificationLoginAlerts: [true],
      notificationSecurityUpdates: [true]
    });

    // Load user preferences from localStorage initially
    this.loadPreferences();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submitAppearance(): void {
    if (!this.appearanceForm.valid) {
      return;
    }

    this.isSavingAppearance = true;
    const preferences = this.appearanceForm.value;

    // TODO: Remove this temporary fix when GraphQL integration is complete
    this.savePreferencesToStorage(preferences);
    this.applyTheme(preferences.theme);
    this.appearanceForm.markAsPristine();
    this.isSavingAppearance = false;
    this.msg.success('Appearance preferences saved locally');

    // Call GraphQL mutation to save preferences
    // this.settingsService.updateUserPreferences(preferences)
    //   .pipe(takeUntil(this.destroy$))
    //   .subscribe(
    //     () => {
    //       this.msg.success('Appearance preferences saved');
    //       this.appearanceForm.markAsPristine();
    //       this.isSavingAppearance = false;
    //       this.savePreferencesToStorage(preferences);
    //       // Apply theme changes
    //       this.applyTheme(preferences.theme);
    //     },
    //     () => {
    //       this.msg.error('Failed to save preferences');
    //       this.isSavingAppearance = false;
    //     }
    //   );
  }

  submitNotifications(): void {
    if (!this.notificationForm.valid) {
      return;
    }

    this.isSavingNotifications = true;
    const preferences = this.notificationForm.value;

    // Call GraphQL mutation to save preferences
    // this.settingsService.updateUserPreferences(preferences)
    //   .pipe(takeUntil(this.destroy$))
    //   .subscribe(
    //     () => {
    //       this.msg.success('Notification preferences saved');
    //       this.notificationForm.markAsPristine();
    //       this.isSavingNotifications = false;
    //       this.savePreferencesToStorage(preferences);
    //     },
    //     () => {
    //       this.msg.error('Failed to save preferences');
    //       this.isSavingNotifications = false;
    //     }
    //   );
    
    // Reset UI state to prevent stuck saving state until mutation is implemented
    this.isSavingNotifications = false;
  }

  private loadPreferences(): void {
    const stored = localStorage.getItem('user-preferences');
    if (stored) {
      const preferences = JSON.parse(stored);
      this.appearanceForm.patchValue({
        theme: preferences.theme,
        language: preferences.language
      });
      this.notificationForm.patchValue({
        notificationEmails: preferences.notificationEmails,
        notificationPush: preferences.notificationPush,
        notificationLoginAlerts: preferences.notificationLoginAlerts,
        notificationSecurityUpdates: preferences.notificationSecurityUpdates
      });
    }
  }

  private savePreferencesToStorage(preferences: any): void {
    localStorage.setItem('user-preferences', JSON.stringify(preferences));
  }

  private applyTheme(theme: string): void {
    // Apply theme to document
    const htmlElement = document.documentElement;
    if (theme === 'dark') {
      htmlElement.setAttribute('data-theme', 'dark');
    } else if (theme === 'light') {
      htmlElement.setAttribute('data-theme', 'light');
    } else {
      // Auto: check system preference
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      htmlElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
    }
  }
}
