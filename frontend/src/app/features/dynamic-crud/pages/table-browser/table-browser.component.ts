import { Component, OnInit, OnDestroy, AfterViewInit, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { Subject, Observable, of } from 'rxjs';
import { takeUntil, catchError } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { ModalService } from '../../../../core/services/modal.service';
import { KeyboardService } from '../../../../core/services/keyboard.service';

interface TableData {
  [key: string]: any;
}

interface TableColumn {
  name: string;
  type: string;
  nullable: boolean;
  primaryKey?: boolean;
}

interface DynamicCrudRequest {
  tableName: string;
  operation: 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE';
  columns?: Array<{name: string, value: any}>;
  filters?: Array<{
    column: string;
    operator: 'EQ' | 'NE' | 'GT' | 'LT' | 'GE' | 'LE' | 'LIKE';
    value: any;
  }>;
  limit?: number;
  offset?: number;
  orderBy?: string;
  orderDirection?: 'ASC' | 'DESC';
}

interface DynamicCrudResponse {
  rows: TableData[];
  totalCount: number;
  columns: TableColumn[];
}

@Component({
  selector: 'app-table-browser',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    NzTableModule,
    NzButtonModule,
    NzInputModule,
    NzSelectModule,
    NzModalModule,
    NzFormModule,
    NzPaginationModule,
    NzIconModule,
    NzPopconfirmModule
  ],
  templateUrl: './table-browser.component.html',
  styleUrls: ['./table-browser.component.css']
})
export class TableBrowserComponent implements OnInit, OnDestroy, AfterViewInit {
  availableTables: string[] = [];
  selectedTable: string = '';
  tableData: TableData[] = [];
  columns: TableColumn[] = [];
  loading = false;
  totalRecords = 0;
  pageSize = 10;
  currentPage = 1;
  searchValue = '';
  selectedSearchColumn = '';
  sortField = '';
  sortOrder: 'ASC' | 'DESC' = 'ASC';

  // Modal states
  isCreateModalVisible = false;
  isEditModalVisible = false;
  editingRecord: TableData | null = null;
  formData: { [key: string]: any } = {};

  private destroy$ = new Subject<void>();
  private createModalCloseHandler?: () => void;
  private editModalCloseHandler?: () => void;
  private readonly modalService = inject(ModalService);
  private readonly nzModalService = inject(NzModalService);
  private readonly keyboardService = inject(KeyboardService);
  
  // In-memory cache for primary key metadata (keyed by table name)
  private primaryKeyCache: Map<string, string[]> = new Map();
  
  // ━━━━━ SEARCH & KEYBOARD INTEGRATION ━━━━━

  @ViewChild('searchInput', { read: ElementRef }) searchInput?: ElementRef<HTMLInputElement>;

  private searchTargetCleanup?: () => void;

  constructor(
    private http: HttpClient,
    private message: NzMessageService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadAvailableTables();
  }

    ngAfterViewInit(): void {
    if (this.searchInput?.nativeElement) {
      // Register search target and store cleanup function for ngOnDestroy
      this.searchTargetCleanup = this.keyboardService.registerSearchTarget(this.searchInput.nativeElement);
    }
  }

