import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NZ_MODAL_DATA, NzModalRef } from 'ng-zorro-antd/modal';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { KeyboardShortcut } from '../../core/services/keyboard.service';

@Component({
  selector: 'app-shortcuts-modal',
  standalone: true,
  imports: [CommonModule, NzButtonModule],
  styleUrl: './shortcuts-modal.component.css',
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