import { Injectable } from '@angular/core';
import { FormBuilder, FormGroup, FormControl, Validators, AsyncValidator, AbstractControl, ValidationErrors } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

export interface ColumnMeta {
  name: string;
  type: string;
  nullable: boolean;
  primaryKey?: boolean;
  maxLength?: number;
  defaultValue?: string;
  precision?: number;
  scale?: number;
  unique?: boolean;
  comment?: string;
  foreignKeyTable?: string;
  foreignKeyColumn?: string;
}

export interface TableSchema {
  tableName: string;
  columns: ColumnMeta[];
}

@Injectable({
  providedIn: 'root'
})
export class DynamicFormService {
  constructor(
    private formBuilder: FormBuilder,
    private http: HttpClient
  ) {}

  /**
   * Fetch table schema from backend
   */
  getTableSchema(tableName: string): Observable<TableSchema> {
    return this.http.get<TableSchema>(`/api/dynamic-crud/schema/${tableName}`);
  }

  /**
   * Generate FormGroup dynamically based on column metadata
   */
  generateFormGroup(columns: ColumnMeta[], editingRecord?: any): FormGroup {
    const group: { [key: string]: any } = {};

    columns.forEach(column => {
      if (column.primaryKey && editingRecord) {
        // Primary keys are read-only in edit mode
        group[column.name] = new FormControl(
          { value: editingRecord?.[column.name] || '', disabled: true },
          { validators: this.getValidators(column), updateOn: 'blur' }
        );
      } else {
        const validators = this.getValidators(column);
        const value = editingRecord ? editingRecord[column.name] ?? this.parseDefaultValue(column.defaultValue, column.type) : this.parseDefaultValue(column.defaultValue, column.type);
        group[column.name] = new FormControl(value, { validators, updateOn: 'blur' });
      }
    });

    return this.formBuilder.group(group);
  }

  /**
   * Get validators based on column metadata
   */
  private getValidators(column: ColumnMeta): any[] {
    const validators: any[] = [];

    // Required validator
    if (!column.nullable && !column.primaryKey) {
      validators.push(Validators.required);
    }

    // Type-specific validators
    const typeValidators = this.getTypeValidators(column);
    validators.push(...typeValidators);

    // Uniqueness validator (async)
    if (column.unique) {
      validators.push(this.uniqueValueValidator(column));
    }

    return validators;
  }

  /**
   * Get validators specific to Oracle data types
   */
  private getTypeValidators(column: ColumnMeta): any[] {
    const validators: any[] = [];
    const type = column.type.toUpperCase();

    if (type.includes('VARCHAR') || type.includes('CHAR')) {
      // String length validation
      if (column.maxLength) {
        validators.push(Validators.maxLength(column.maxLength));
      }
    } else if (type === 'NUMBER' || type.includes('INT')) {
      // Number validation
      validators.push(Validators.pattern(/^-?\d+(\.\d+)?$/));
      
      if (column.precision && column.scale !== undefined) {
        // Validate precision and scale for NUMBER(p,s)
        validators.push(this.numberPrecisionValidator(column.precision, column.scale));
      } else if (column.precision) {
        // Validate max digits
        validators.push(this.maxDigitsValidator(column.precision));
      }
    } else if (type.includes('DATE') || type.includes('TIMESTAMP')) {
      // Date validation
      validators.push(this.dateValidator());
    }

    return validators;
  }

