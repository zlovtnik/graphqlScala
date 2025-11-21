import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzUploadModule, NzUploadChangeParam, NzUploadFile } from 'ng-zorro-antd/upload';
import { Observable, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzMessageModule } from 'ng-zorro-antd/message';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { AuthService, User } from '../../../core/services/auth.service';
import { NzMessageService } from 'ng-zorro-antd/message';
import { SettingsService } from '../../../core/services/settings.service';

/**
 * Profile settings page for avatar management and password reset.
 */
@Component({
  selector: 'app-settings-profile',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    NzCardModule,
    NzFormModule,
    NzInputModule,
    NzButtonModule,
    NzUploadModule,
    NzAvatarModule,
    NzMessageModule,
    NzModalModule,
    NzIconModule,
    NzDividerModule,
    NzGridModule
  ],
  template: `
    <div class="profile-settings">
      <nz-card nzTitle="Avatar">
        <div class="avatar-section">
          <nz-avatar
            [nzSrc]="avatarUrl"
            [nzSize]="128"
            [nzIcon]="'user'"></nz-avatar>
          <div class="avatar-actions">
            <nz-upload
              nzName="avatar"
              [nzAccept]="'image/*'"
              [nzBeforeUpload]="beforeUpload"
              (nzChange)="handleAvatarChange($event)">
              <button nz-button nzType="primary" [disabled]="isUploading">
                <i nz-icon nzType="upload" nzTheme="outline"></i>
                {{ isUploading ? 'Uploading...' : 'Upload Avatar' }}
              </button>
            </nz-upload>
            <button nz-button (click)="removeAvatar()" [disabled]="!hasAvatar">
              Remove Avatar
            </button>
          </div>
        </div>
      </nz-card>

      <nz-card nzTitle="Profile Information">
        <form [formGroup]="profileForm">
          <div nz-row [nzGutter]="[16, 16]">
            <div nz-col [nzXs]="24" [nzSm]="24" [nzMd]="12">
              <div nz-form-item>
                <label nz-form-label>Username</label>
                <div nz-form-control>
                  <input
                    nz-input
                    formControlName="username"
                    placeholder="Enter username"
                    readonly
                    [value]="currentUser?.username" />
                </div>
              </div>
            </div>
            <div nz-col [nzXs]="24" [nzSm]="24" [nzMd]="12">
              <div nz-form-item>
                <label nz-form-label>Email</label>
                <div nz-form-control>
                  <input
                    nz-input
                    formControlName="email"
                    placeholder="Enter email"
                    readonly
                    [value]="currentUser?.email" />
                </div>
              </div>
            </div>
          </div>
        </form>
      </nz-card>

      <nz-card nzTitle="Password">
        <form [formGroup]="passwordForm" (ngSubmit)="submitPasswordChange()">
          <div nz-form-item>
            <label nz-form-label>Current Password</label>
            <div nz-form-control>
              <input
                nz-input
                type="password"
                formControlName="currentPassword"
                placeholder="Enter current password" />
            </div>
          </div>
          <div nz-form-item>
            <label nz-form-label>New Password</label>
            <div nz-form-control>
              <input
                nz-input
                type="password"
                formControlName="newPassword"
                placeholder="Enter new password" />
            </div>
          </div>
          <div nz-form-item>
            <label nz-form-label>Confirm New Password</label>
            <div nz-form-control>
              <input
                nz-input
                type="password"
                formControlName="confirmPassword"
                placeholder="Confirm new password" />
            </div>
          </div>
          <button nz-button nzType="primary" [disabled]="!passwordForm.valid || isUpdatingPassword">
            {{ isUpdatingPassword ? 'Updating...' : 'Update Password' }}
          </button>
        </form>
      </nz-card>
    </div>
  `,
  styles: [`
    .profile-settings {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    .avatar-section {
      display: flex;
      align-items: center;
      gap: 24px;
    }
    .avatar-actions {
      display: flex;
      flex-direction: column;
      gap: 12px;
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
  `]
})
export class ProfileSettingsComponent implements OnInit, OnDestroy {
  currentUser: User | null = null;
  profileForm!: FormGroup;
  passwordForm!: FormGroup;
  avatarUrl = '';
  hasAvatar = false;
  isUploading = false;
  isUpdatingPassword = false;

  private authService = inject(AuthService);
  private settingsService = inject(SettingsService);
  private fb = inject(FormBuilder);
  private msg = inject(NzMessageService);
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.authService.getCurrentUser$()
      .pipe(takeUntil(this.destroy$))
      .subscribe((user) => {
        this.currentUser = user;
        // Avatar functionality would be added when backend is ready
        // if (user?.avatarKey) {
        //   this.hasAvatar = true;
        //   this.avatarUrl = `/api/avatar/${user.avatarKey}`;
        // }
      });

    this.profileForm = this.fb.group({
      username: [''],
      email: ['']
    });

    this.passwordForm = this.fb.group({
      currentPassword: ['', [Validators.required, Validators.minLength(8)]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required]
    }, { validators: this.passwordMatchValidator });
  }

  beforeUpload = (file: NzUploadFile, _fileList: NzUploadFile[]): boolean | Observable<boolean> => {
    const isImage = file.type?.startsWith('image/');
    const isLt5M = file.size! / 1024 / 1024 < 5;

    if (!isImage) {
      this.msg.error('You can only upload image files');
      return false;
    }

    if (!isLt5M) {
      this.msg.error('Image must be smaller than 5MB');
      return false;
    }

    return true;
  };

  handleAvatarChange(info: NzUploadChangeParam): void {
    if (info.file.status === 'uploading') {
      this.isUploading = true;
    } else if (info.file.status === 'done') {
      this.isUploading = false;
      this.msg.success('Avatar uploaded successfully');
      // Refresh user data from server
      this.authService.getCurrentUser$()
        .pipe(takeUntil(this.destroy$))
        .subscribe();
    } else if (info.file.status === 'error') {
      this.isUploading = false;
      this.msg.error('Failed to upload avatar');
    }
  }

  removeAvatar(): void {
    // Implement avatar removal via GraphQL mutation
    this.msg.info('Avatar removal coming soon');
  }

  submitPasswordChange(): void {
    if (!this.passwordForm.valid) {
      return;
    }

    this.isUpdatingPassword = true;
    const { currentPassword, newPassword } = this.passwordForm.value;

    // Call GraphQL mutation to update password
    this.settingsService.updatePassword(currentPassword, newPassword)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.msg.success('Password updated successfully');
          this.passwordForm.reset();
          this.isUpdatingPassword = false;
        },
        error: () => {
          this.msg.error('Failed to update password');
          this.isUpdatingPassword = false;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private passwordMatchValidator(group: FormGroup): { [key: string]: any } | null {
    const password = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return password === confirm ? null : { passwordMismatch: true };
  }
}
