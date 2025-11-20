import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup } from '@angular/forms';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { DynamicFormService, ColumnMeta } from '../services/dynamic-form.service';

@Component({
  selector: 'app-dynamic-form-field',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    NzFormModule,
    NzInputModule,
    NzInputNumberModule,
    NzDatePickerModule,
    NzCheckboxModule,
    NzSelectModule,
    NzAlertModule
  ],
  template: `
    <div [formGroup]="formGroup">
      <nz-form-item>
        <nz-form-label 
          *ngIf="!isBooleanInput(column.type)"
          [nzRequired]="!column.nullable && !column.primaryKey"
          [nzFor]="column.name">
          {{ formService.getFieldLabel(column.name) }}
          <span *ngIf="column.comment" nz-tooltip [nzTooltipTitle]="column.comment" class="help-text">
            ℹ️
          </span>
        </nz-form-label>
        <nz-form-control 
          [nzErrorTip]="errorTemplate"
          [nzValidateStatus]="getValidateStatus()">
          
          <!-- Text Input (VARCHAR, CHAR, CLOB, etc.) -->
          <textarea
            *ngIf="isTextArea(column.type)"
            nz-input
            [formControlName]="column.name"
            [placeholder]="formService.getPlaceholder(column)"
            [nzAutosize]="{ minRows: 2, maxRows: 6 }">
          </textarea>

          <!-- Text Input (regular text) -->
          <input
            *ngIf="isTextInput(column.type)"
            nz-input
            type="text"
            [formControlName]="column.name"
            [placeholder]="formService.getPlaceholder(column)"
            [maxLength]="column.maxLength || undefined"
            [readonly]="column.primaryKey" />

          <!-- Number Input -->
          <nz-input-number
            *ngIf="isNumberInput(column.type)"
            [formControlName]="column.name"
            [placeholder]="formService.getPlaceholder(column)"
            [nzMin]="getNumberMin()"
            [nzMax]="getNumberMax()"
            [nzStep]="getNumberStep(column)"
            [nzPrecision]="column.scale ?? 0"
            [disabled]="column.primaryKey">
          </nz-input-number>

          <!-- Date/DateTime Input -->
          <nz-date-picker
            *ngIf="isDateInput(column.type)"
            [formControlName]="column.name"
            [nzFormat]="getDateFormat(column)"
            [nzPlaceHolder]="'Select date'">
          </nz-date-picker>

          <!-- Checkbox (Boolean) -->
          <label nz-checkbox *ngIf="isBooleanInput(column.type)" [formControlName]="column.name" [attr.aria-label]="formService.getFieldLabel(column.name)">
          </label>

          <!-- Foreign Key Dropdown (placeholder for future enhancement) -->
          <nz-select
            *ngIf="hasForeignKey(column)"
            [formControlName]="column.name"
            [nzPlaceHolder]="formService.getPlaceholder(column)"
            nzAllowClear>
            <nz-option [nzValue]="''" [nzLabel]="'-- Select --'"></nz-option>
            <!-- Foreign key options would be loaded from backend -->
          </nz-select>
        </nz-form-control>
      </nz-form-item>
    </div>

    <ng-template #errorTemplate let-control>
      <div *ngIf="control && control.errors && (control.dirty || control.touched)" class="error-message">
        {{ formService.getErrorMessage(column, control.errors) }}
      </div>
    </ng-template>
  `,
  styles: [`
    .help-text {
      margin-left: 4px;
      cursor: help;
      opacity: 0.6;
    }
    .error-message {
      color: #ff4d4f;
      font-size: 12px;
      margin-top: 4px;
    }
  `]
})
export class DynamicFormFieldComponent implements OnInit {
  @Input() column!: ColumnMeta;
  @Input() formGroup!: FormGroup;
  @Input() textareaThreshold: number = 1000;

  constructor(public formService: DynamicFormService) {}

  ngOnInit(): void {
    if (!this.column) {
      throw new Error('DynamicFormFieldComponent requires @Input column');
    }
    if (!this.formGroup) {
      throw new Error('DynamicFormFieldComponent requires @Input formGroup');
    }
  }

  isTextInput(type: string): boolean {
    const t = type.toUpperCase();
    return (t.includes('VARCHAR') || t.includes('CHAR') || t === 'TEXT') && !this.isTextArea(type);
  }

  isTextArea(type: string): boolean {
    const t = type.toUpperCase();
    return t === 'CLOB' || (t.includes('VARCHAR') && this.column.maxLength && this.column.maxLength > this.textareaThreshold) || false;
  }

  isNumberInput(type: string): boolean {
    const t = type.toUpperCase();
    return t === 'NUMBER' || t.includes('INT');
  }

  isDateInput(type: string): boolean {
    const t = type.toUpperCase();
    return t === 'DATE' || t.includes('TIMESTAMP');
  }

  isBooleanInput(type: string): boolean {
    return type.toUpperCase() === 'BOOLEAN';
  }

  hasForeignKey(column: ColumnMeta): boolean {
    return !!column.foreignKeyTable && !!column.foreignKeyColumn;
  }

  getValidateStatus(): string {
    const control = this.formGroup.get(this.column.name);
    if (!control) return '';
    if (control.hasError('required') && (control.dirty || control.touched)) return 'error';
    if (control.valid && control.touched) return 'success';
    return '';
  }

  getNumberMin(): number {
    // For NUMBER(p,s), minimum is typically -(10^(p-s) - 1)
    if (this.column.precision && this.column.scale !== undefined) {
      const intDigits = this.column.precision - this.column.scale;
      return -(Math.pow(10, intDigits) - 1);
    }
    return Number.MIN_SAFE_INTEGER;
  }

  getNumberMax(): number {
    // For NUMBER(p,s), maximum is typically 10^(p-s) - 1
    if (this.column.precision && this.column.scale !== undefined) {
      const intDigits = this.column.precision - this.column.scale;
      return Math.pow(10, intDigits) - 1;
    }
    return Number.MAX_SAFE_INTEGER;
  }

  getNumberStep(column: ColumnMeta): number {
    if (column.scale !== undefined && column.scale > 0) {
      return Math.pow(10, -column.scale);
    }
    return 1;
  }

  getDateFormat(column: ColumnMeta): string {
    const type = column.type.toUpperCase();
    if (type.includes('TIMESTAMP')) {
      return 'yyyy-MM-dd HH:mm:ss';
    }
    return 'yyyy-MM-dd';
  }
}
