import { Component, OnInit, ViewChild, ElementRef, Input, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormBuilder, FormControl, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { Subject, takeUntil } from 'rxjs';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageModule, NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzUploadModule } from 'ng-zorro-antd/upload';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzBadgeModule } from 'ng-zorro-antd/badge';

interface BulkRow {
  columns?: Array<{ name: string; value: any }>;
  filters?: Array<{ column: string; operator: string; value: any }>;
}

interface ColumnMeta {
  name: string;
  type: string;
  nullable?: boolean;
  primaryKey?: boolean;
}

interface BulkOperationRequest {
  tableName: string;
  operation: 'INSERT' | 'UPDATE' | 'DELETE';
  rows: BulkRow[];
  dryRun?: boolean;
  batchSize?: number;
  metadata?: string;
}

interface BulkOperationResponse {
  totalRows: number;
  successfulRows: number;
  failedRows: number;
  processedRows: number;
  status: string;
  errors: Array<{ rowNumber: number; message: string; errorType: string }>;
  durationMs: number;
  dryRunPreview?: {
    estimatedAffectedRows: number;
    executionPlan: string;
    validationWarnings: string[];
  };
}

interface ImportRequest {
  tableName: string;
  format: 'CSV' | 'JSON';
  data: string;
  operation: 'INSERT' | 'UPDATE' | 'DELETE';
  dryRun?: boolean;
  skipOnError?: boolean;
  columnMapping?: string[];
  metadata?: string;
}