  /**
   * Custom validator for number precision (e.g., NUMBER(10,2))
   */
  private numberPrecisionValidator(precision: number, scale: number) {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;

      // Guard against invalid precision/scale combinations
      if (scale > precision || precision <= 0) {
        return {
          invalidPrecision: {
            precision,
            scale,
            message: 'Scale must not exceed precision, and precision must be positive'
          }
        };
      }

      const value = String(control.value);
      const parts = value.replace(/^-/, '').split('.');

      const integerDigits = parts[0].length;
      const decimalDigits = parts[1]?.length ?? 0;
      const maxIntegerDigits = precision - scale;

      if (integerDigits > maxIntegerDigits || decimalDigits > scale) {
        return { 
          numberPrecision: {
            expected: `${precision},${scale}`,
            actual: `${integerDigits},${decimalDigits}`
          }
        };
      }

      return null;
    };
  }

  /**
   * Custom validator for maximum digits
   */
  private maxDigitsValidator(maxDigits: number) {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;

      const digits = String(control.value).replace(/^-/, '').replace(/\D/g, '');
      if (digits.length > maxDigits) {
        return { maxDigits: { max: maxDigits, actual: digits.length } };
      }

      return null;
    };
  }

  /**
   * Custom validator for dates
   */
  private dateValidator() {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;

      const date = new Date(control.value);
      if (isNaN(date.getTime())) {
        return { invalidDate: true };
      }

      return null;
    };
  }

  /**
   * Async validator for unique values (checks backend)
   */
  private uniqueValueValidator(column: ColumnMeta) {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      if (!control.value) return of(null);

      // In a real implementation, this would call the backend to check uniqueness
      // For now, it's a placeholder
      return of(null);
    };
  }

  /**
   * Get input type for HTML input element based on Oracle data type
   */
  getInputType(columnType: string): string {
    const type = columnType.toUpperCase();

    if (type === 'DATE' || type.includes('TIMESTAMP')) {
      return 'datetime-local';
    } else if (type === 'NUMBER' || type.includes('INT')) {
      return 'number';
    } else if (type === 'BOOLEAN') {
      return 'checkbox';
    } else {
      return 'text';
    }
  }

  /**
   * Get input pattern for HTML input element based on Oracle data type
   */
  getInputPattern(columnType: string): string | null {
    const type = columnType.toUpperCase();

    if (type === 'NUMBER' || type.includes('INT')) {
      return '^-?\\d+(\\.\\d+)?$';
    } else if (type === 'EMAIL' || type.includes('EMAIL')) {
      return '^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$';
    }

    return null;
  }

  /**
   * Get placeholder text for form fields
   */
  getPlaceholder(column: ColumnMeta): string {
    let placeholder = `Enter ${column.name.toLowerCase()}`;

    if (column.type.toUpperCase().includes('NUMBER')) {
      placeholder = 'Enter a number';
    } else if (column.type.toUpperCase().includes('DATE')) {
      placeholder = 'YYYY-MM-DD';
    }

    if (column.comment) {
      placeholder += ` (${column.comment})`;
    }

    return placeholder;
  }

  /**
   * Get field label from column name
   */
  getFieldLabel(columnName: string): string {
    return columnName
      .replace(/_/g, ' ')
      .replace(/([A-Z])/g, ' $1')
      .replace(/^\s+|\s+$/g, '')
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  /**
   * Parse default value based on column type
   */
  private parseDefaultValue(defaultValue: string | undefined, columnType: string): any {
    // Handle null/undefined explicitly (0 is a valid default)
    if (defaultValue === null || defaultValue === undefined || defaultValue === '') return '';

    const type = columnType.toUpperCase();

    if (type === 'NUMBER' || type.includes('INT')) {
      const parsed = parseFloat(defaultValue);
      // Preserve 0 as a valid default
      return isNaN(parsed) ? '' : parsed;
    } else if (type === 'BOOLEAN') {
      return defaultValue.toUpperCase() === 'TRUE' || defaultValue === '1';
    } else if (type.includes('DATE') || type.includes('TIMESTAMP')) {
      try {
        const date = new Date(defaultValue);
        // Validate that date parsing was successful
        if (isNaN(date.getTime())) {
          return '';
        }
        return date.toISOString().slice(0, 16);
      } catch (error) {
        return '';
      }
    }

    return defaultValue;
  }

  /**
   * Get form field error message
   */
  getErrorMessage(column: ColumnMeta, errors: any): string {
    if (!errors) return '';

    if (errors['required']) {
      return `${this.getFieldLabel(column.name)} is required`;
    } else if (errors['maxlength']) {
      return `Maximum length is ${errors['maxlength'].requiredLength} characters`;
    } else if (errors['minlength']) {
      return `Minimum length is ${errors['minlength'].requiredLength} characters`;
    } else if (errors['min']) {
      return `Minimum value is ${errors['min'].min}`;
    } else if (errors['max']) {
      return `Maximum value is ${errors['max'].max}`;
    } else if (errors['pattern']) {
      return `Invalid format for ${this.getFieldLabel(column.name)}`;
    } else if (errors['numberPrecision']) {
      return `Number precision should be ${errors['numberPrecision'].expected}`;
    } else if (errors['maxDigits']) {
      return `Maximum ${errors['maxDigits'].max} digits allowed`;
    } else if (errors['invalidDate']) {
      return 'Invalid date format';
    }

    return 'Invalid value';
  }

  /**
   * Get columns for displaying in forms (exclude primary keys from edit)
   */
  getFormColumns(columns: ColumnMeta[], isEdit: boolean = false): ColumnMeta[] {
    if (isEdit) {
      // In edit mode, exclude primary keys but show them as read-only
      return columns;
    }
    // In create mode, exclude all primary keys
    return columns.filter(col => !col.primaryKey);
  }

  /**
   * Get columns that are foreign keys
   */
  getForeignKeyColumns(columns: ColumnMeta[]): ColumnMeta[] {
    return columns.filter(col => col.foreignKeyTable && col.foreignKeyColumn);
  }
}
