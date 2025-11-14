import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NZ_MODAL_DATA, NzModalRef } from 'ng-zorro-antd/modal';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { KeyboardShortcut } from '../../core/services/keyboard.service';

@Component({
  selector: 'app-shortcuts-modal',
  standalone: true,
  imports: [CommonModule, NzButtonModule],
  template: `
    <div class="shortcuts-modal">
      <h2>Keyboard Shortcuts</h2>
      <div class="shortcuts-list">
        <div *ngFor="let shortcut of shortcuts" class="shortcut-item">
          <div class="shortcut-keys">
            <span *ngFor="let key of getShortcutKeys(shortcut)" class="key">{{ key }}</span>
          </div>
          <div class="shortcut-description">{{ shortcut.description }}</div>
        </div>
      </div>
      <div class="modal-footer">
        <button nz-button nzType="primary" (click)="close()">Close</button>
      </div>
    </div>
  `,
  styles: [`
    .shortcuts-modal {
      padding: 20px;
    }

    .shortcuts-list {
      margin: 20px 0;
    }

    .shortcut-item {
      display: flex;
      align-items: center;
      margin-bottom: 12px;
      padding: 8px;
      border-radius: 4px;
      background: #f5f5f5;
    }

    .shortcut-keys {
      display: flex;
      gap: 4px;
      margin-right: 16px;
    }

    .key {
      padding: 4px 8px;
      background: #fff;
      border: 1px solid #d9d9d9;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 500;
      min-width: 24px;
      text-align: center;
    }

    .shortcut-description {
      flex: 1;
      font-size: 14px;
    }

    .modal-footer {
      text-align: right;
      margin-top: 20px;
    }
  `]
})
export class ShortcutsModalComponent {
  private modalRef = inject(NzModalRef);
  shortcuts: KeyboardShortcut[] = inject(NZ_MODAL_DATA);

  close(): void {
    this.modalRef.close();
  }

  getShortcutKeys(shortcut: KeyboardShortcut): string[] {
    const keys: string[] = [];
    if (shortcut.ctrlKey) keys.push('Ctrl');
    if (shortcut.altKey) keys.push('Alt');
    if (shortcut.shiftKey) keys.push('Shift');
    keys.push(shortcut.key.toUpperCase());
    return keys;
  }
}