@Component({
  selector: 'app-bulk-operations-wizard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    NzTableModule,
    NzButtonModule,
    NzInputModule,
    NzSelectModule,
    NzModalModule,
    NzFormModule,
    NzPaginationModule,
    NzIconModule,
    NzMessageModule,
    NzPopconfirmModule,
    NzAlertModule,
    NzProgressModule,
    NzSpinModule,
    NzUploadModule,
    NzCheckboxModule,
    NzTabsModule,
    NzToolTipModule,
    NzBadgeModule
  ],
  template: `
    <div class="bulk-operations-container">
      <!-- Bulk Operations Tabs -->
      <nz-tabset #tabset>
        <!-- Bulk Execute Tab -->
        <nz-tab nzTitle="Bulk Operations">
          <div class="bulk-operations-section">
            <h3>Execute Bulk Operations</h3>
            <p>Import multiple rows for insert, update, or delete operations with progress tracking.</p>
            
            <form [formGroup]="bulkForm">
              <div class="form-group">
                <label>Operation Type</label>
                <nz-select formControlName="operation" nzPlaceHolder="Select operation">
                  <nz-option nzValue="INSERT" nzLabel="Insert"></nz-option>
                  <nz-option nzValue="UPDATE" nzLabel="Update"></nz-option>
                  <nz-option nzValue="DELETE" nzLabel="Delete"></nz-option>
                </nz-select>
              </div>

              <div class="form-group">
                <label>Batch Size</label>
                <input nz-input type="number" formControlName="batchSize" 
                       nzPlaceHolder="Enter batch size (default: 100)" />
              </div>

              <div class="form-group">
                <label>
                  <input type="checkbox" formControlName="dryRun" />
                  Dry Run Preview (validate without executing)
                </label>
              </div>

              <div class="form-group">
                <label>Metadata (Optional)</label>
                <textarea nz-input formControlName="metadata" 
                          nzPlaceHolder="Add operation metadata for audit logging"
                          [nzAutosize]="{ minRows: 2, maxRows: 4 }"></textarea>
              </div>

              <div class="button-group">
                <button nz-button nzType="primary" (click)="showBulkRowEditor()">
                  <span nz-icon nzType="plus"></span>
                  Add Rows
                </button>
                <button nz-button (click)="importFromCSV()">
                  <span nz-icon nzType="upload"></span>
                  Import CSV
                </button>
              </div>

              <div *ngIf="bulkRows.length > 0" class="rows-preview">
                <h4>Preview: {{ bulkRows.length }} rows</h4>
                <nz-table [nzData]="bulkRows.slice(0, 5)" [nzShowPagination]="false">
                  <thead>
                    <tr>
                      <th>Row #</th>
                      <th>Columns</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let row of bulkRows.slice(0, 5); let i = index">
                      <td>{{ i + 1 }}</td>
                      <td>{{ row.columns?.length || 0 }} columns</td>
                      <td>
                        <button nz-button nzType="link" nzSize="small" (click)="removeBulkRow(i)">
                          Remove
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </nz-table>
                <p *ngIf="bulkRows.length > 5" class="text-muted">... and {{ bulkRows.length - 5 }} more rows</p>

                <div class="bulk-controls">
                  <button nz-button nzType="primary" nzDanger (click)="executeBulkOperation()" [disabled]="bulkExecuting">
                    <span nz-icon nzType="rocket" *ngIf="!bulkExecuting"></span>
                    <nz-spin *ngIf="bulkExecuting" nzSimple [nzSize]="'small'"></nz-spin>
                    {{ bulkForm.get('dryRun')?.value ? 'Preview' : 'Execute' }}
                  </button>
                  <button nz-button (click)="clearBulkRows()">Clear All</button>
                </div>
              </div>
            </form>

            <!-- Bulk Operation Results -->
            <div *ngIf="bulkOperationResult" class="operation-result">
              <h4>Operation Result</h4>
              <nz-alert 
                [nzType]="bulkOperationResult.status === 'SUCCESS' ? 'success' : 'warning'"
                [nzMessage]="bulkOperationResult.status"
                [nzDescription]="getResultSummary(bulkOperationResult)"
                [nzCloseable]="true">
              </nz-alert>

              <div *ngIf="bulkOperationResult.dryRunPreview" class="dry-run-preview">
                <h5>Dry Run Preview</h5>
                <p><strong>Estimated Affected Rows:</strong> {{ bulkOperationResult.dryRunPreview.estimatedAffectedRows }}</p>
                <p><strong>Plan:</strong> {{ bulkOperationResult.dryRunPreview.executionPlan }}</p>
                <div *ngIf="bulkOperationResult.dryRunPreview.validationWarnings?.length">
                  <strong>Warnings:</strong>
                  <ul>
                    <li *ngFor="let warning of bulkOperationResult.dryRunPreview.validationWarnings">
                      {{ warning }}
                    </li>
                  </ul>
                </div>
              </div>

              <div *ngIf="bulkOperationResult.errors?.length" class="error-list">
                <h5>Errors ({{ bulkOperationResult.errors.length }})</h5>
                <nz-table [nzData]="bulkOperationResult.errors.slice(0, 10)" [nzShowPagination]="false">
                  <thead>
                    <tr>
                      <th>Row</th>
                      <th>Type</th>
                      <th>Message</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let error of bulkOperationResult.errors.slice(0, 10)">
                      <td>{{ error.rowNumber }}</td>
                      <td><span nz-badge [nzStatus]="'error'" [nzText]="error.errorType"></span></td>
                      <td>{{ error.message }}</td>
                    </tr>
                  </tbody>
                </nz-table>
              </div>
            </div>
          </div>
        </nz-tab>

        <!-- Import Tab -->
        <nz-tab nzTitle="Import Data">
          <div class="import-section">
            <h3>Import Data with Validation</h3>
            <p>Import data from CSV or JSON format with dry-run preview before committing.</p>

            <form [formGroup]="importForm">
              <div class="form-group">
                <label>Import Format</label>
                <nz-select formControlName="format" nzPlaceHolder="Select format">
                  <nz-option nzValue="CSV" nzLabel="CSV"></nz-option>
                  <nz-option nzValue="JSON" nzLabel="JSON"></nz-option>
                </nz-select>
              </div>

              <div class="form-group">
                <label>Operation Type</label>
                <nz-select formControlName="operation" nzPlaceHolder="Select operation">
                  <nz-option nzValue="INSERT" nzLabel="Insert"></nz-option>
                  <nz-option nzValue="UPDATE" nzLabel="Update"></nz-option>
                  <nz-option nzValue="DELETE" nzLabel="Delete"></nz-option>
                </nz-select>
              </div>

              <div class="form-group">
                <label>Import Data</label>
                <textarea formControlName="data"
                          nz-input
                          nzPlaceHolder="Paste CSV or JSON data here"
                          [nzAutosize]="{ minRows: 6, maxRows: 12 }"></textarea>
              </div>

              <div class="form-group">
                <label>
                  <input type="checkbox" formControlName="dryRun" />
                  Dry Run (preview without executing)
                </label>
              </div>

              <div class="form-group">
                <label>
                  <input type="checkbox" formControlName="skipOnError" />
                  Skip on Error (continue processing remaining rows on error)
                </label>
              </div>

              <div class="button-group">
                <button nz-button nzType="primary" (click)="executeImport()" [disabled]="importExecuting">
                  <span nz-icon nzType="upload" *ngIf="!importExecuting"></span>
                  <nz-spin *ngIf="importExecuting" nzSimple [nzSize]="'small'"></nz-spin>
                  {{ importForm.get('dryRun')?.value ? 'Preview' : 'Import' }}
                </button>
              </div>
            </form>

            <!-- Import Results -->
            <div *ngIf="importResult" class="import-result">
              <h4>Import Result</h4>
              <nz-alert
                [nzType]="importResult.status === 'SUCCESS' ? 'success' : 'warning'"
                [nzMessage]="importResult.status"
                [nzDescription]="getResultSummary(importResult)"
                [nzCloseable]="true">
              </nz-alert>
            </div>
          </div>
        </nz-tab>

        <!-- Export Tab -->
        <nz-tab nzTitle="Export Data">
          <div class="export-section">
            <h3>Export Table Data</h3>
            <p>Export current table data in various formats for backup or external processing.</p>

            <form [formGroup]="exportForm">
              <div class="form-group">
                <label>Export Format</label>
                <nz-select formControlName="format" nzPlaceHolder="Select format">
                  <nz-option nzValue="CSV" nzLabel="CSV"></nz-option>
                  <nz-option nzValue="JSON" nzLabel="JSON (Array)"></nz-option>
                  <nz-option nzValue="JSONL" nzLabel="JSONL (Line-delimited)"></nz-option>
                </nz-select>
              </div>

              <div class="form-group">
                <label>
                  <input type="checkbox" formControlName="includeHeaders" />
                  Include Headers
                </label>
              </div>

              <div class="form-group">
                <label>File Name (Optional)</label>
                <input nz-input type="text" formControlName="fileName"
                       [value]="selectedTable + '_export'" />
              </div>

              <div class="button-group">
                <button nz-button nzType="primary" (click)="executeExport()" [disabled]="exportExecuting">
                  <span nz-icon nzType="download" *ngIf="!exportExecuting"></span>
                  <nz-spin *ngIf="exportExecuting" nzSimple [nzSize]="'small'"></nz-spin>
                  Download
                </button>
              </div>
            </form>
          </div>
        </nz-tab>
      </nz-tabset>
    </div>

    <!-- Bulk Row Editor Modal -->
    <nz-modal
      [(nzVisible)]="isRowEditorVisible"
      nzTitle="Add Bulk Rows"
      [nzOkText]="'Add Row'"
      [nzCancelText]="'Close'"
      [nzWidth]="800"
      (nzOnOk)="addBulkRow()">
      <form [formGroup]="rowEditorForm">
        <!-- Dynamic form fields based on table columns -->
        <div class="row-fields">
          <div *ngFor="let col of columns" class="form-group">
            <label>{{ col.name }}</label>
            <input nz-input type="text" 
                   [formControl]="getRowFieldControl(col.name)"
                   [placeholder]="col.type" />
          </div>
        </div>
      </form>
    </nz-modal>

    <!-- File Input for CSV Upload -->
    <input #fileInput type="file" accept=".csv,.json" style="display:none" 
           (change)="onFileSelected($event)" />
  `,
  styles: [`
    .bulk-operations-container {
      padding: 16px;
      background: #f5f5f5;
      border-radius: 4px;
    }

    .bulk-operations-section, .import-section, .export-section {
      background: white;
      padding: 16px;
      border-radius: 4px;
    }

    .form-group {
      margin-bottom: 16px;
    }

    .form-group label {
      display: block;
      margin-bottom: 8px;
      font-weight: 500;
    }

    .button-group {
      margin-top: 16px;
      display: flex;
      gap: 8px;
    }

    .rows-preview {
      margin-top: 24px;
      padding: 16px;
      background: #fafafa;
      border-radius: 4px;
    }

    .rows-preview h4 {
      margin-bottom: 12px;
    }

    .bulk-controls {
      margin-top: 16px;
      display: flex;
      gap: 8px;
    }

    .operation-result, .import-result {
      margin-top: 24px;
      padding: 16px;
      background: #fafafa;
      border-radius: 4px;
    }

    .dry-run-preview {
      margin-top: 12px;
      padding: 12px;
      background: white;
      border-left: 3px solid #1890ff;
    }

    .error-list {
      margin-top: 12px;
      padding: 12px;
      background: white;
      border-left: 3px solid #ff4d4f;
    }

    .error-list ul {
      margin-left: 20px;
      margin-top: 8px;
    }

    .text-muted {
      color: #666;
      font-size: 12px;
      margin-top: 8px;
    }

    .row-fields {
      max-height: 400px;
      overflow-y: auto;
    }
  `]
})
export class BulkOperationsWizardComponent implements OnInit, OnDestroy {
  private readonly apiBaseUrl = environment.apiUrl;
  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;
  @ViewChild('tabset') tabset?: any;
  @Input() selectedTable = '';

