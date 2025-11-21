import { Component, OnInit, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzMessageModule } from 'ng-zorro-antd/message';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalService } from 'ng-zorro-antd/modal';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

interface ApiKey {
  id: number;
  keyName: string;
  keyPreview: string;
  lastUsedAt?: number;
  revokedAt?: number;
  expiresAt?: number;
  createdAt: number;
  isActive: boolean;
}

/**
 * API Keys management page for lifecycle management.
 * Allows users to generate, revoke, and delete API keys.
 */
@Component({
  selector: 'app-settings-api-keys',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    NzCardModule,
    NzFormModule,
    NzInputModule,
    NzButtonModule,
    NzTableModule,
    NzModalModule,
    NzMessageModule,
    NzIconModule,
    NzBadgeModule,
    NzDividerModule,
    NzDatePickerModule
  ],
  template: `
    <div class="api-keys-settings">
      <nz-card nzTitle="Generate New API Key">
        <form [formGroup]="generateForm" (ngSubmit)="submitGenerateKey()">
          <div nz-form-item>
            <label nz-form-label>Key Name</label>
            <div nz-form-control>
              <input
                nz-input
                formControlName="keyName"
                placeholder="e.g., Production API"
                required />
            </div>
          </div>
          <div nz-form-item>
            <label nz-form-label>Expires In (days)</label>
            <div nz-form-control>
              <input
                nz-input
                type="number"
                formControlName="expiresInDays"
                placeholder="e.g., 90"
                min="1"
                max="365" />
            </div>
          </div>
          <button
            nz-button
            nzType="primary"
            [disabled]="!generateForm.valid || isGenerating">
            {{ isGenerating ? 'Generating...' : 'Generate API Key' }}
          </button>
        </form>
      </nz-card>

      <nz-card nzTitle="Your API Keys">
        <nz-table
          #basicTable
          [nzData]="apiKeys"
          [nzFrontPagination]="true"
          [nzPageSize]="10"
          [nzShowSizeChanger]="true">
          <thead>
            <tr>
              <th>Name</th>
              <th>Key</th>
              <th>Status</th>
              <th>Last Used</th>
              <th>Expires</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let key of apiKeys">
              <td>{{ key.keyName }}</td>
              <td>
                <code>{{ key.keyPreview }}</code>
              </td>
              <td>
                <nz-badge
                  [nzStatus]="key.isActive ? 'success' : 'error'"
                  [nzText]="key.isActive ? 'Active' : 'Revoked'"></nz-badge>
              </td>
              <td>{{ key.lastUsedAt ? (key.lastUsedAt | date : 'short') : 'Never' }}</td>
              <td>{{ key.expiresAt ? (key.expiresAt | date : 'short') : 'Never' }}</td>
              <td>
                <button
                  nz-button
                  nzType="link"
                  nzDanger
                  (click)="revokeKey(key)"
                  [disabled]="!key.isActive">
                  Revoke
                </button>
                <nz-divider nzType="vertical"></nz-divider>
                <button
                  nz-button
                  nzType="link"
                  nzDanger
                  (click)="deleteKey(key)">
                  Delete
                </button>
              </td>
            </tr>
          </tbody>
        </nz-table>
        <div *ngIf="apiKeys.length === 0" class="empty-state">
          <p>No API keys yet. Generate one to get started.</p>
        </div>
      </nz-card>

      <!-- Modal for showing newly generated key -->
      <nz-modal
        [(nzVisible)]="showNewKeyModal"
        nzTitle="API Key Generated"
        nzOkText="Copy"
        nzCancelText="Done"
        (nzOnOk)="copyKeyToClipboard()"
        (nzOnCancel)="closeNewKeyModal()">
        <div class="new-key-modal">
          <p>
            <strong>⚠️ Save this key somewhere safe. You won't be able to see it again.</strong>
          </p>
          <div class="key-display">
            <code>{{ newGeneratedKey }}</code>
            <button nz-button nzType="text" (click)="copyKeyToClipboard()">
              <i nz-icon nzType="copy" nzTheme="outline"></i>
            </button>
          </div>
        </div>
      </nz-modal>
    </div>
  `,
  styles: [`
    .api-keys-settings {
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
    code {
      background: #f5f5f5;
      padding: 4px 8px;
      border-radius: 4px;
      font-family: monospace;
    }
    .empty-state {
      text-align: center;
      padding: 40px;
      color: rgba(0, 0, 0, 0.45);
    }
    .key-display {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      background: #f5f5f5;
      border-radius: 4px;
      margin-top: 16px;
    }
    .key-display code {
      flex: 1;
      margin: 0;
      overflow-x: auto;
    }
  `]
})
export class ApiKeysSettingsComponent implements OnInit, OnDestroy {
  generateForm!: FormGroup;
  apiKeys: ApiKey[] = [];
  isGenerating = false;
  showNewKeyModal = false;
  newGeneratedKey = '';