  ngOnDestroy(): void {
    // Cleanup keyboard service registration - removes event listeners and clears DOM references
    if (this.searchTargetCleanup) {
      this.searchTargetCleanup();
    }

    // Unregister modal close handlers to prevent memory leaks
    if (this.createModalCloseHandler) {
      this.modalService.unregisterCloseHandler(this.createModalCloseHandler);
      this.createModalCloseHandler = undefined;
    }
    if (this.editModalCloseHandler) {
      this.modalService.unregisterCloseHandler(this.editModalCloseHandler);
      this.editModalCloseHandler = undefined;
    }

    // Signal completion to all subscribers
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadAvailableTables(): void {
    this.http.get<string[]>('/api/dynamic-crud/tables')
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (tables) => {
          this.availableTables = tables;
        },
        error: (error) => {
          this.message.error('Failed to load available tables');
          console.error('Error loading tables:', error);
        }
      });
  }

  onTableSelect(table: string): void {
    this.selectedTable = table;
    this.currentPage = 1;
    this.searchValue = '';
    this.sortField = '';
    this.loadTableData();
  }

  loadTableData(): void {
    if (!this.selectedTable) return;

    this.loading = true;
    const request: DynamicCrudRequest = {
      tableName: this.selectedTable,
      operation: 'SELECT',
      limit: this.pageSize,
      offset: (this.currentPage - 1) * this.pageSize,
      orderBy: this.sortField || undefined,
      orderDirection: this.sortOrder
    };

    if (this.searchValue && this.columns.length > 0) {
      request.filters = this.buildSearchFilters();
    }

    this.http.post<DynamicCrudResponse>('/api/dynamic-crud/execute', request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.tableData = response.rows;
          this.columns = response.columns;
          this.totalRecords = response.totalCount;
          this.loading = false;
        },
        error: (error) => {
          this.message.error('Failed to load table data');
          console.error('Error loading table data:', error);
          this.loading = false;
        }
      });
  }

  onPageChange(page: number): void {
    this.currentPage = page;
    this.loadTableData();
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.currentPage = 1;
    this.loadTableData();
  }

  onSort(sort: Array<{ key: string; value: string }>): void {
    if (sort && sort.length > 0) {
      const primarySort = sort[0];
      this.sortField = primarySort.key;
      this.sortOrder = primarySort.value === 'descend' ? 'DESC' : 'ASC';
      this.loadTableData();
    }
  }

  onColumnSort(columnName: string, sortOrder: string | null): void {
    this.sortField = columnName;
    this.sortOrder = sortOrder === 'descend' ? 'DESC' : 'ASC';
    this.loadTableData();
  }

  createSortFn(columnName: string): (a: TableData, b: TableData) => number {
    return (a: TableData, b: TableData): number => {
      const aValue = a[columnName];
      const bValue = b[columnName];

      if (aValue === null || aValue === undefined) return 1;
      if (bValue === null || bValue === undefined) return -1;

      if (typeof aValue === 'string' && typeof bValue === 'string') {
        return aValue.localeCompare(bValue);
      }

      if (aValue < bValue) return -1;
      if (aValue > bValue) return 1;
      return 0;
    };
  }

  onSearch(): void {
    this.currentPage = 1;
    this.loadTableData();
  }

  showCreateModal(): void {
    this.formData = {};
    this.columns.forEach(col => {
      if (!col.nullable) {
        this.formData[col.name] = '';
      }
    });
    this.isCreateModalVisible = true;
    this.createModalCloseHandler = () => this.isCreateModalVisible = false;
    this.modalService.registerCloseHandler(this.createModalCloseHandler);
  }

  showEditModal(record: TableData): void {
    this.editingRecord = record;
    this.formData = { ...record };
    this.isEditModalVisible = true;
    this.editModalCloseHandler = () => this.isEditModalVisible = false;
    this.modalService.registerCloseHandler(this.editModalCloseHandler);
  }

  handleCreate(): void {
    if (!this.selectedTable) return;

    const columns = Object.keys(this.formData).map(key => ({
      name: key,
      value: this.formData[key]
    }));

    const request: DynamicCrudRequest = {
      tableName: this.selectedTable,
      operation: 'INSERT',
      columns
    };

    this.http.post('/api/dynamic-crud/execute', request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.message.success('Record created successfully');
          this.isCreateModalVisible = false;
          if (this.createModalCloseHandler) {
            this.modalService.unregisterCloseHandler(this.createModalCloseHandler);
            this.createModalCloseHandler = undefined;
          }
          this.loadTableData();
        },
        error: (error) => {
          this.message.error('Failed to create record');
          console.error('Error creating record:', error);
        }
      });
  }

  handleUpdate(): void {
    if (!this.selectedTable || !this.editingRecord) return;

    // Async resolution of primary keys before proceeding
    this.resolvePrimaryKeys(this.selectedTable).then(resolvedPKNames => {
      if (!resolvedPKNames) {
        return; // User cancelled or error occurred
      }

      // Temporarily inject resolved PKs into columns metadata for buildPrimaryKeyFilters
      const originalPrimaryKey = this.columns.map(c => ({ ...c }));
      const resolvedSet = new Set(resolvedPKNames);
      this.columns.forEach(col => {
        col.primaryKey = resolvedSet.has(col.name);
      });

      const filters = this.buildPrimaryKeyFilters(this.editingRecord);
      
      // Restore original metadata
      this.columns = originalPrimaryKey;

      if (!filters) {
        return;
      }

      // Derive primary key column names from the resolved filters (not stale metadata)
      // This ensures PKs resolved at runtime (e.g., via user prompt) are never included in editable columns
      const primaryKeyNames = new Set(filters.map(f => f.column));
      
      const columns = Object.keys(this.formData)
        .filter(key => !primaryKeyNames.has(key))
        .map(key => ({
          name: key,
          value: this.formData[key]
        }));

      if (columns.length === 0) {
        this.message.warning('No editable fields detected for update.');
        return;
      }

      const request: DynamicCrudRequest = {
        tableName: this.selectedTable,
        operation: 'UPDATE',
        columns,
        filters
      };

      this.http.post('/api/dynamic-crud/execute', request)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.message.success('Record updated successfully');
            this.isEditModalVisible = false;
            this.editingRecord = null;
            if (this.editModalCloseHandler) {
              this.modalService.unregisterCloseHandler(this.editModalCloseHandler);
              this.editModalCloseHandler = undefined;
            }
            this.loadTableData();
          },
          error: (error) => {
            this.message.error('Failed to update record');
            console.error('Error updating record:', error);
          }
        });
    });
  }

  handleDelete(record: TableData): void {
    if (!this.selectedTable) return;

    // Async resolution of primary keys before proceeding
    this.resolvePrimaryKeys(this.selectedTable).then(resolvedPKNames => {
      if (!resolvedPKNames) {
        return; // User cancelled or error occurred
      }

      // Temporarily inject resolved PKs into columns metadata for buildPrimaryKeyFilters
      const originalPrimaryKey = this.columns.map(c => ({ ...c }));
      const resolvedSet = new Set(resolvedPKNames);
      this.columns.forEach(col => {
        col.primaryKey = resolvedSet.has(col.name);
      });

      const filters = this.buildPrimaryKeyFilters(record);
      
      // Restore original metadata
      this.columns = originalPrimaryKey;

      if (!filters) {
        return;
      }

      const request: DynamicCrudRequest = {
        tableName: this.selectedTable,
        operation: 'DELETE',
        filters
      };

      this.http.post('/api/dynamic-crud/execute', request)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.message.success('Record deleted successfully');
            this.loadTableData();
          },
          error: (error) => {
            this.message.error('Failed to delete record');
            console.error('Error deleting record:', error);
          }
        });
    });
  }

  exportData(): void {
    // Simple CSV export
    if (this.tableData.length === 0) return;

    const headers = this.columns.map(col => col.name).join(',');
    const rows = this.tableData.map(row =>
      this.columns.map(col => {
        let value = row[col.name] ?? '';
        // Escape quotes and wrap in quotes if contains comma/quote/newline
        if (typeof value === 'string' && (value.includes(',') || value.includes('"') || value.includes('\n'))) {
          value = '"' + value.replace(/"/g, '""') + '"';
        }
        return value;
      }).join(',')
    ).join('\n');

    const csv = `${headers}\n${rows}`;
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${this.selectedTable}_export.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  getInputType(columnType: string): string {
    switch (columnType.toLowerCase()) {
      case 'number':
      case 'integer':
      case 'bigint':
        return 'number';
      case 'date':
      case 'timestamp':
        return 'datetime-local';
      case 'boolean':
        return 'checkbox';
      default:
        return 'text';
    }
  }

  closeCreateModal(): void {
    this.isCreateModalVisible = false;
    if (this.createModalCloseHandler) {
      this.modalService.unregisterCloseHandler(this.createModalCloseHandler);
      this.createModalCloseHandler = undefined;
    }
  }

  closeEditModal(): void {
    this.isEditModalVisible = false;
    if (this.editModalCloseHandler) {
      this.modalService.unregisterCloseHandler(this.editModalCloseHandler);
      this.editModalCloseHandler = undefined;
    }
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  private buildSearchFilters(): Array<{column: string, operator: 'EQ' | 'NE' | 'GT' | 'LT' | 'GE' | 'LE' | 'LIKE', value: any}> {
    if (this.selectedSearchColumn) {
      return [{ column: this.selectedSearchColumn, operator: 'LIKE', value: `%${this.searchValue}%` }];
    } else {
      const textColumns = this.columns.filter(col => this.isTextColumn(col.type));
      if (textColumns.length > 0) {
        return textColumns.map(col => ({ column: col.name, operator: 'LIKE' as const, value: `%${this.searchValue}%` }));
      } else {
        return [];
      }
    }
  }

  private isTextColumn(type: string): boolean {
    const lowerType = type.toLowerCase();
    return lowerType.includes('char') || lowerType.includes('varchar') || lowerType.includes('text') || lowerType.includes('clob');
  }

  private getPrimaryKeyColumns(): TableColumn[] {
    return this.columns.filter(column => Boolean(column.primaryKey));
  }

  /**
   * Fetches primary key metadata for a table from the backend.
   * First attempts to fetch from /api/dynamic-crud/primary-keys?table=<name>
   * If that fails or returns empty, prompts user via modal to select PK columns.
   * Results are cached in-memory to avoid repeated requests/modals.
   */
  private async resolvePrimaryKeys(tableName: string): Promise<string[] | null> {
    // Check cache first
    if (this.primaryKeyCache.has(tableName)) {
      return this.primaryKeyCache.get(tableName) || null;
    }

    // Step 1: Try to fetch from backend
    try {
      const result = await this.http.get<{ primaryKeys: string[] }>(
        `/api/dynamic-crud/primary-keys?table=${encodeURIComponent(tableName)}`
      ).pipe(
        takeUntil(this.destroy$),
        catchError(() => of(null))
      ).toPromise();

      if (result?.primaryKeys && result.primaryKeys.length > 0) {
        // Cache and return backend result
        this.primaryKeyCache.set(tableName, result.primaryKeys);
        return result.primaryKeys;
      }
    } catch (err) {
      console.debug(`Failed to fetch primary keys from backend for table '${tableName}':`, err);
    }

    // Step 2: Fallback to modal for user selection
    const selectedPKs = await this.promptUserSelectPrimaryKeys();
    if (selectedPKs && selectedPKs.length > 0) {
      // Validate selected columns exist
      const validatedPKs: string[] = [];
      for (const pkName of selectedPKs) {
        const match = this.columns.find(col => col.name.toLowerCase() === pkName.toLowerCase());
        if (!match) {
          this.message.error(`Unknown column '${pkName}'. Operation cancelled.`);
          return null;
        }
        validatedPKs.push(match.name);
      }

      // Cache user selection for future operations
      this.primaryKeyCache.set(tableName, validatedPKs);
      return validatedPKs;
    }

    return null;
  }

  /**
   * Opens a modal to allow user to select primary key columns from available columns.
   * Presents a multi-select list of this.columns and returns the selected column names.
   */
  private promptUserSelectPrimaryKeys(): Promise<string[] | null> {
    return new Promise((resolve) => {
      const selectedColumns: Set<string> = new Set();
      const columnNames = this.columns.map(col => col.name);
      const columnTypes = new Map(this.columns.map(col => [col.name, col.type]));

      this.nzModalService.create({
        nzTitle: 'Select Primary Key Columns',
        nzContent: this.createColumnSelectContent(columnNames, columnTypes),
        nzOkText: 'Confirm',
        nzCancelText: 'Cancel',
        nzOnOk: () => {
          // Collect checked columns
          const checkboxes = document.querySelectorAll('input[type="checkbox"][data-column]') as NodeListOf<HTMLInputElement>;
          checkboxes.forEach(checkbox => {
            if (checkbox.checked) {
              const colName = checkbox.getAttribute('data-column');
              if (colName) {
                selectedColumns.add(colName);
              }
            }
          });

          if (selectedColumns.size === 0) {
            this.message.error('No columns selected. Operation cancelled.');
            return resolve(null);
          }

          resolve(Array.from(selectedColumns));
        },
        nzOnCancel: () => {
          this.message.info('Operation cancelled: no primary key columns selected.');
          resolve(null);
        }
      });
    });
  }

  /**
   * Helper to generate column select content HTML
   */
  private createColumnSelectContent(columnNames: string[], columnTypes: Map<string, string>): string {
    return `
      <div>
        <p>Primary key metadata is unavailable. Please select one or more columns that uniquely identify records:</p>
        <div style="max-height: 300px; overflow-y: auto; border: 1px solid #d9d9d9; border-radius: 2px;">
          ${columnNames.map(colName => `
            <label style="display: block; padding: 8px; cursor: pointer; user-select: none;">
              <input type="checkbox" data-column="${colName}" style="margin-right: 8px;" />
              <strong>${colName}</strong> (${columnTypes.get(colName)})
            </label>
          `).join('')}
        </div>
      </div>
    `;
  }

  private buildPrimaryKeyFilters(record: TableData | null): DynamicCrudRequest['filters'] | null {
    let primaryKeys = this.getPrimaryKeyColumns();

    if (!record) {
      this.message.error('No record selected for deriving primary key filters.');
      return null;
    }

    const filters: NonNullable<DynamicCrudRequest['filters']> = [];

    for (const pk of primaryKeys) {
      const value = record[pk.name];
      if (value === undefined || value === null || value === '') {
        this.message.error(`Missing value for primary key column '${pk.name}'.`);
        return null;
      }
      filters.push({
        column: pk.name,
        operator: 'EQ',
        value
      });
    }

    return filters;
  }
}