  bulkForm: FormGroup;
  importForm: FormGroup;
  exportForm!: FormGroup;
  rowEditorForm: FormGroup;

  bulkRows: BulkRow[] = [];
  columns: ColumnMeta[] = [];

  isRowEditorVisible = false;
  bulkExecuting = false;
  importExecuting = false;
  exportExecuting = false;

  bulkOperationResult: BulkOperationResponse | null = null;
  importResult: BulkOperationResponse | null = null;

  private destroy$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private message: NzMessageService,
    private fb: FormBuilder
  ) {
    this.bulkForm = this.fb.group({
      operation: ['INSERT', Validators.required],
      batchSize: [100, [Validators.required, Validators.min(1), Validators.max(1000)]],
      dryRun: [false],
      metadata: ['']
    });

    this.importForm = this.fb.group({
      format: ['CSV', Validators.required],
      operation: ['INSERT', Validators.required],
      data: ['', Validators.required],
      dryRun: [true],
      skipOnError: [false]
    });

    this.rowEditorForm = this.fb.group({});
  }

  ngOnInit(): void {
    // Initialize exportForm with resolved selectedTable
    this.exportForm = this.fb.group({
      format: ['CSV', Validators.required],
      includeHeaders: [true],
      fileName: [this.selectedTable + '_export']
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  showBulkRowEditor(): void {
    this.rowEditorForm = this.createRowEditorForm();
    this.isRowEditorVisible = true;
  }

  addBulkRow(): void {
    const formValue = this.rowEditorForm.value;
    const columns: Array<{ name: string; value: any }> = Object.keys(formValue)
      .map(key => ({ name: key, value: formValue[key] }))
      .filter(col => col.value !== null && col.value !== undefined);

    this.bulkRows.push({ columns });
    this.isRowEditorVisible = false;
    this.message.success(`Row added. Total: ${this.bulkRows.length}`);
  }

  removeBulkRow(index: number): void {
    this.bulkRows.splice(index, 1);
    this.message.info('Row removed');
  }

  clearBulkRows(): void {
    this.bulkRows = [];
    this.message.info('All rows cleared');
  }

  importFromCSV(): void {
    this.fileInput?.nativeElement.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;

      // Detect active tab - if Bulk Operations tab is active, parse into bulkRows
      const activeTabIndex = this.tabset?.nzSelectedIndex ?? 1;
      if (activeTabIndex === 0) {
        // Bulk Operations tab - parse CSV/JSON into bulkRows
        try {
          const format = file.name.endsWith('.json') ? 'JSON' : 'CSV';
          const parsedRows = format === 'JSON'
            ? this.parseJSONForBulk(content)
            : this.parseCSVForBulk(content);

          this.bulkRows = parsedRows;
          this.message.success(`Loaded ${parsedRows.length} rows into Bulk Operations`);
        } catch (error) {
          this.message.error(`Failed to parse file: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
      } else {
        // Import tab - load into importForm.data
        this.importForm.patchValue({ data: content });
        this.message.success('File loaded');
      }
    };
    reader.readAsText(file);
  }

  private parseCSVForBulk(content: string): BulkRow[] {
    const lines = content.split('\n').filter(line => line.trim());
    if (lines.length < 2) {
      throw new Error('CSV must have header and at least one data row');
    }

    const headers = lines[0].split(',').map(h => h.trim());
    const rows: BulkRow[] = [];

    for (let i = 1; i < lines.length; i++) {
      const values = lines[i].split(',').map(v => v.trim());
      const columns = headers.map((header, idx) => ({
        name: header,
        value: values[idx] || ''
      })).filter(col => col.value !== null && col.value !== undefined);

      rows.push({ columns });
    }

    return rows;
  }

  private parseJSONForBulk(content: string): BulkRow[] {
    const data = JSON.parse(content);
    if (!Array.isArray(data)) {
      throw new Error('JSON must be an array of objects');
    }

    return data.map(item => {
      const columns = Object.entries(item).map(([name, value]) => ({
        name,
        value
      })).filter(col => col.value !== null && col.value !== undefined);

      return { columns };
    });
  }

  executeBulkOperation(): void {
    if (!this.selectedTable) {
      this.message.error('Please select a table');
      return;
    }

    if (!this.bulkForm.valid || this.bulkRows.length === 0) {
      this.message.error('Please select operation and add rows');
      return;
    }

    this.bulkExecuting = true;
    const request: BulkOperationRequest = {
      tableName: this.selectedTable,
      operation: this.bulkForm.get('operation')?.value,
      rows: this.bulkRows,
      dryRun: this.bulkForm.get('dryRun')?.value,
      batchSize: this.bulkForm.get('batchSize')?.value,
      metadata: this.bulkForm.get('metadata')?.value
    };

    this.http.post<BulkOperationResponse>(this.apiUrl('/api/dynamic-crud/bulk'), request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.bulkOperationResult = response;
          this.message.success('Bulk operation completed');
          if (!request.dryRun) {
            this.bulkRows = [];
          }
        },
        error: (error) => {
          const errorMessage = error.error?.message || error.message || error.statusText || 'Unknown error';
          this.message.error(`Bulk operation failed: ${errorMessage}`);
          console.error('Bulk operation error:', error);
          this.bulkExecuting = false;
        },
        complete: () => {
          this.bulkExecuting = false;
        }
      });
  }

  executeImport(): void {
    if (!this.selectedTable) {
      this.message.error('Please select a table');
      return;
    }

    if (!this.importForm.valid) {
      this.message.error('Please fill all required fields');
      return;
    }

    this.importExecuting = true;
    const request: ImportRequest = {
      tableName: this.selectedTable,
      format: this.importForm.get('format')?.value,
      operation: this.importForm.get('operation')?.value,
      data: this.importForm.get('data')?.value,
      dryRun: this.importForm.get('dryRun')?.value,
      skipOnError: this.importForm.get('skipOnError')?.value
    };

    this.http.post<BulkOperationResponse>(this.apiUrl('/api/dynamic-crud/import'), request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.importResult = response;
          this.message.success('Import completed');
        },
        error: (error) => {
          const errorMessage = error.error?.message || error.message || error.statusText || 'Unknown error';
          this.message.error(`Import failed: ${errorMessage}`);
          console.error('Import error:', error);
          this.importExecuting = false;
        },
        complete: () => {
          this.importExecuting = false;
        }
      });
  }

  executeExport(): void {
    if (!this.selectedTable) {
      this.message.error('Please select a table');
      return;
    }

    if (!this.exportForm.valid) {
      this.message.error('Please select export format');
      return;
    }

    this.exportExecuting = true;
    const format = this.exportForm.get('format')?.value;
    const extensionMap: { [key: string]: string } = {
      'CSV': 'csv',
      'JSON': 'json',
      'JSONL': 'jsonl'
    };
    const extension = extensionMap[format] || format.toLowerCase();

    const request = {
      tableName: this.selectedTable,
      format: format,
      includeHeaders: this.exportForm.get('includeHeaders')?.value,
      fileName: this.exportForm.get('fileName')?.value
    };

    this.http.post(this.apiUrl('/api/dynamic-crud/export'), request, { responseType: 'blob' })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (blob) => {
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = request.fileName || `export.${extension}`;
          link.click();
          window.URL.revokeObjectURL(url);
          this.message.success('File exported successfully');
        },
        error: (error) => {
          const errorMessage = error.error?.message || error.message || error.statusText || 'Unknown error';
          this.message.error(`Export failed: ${errorMessage}`);
          console.error('Export error:', error);
          this.exportExecuting = false;
        },
        complete: () => {
          this.exportExecuting = false;
        }
      });
  }

  getResultSummary(result: BulkOperationResponse): string {
    return `Processed: ${result.processedRows}/${result.totalRows}, Success: ${result.successfulRows}, Failed: ${result.failedRows}, Duration: ${result.durationMs}ms`;
  }

  private createRowEditorForm(): FormGroup {
    const group: any = {};
    this.columns.forEach(col => {
      group[col.name] = [''];
    });
    return this.fb.group(group);
  }

  getRowFieldControl(columnName: string): FormControl {
    return this.rowEditorForm.get(columnName) as FormControl;
  }

  private apiUrl(path: string): string {
    if (!path.startsWith('/')) {
      return `${this.apiBaseUrl}/${path}`;
    }
    return `${this.apiBaseUrl}${path}`;
  }
}