  private fb = inject(FormBuilder);
  private msg = inject(NzMessageService);
  private modal = inject(NzModalService);
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.generateForm = this.fb.group({
      keyName: ['', [Validators.required, Validators.minLength(3)]],
      expiresInDays: [90, [Validators.required, Validators.min(1), Validators.max(365)]]
    });

    // Load API keys
    this.loadApiKeys();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submitGenerateKey(): void {
    if (!this.generateForm.valid) {
      return;
    }

    this.isGenerating = true;
    const formData = this.generateForm.value;

    // Call GraphQL mutation to generate API key
    // this.settingsService.generateApiKey(formData)
    //   .pipe(takeUntil(this.destroy$))
    //   .subscribe(
    //     (response) => {
    //       this.msg.success('API Key generated successfully');
    //       this.newGeneratedKey = response.rawKey;
    //       this.showNewKeyModal = true;
    //       this.generateForm.reset({ expiresInDays: 90 });
    //       this.isGenerating = false;
    //       this.loadApiKeys();
    //     },
    //     () => {
    //       this.msg.error('Failed to generate API Key');
    //       this.isGenerating = false;
    //     }
    //   );
  }

  revokeKey(key: ApiKey): void {
    this.modal.confirm({
      nzTitle: 'Revoke API Key?',
      nzContent: `Are you sure you want to revoke "${key.keyName}"? Applications using this key will stop working.`,
      nzOkText: 'Revoke',
      nzOkType: 'primary',
      nzOkDanger: true,
      nzCancelText: 'Cancel',
      nzOnOk: () => {
        // Call GraphQL mutation to revoke API key
        // this.settingsService.revokeApiKey(key.id)
        //   .pipe(takeUntil(this.destroy$))
        //   .subscribe(
        //     () => {
        //       this.msg.success('API Key revoked');
        //       this.loadApiKeys();
        //     },
        //     () => {
        //       this.msg.error('Failed to revoke API Key');
        //     }
        //   );
      }
    });
  }

  deleteKey(key: ApiKey): void {
    this.modal.confirm({
      nzTitle: 'Delete API Key?',
      nzContent: `Are you sure you want to permanently delete "${key.keyName}"? This action cannot be undone.`,
      nzOkText: 'Delete',
      nzOkType: 'primary',
      nzOkDanger: true,
      nzCancelText: 'Cancel',
      nzOnOk: () => {
        // Call GraphQL mutation to delete API key
        // this.settingsService.deleteApiKey(key.id)
        //   .pipe(takeUntil(this.destroy$))
        //   .subscribe(
        //     () => {
        //       this.msg.success('API Key deleted');
        //       this.loadApiKeys();
        //     },
        //     () => {
        //       this.msg.error('Failed to delete API Key');
        //     }
        //   );
      }
    });
  }

  copyKeyToClipboard(): void {
    navigator.clipboard.writeText(this.newGeneratedKey).then(() => {
      this.msg.success('API Key copied to clipboard');
    });
  }

  closeNewKeyModal(): void {
    this.showNewKeyModal = false;
    this.newGeneratedKey = '';
  }

  private loadApiKeys(): void {
    // Call GraphQL query to load API keys
    // this.settingsService.getApiKeys()
    //   .pipe(takeUntil(this.destroy$))
    //   .subscribe(
    //     (keys) => {
    //       this.apiKeys = keys;
    //     },
    //     () => {
    //       this.msg.error('Failed to load API Keys');
    //     }
    //   );
  }
}
