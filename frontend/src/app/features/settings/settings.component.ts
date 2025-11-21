import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzLayoutModule } from 'ng-zorro-antd/layout';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';

/**
 * Settings container component with sub-routes for:
 * - Profile (avatar, user info, password reset)
 * - Preferences (theme, language, notifications)
 * - API Keys (lifecycle management)
 * - Notifications (detailed notification preferences)
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    NzLayoutModule,
    NzMenuModule,
    NzButtonModule,
    NzIconModule
  ],
  template: `
    <nz-layout class="settings-layout">
      <nz-sider [nzWidth]="250" nzTheme="light" [nzCollapsible]="true">
        <div class="settings-logo">
          <h3>Settings</h3>
        </div>
        <ul nz-menu nzMode="inline" nzSelectedKeys="['profile']">
          <li nz-menu-item
              nzIcon="user"
              nzMenuItemKey="profile"
              [routerLink]="['profile']">
            Profile
          </li>
          <li nz-menu-item
              nzIcon="bg-colors"
              nzMenuItemKey="preferences"
              [routerLink]="['preferences']">
            Preferences
          </li>
          <li nz-menu-item
              nzIcon="key"
              nzMenuItemKey="api-keys"
              [routerLink]="['api-keys']">
            API Keys
          </li>
          <li nz-menu-item
              nzIcon="bell"
              nzMenuItemKey="notifications"
              [routerLink]="['notifications']">
            Notifications
          </li>
        </ul>
      </nz-sider>
      <nz-layout>
        <nz-content class="settings-content">
          <router-outlet></router-outlet>
        </nz-content>
      </nz-layout>
    </nz-layout>
  `,
  styles: [`
    .settings-layout {
      min-height: calc(100vh - 64px);
    }
    .settings-logo {
      padding: 24px;
      border-bottom: 1px solid #f0f0f0;
    }
    .settings-logo h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
    }
    .settings-content {
      padding: 24px;
      background: #fafafa;
    }
    nz-sider {
      background: #fff;
    }
  `]
})
export class SettingsComponent implements OnInit {
  activeTab = 'profile';
  private router = inject(Router);

  ngOnInit(): void {
    // Navigate to profile by default
    if (this.router.url === '/settings') {
      this.router.navigate(['/settings/profile']);
    }
  }
}
