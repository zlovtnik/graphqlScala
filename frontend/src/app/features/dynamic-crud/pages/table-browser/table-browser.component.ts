import { Component, OnInit, OnDestroy, AfterViewInit, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormBuilder, Validators, FormControl } from '@angular/forms';
import { Router } from '@angular/router';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService, NzMessageModule } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { Subject, Observable, of } from 'rxjs';
import { takeUntil, catchError, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { ModalService } from '../../../../core/services/modal.service';
import { KeyboardService } from '../../../../core/services/keyboard.service';
import { DynamicFormService, ColumnMeta } from '../../services/dynamic-form.service';
import { DynamicFormFieldComponent } from '../../components/dynamic-form-field.component';
import { environment } from '../../../../../environments/environment';

interface TableData {
  [key: string]: any;
}

type ComparisonOperator = 'EQ' | 'NE' | 'GT' | 'LT' | 'GE' | 'LE' | 'LIKE';
type LogicalOperator = 'AND' | 'OR';

interface GlobalSearchDescriptor {
  term: string;
  columns?: string[];
  matchMode?: 'CONTAINS' | 'STARTS_WITH' | 'ENDS_WITH' | 'EXACT';
  caseSensitive?: boolean;
}

interface FilterGroup {
  operator: LogicalOperator;
  filters: Array<{column: string; operator: ComparisonOperator; value: any}>;
}

interface DynamicCrudRequest {
  tableName: string;
  operation: 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE';
  columns?: Array<{name: string, value: any}>;
  filters?: Array<{
    column: string;
    operator: ComparisonOperator;
    value: any;
  }>;
  filterGroups?: FilterGroup[];
  globalSearch?: GlobalSearchDescriptor;
  limit?: number;
  offset?: number;
  orderBy?: string;
  orderDirection?: 'ASC' | 'DESC';
}

interface DynamicCrudResponse {
  rows: TableData[];
  totalCount: number;
  columns: ColumnMeta[];
}

interface QueryFilterChip {
  id: string;
  column: string;
  operator: ComparisonOperator;
  value: string;
}

@Component({
  selector: 'app-table-browser',
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
    NzTagModule,
    DynamicFormFieldComponent
  ],
  templateUrl: './table-browser.component.html',
  styleUrls: ['./table-browser.component.css']
})
export class TableBrowserComponent implements OnInit, OnDestroy, AfterViewInit {
  private readonly apiBaseUrl = environment.apiUrl;

  availableTables: string[] = [];
  selectedTable: string = '';
  tableData: TableData[] = [];
  columns: ColumnMeta[] = [];
  loading = false;
  totalRecords = 0;
  pageSize = 10;
  currentPage = 1;
  searchValue = '';
  globalSearchColumns: string[] = [];
  sortField = '';
  sortOrder: 'ASC' | 'DESC' = 'ASC';

  // Modal states
  isCreateModalVisible = false;
  isEditModalVisible = false;
  editingRecord: TableData | null = null;
  createFormGroup: FormGroup | null = null;
  editFormGroup: FormGroup | null = null;
  formColumnError: string | null = null;
  filterBuilderForm: FormGroup;
  filterChips: QueryFilterChip[] = [];
  filterGroupOperator: LogicalOperator = 'AND';
  operatorOptions: Array<{ label: string; value: ComparisonOperator }> = [
    { label: 'Contains', value: 'LIKE' },
    { label: 'Equals', value: 'EQ' },
    { label: 'Not equals', value: 'NE' },
    { label: 'Greater than', value: 'GT' },
    { label: 'Greater or equal', value: 'GE' },
    { label: 'Less than', value: 'LT' },
    { label: 'Less or equal', value: 'LE' }
  ];
  globalSearchControl = new FormControl<string>('', { nonNullable: true });

  private destroy$ = new Subject<void>();
  private createModalCloseHandler?: () => void;
  private editModalCloseHandler?: () => void;
  private readonly modalService = inject(ModalService);
  private readonly nzModalService = inject(NzModalService);
  private readonly keyboardService = inject(KeyboardService);
  private readonly dynamicFormService = inject(DynamicFormService);
  
  // In-memory cache for primary key metadata (keyed by table name)
  private primaryKeyCache: Map<string, string[]> = new Map();
  
  // ━━━━━ SEARCH & KEYBOARD INTEGRATION ━━━━━

  @ViewChild('searchInput', { read: ElementRef }) searchInput?: ElementRef<HTMLInputElement>;

  private searchTargetCleanup?: () => void;

  constructor(
    private http: HttpClient,
    private message: NzMessageService,
    private router: Router,
    private formBuilder: FormBuilder
  ) {
    this.filterBuilderForm = this.formBuilder.group({
      column: [null, Validators.required],
      operator: ['LIKE', Validators.required],
      value: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadAvailableTables();
    this.globalSearchControl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(value => {
        this.searchValue = value ?? '';
        this.currentPage = 1;
        this.loadTableData();
      });
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
    this.http.get<string[]>(this.apiUrl('/api/dynamic-crud/tables'))
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (tables) => {
          this.availableTables = tables;
        },
        error: (error) => {
          console.error('Error loading tables:', error);
          console.error('Status:', error?.status);
          console.error('Response:', error?.error);
          this.message.error('Failed to load available tables. Please check backend connectivity.');
        }
      });
  }

  onTableSelect(table: string): void {
    this.selectedTable = table;
    this.currentPage = 1;
    this.resetSearchBuilderState();
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

    const globalSearch = this.buildGlobalSearchDescriptor();
    if (globalSearch) {
      request.globalSearch = globalSearch;
    } else if (this.searchValue && this.columns.length > 0) {
      request.filters = this.buildSearchFilters();
    }

    const filterGroups = this.buildFilterGroupPayload();
    if (filterGroups) {
      request.filterGroups = filterGroups;
    }

    const executeEndpoint = this.apiUrl('/api/dynamic-crud/execute');
    const absoluteEndpoint = executeEndpoint;
    // eslint-disable-next-line no-console
    console.debug('[DynamicCrudDebug] loadTableData issuing execute request', {
      executeEndpoint,
      absoluteEndpoint,
      selectedTable: this.selectedTable,
      pagination: { page: this.currentPage, pageSize: this.pageSize },
      sort: { field: this.sortField, order: this.sortOrder },
      hasFilters: Boolean(request.filters?.length),
      hasFilterGroups: Boolean(request.filterGroups?.length),
      hasGlobalSearch: Boolean(request.globalSearch)
    });

    this.http.post<DynamicCrudResponse>(executeEndpoint, request)
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
    this.searchValue = this.globalSearchControl.value ?? '';
    this.triggerSearch();
  }

  private triggerSearch(resetPage: boolean = true): void {
    if (resetPage) {
      this.currentPage = 1;
    }
    this.loadTableData();
  }

  clearGlobalSearch(): void {
    const hadSearch = Boolean(this.searchValue?.trim());
    const hadColumnOverrides = this.globalSearchColumns.length > 0;
    if (!hadSearch && !hadColumnOverrides) {
      return;
    }

    this.searchValue = '';
    this.globalSearchColumns = [];
    this.globalSearchControl.setValue('', { emitEvent: hadSearch });

    if (hadColumnOverrides && !hadSearch) {
      this.triggerSearch();
    }
  }

  onGlobalSearchColumnsChange(columns: string[] | null): void {
    this.globalSearchColumns = columns ?? [];
    this.triggerSearch();
  }

  addFilterChip(): void {
    if (this.filterBuilderForm.invalid) {
      this.filterBuilderForm.markAllAsTouched();
      return;
    }

    const { column, operator, value } = this.filterBuilderForm.value;
    if (!column || !operator || value === null || value === undefined || value === '') {
      return;
    }

    const chipValue = typeof value === 'string' ? value.trim() : String(value);
    if (chipValue === '') {
      return;
    }

    const newChip: QueryFilterChip = {
      id: this.createChipId(),
      column,
      operator,
      value: chipValue
    };

    this.filterChips = [...this.filterChips, newChip];
    this.filterBuilderForm.patchValue({ value: '' });
    this.triggerSearch();
  }

  removeFilterChip(chipId: string): void {
    this.filterChips = this.filterChips.filter(chip => chip.id !== chipId);
    this.triggerSearch(false);
  }

  clearFilterChips(): void {
    if (!this.filterChips.length) {
      return;
    }
    this.filterChips = [];
    this.triggerSearch();
  }

  onFilterGroupOperatorChange(operator: LogicalOperator): void {
    if (this.filterGroupOperator === operator) {
      return;
    }
    this.filterGroupOperator = operator;
    if (this.filterChips.length) {
      this.triggerSearch(false);
    }
  }

  showCreateModal(): void {
    try {
      this.formColumnError = null;
      this.createFormGroup = this.dynamicFormService.generateFormGroup(this.columns);
      this.isCreateModalVisible = true;
      this.createModalCloseHandler = () => this.isCreateModalVisible = false;
      this.modalService.registerCloseHandler(this.createModalCloseHandler);
    } catch (error) {
      this.formColumnError = 'Failed to generate form';
      this.message.error('Failed to generate create form');
      console.error('Error generating form:', error);
    }
  }

  showEditModal(record: TableData): void {
    try {
      this.formColumnError = null;
      this.editingRecord = record;
      this.editFormGroup = this.dynamicFormService.generateFormGroup(this.columns, record);
      this.isEditModalVisible = true;
      this.editModalCloseHandler = () => this.isEditModalVisible = false;
      this.modalService.registerCloseHandler(this.editModalCloseHandler);
    } catch (error) {
      this.formColumnError = 'Failed to generate form';
      this.message.error('Failed to generate edit form');
      console.error('Error generating form:', error);
    }
  }

  handleCreate(): void {
    if (!this.selectedTable || !this.createFormGroup) return;

    if (this.createFormGroup.invalid) {
      this.message.error('Please fix validation errors in the form');
      return;
    }

    const columns = Object.keys(this.createFormGroup.value).map(key => ({
      name: key,
      value: this.createFormGroup!.get(key)?.value
    }));

    const request: DynamicCrudRequest = {
      tableName: this.selectedTable,
      operation: 'INSERT',
      columns
    };

    this.http.post(this.apiUrl('/api/dynamic-crud/execute'), request)
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
    if (!this.selectedTable || !this.editingRecord || !this.editFormGroup) return;

    if (this.editFormGroup.invalid) {
      this.message.error('Please fix validation errors in the form');
      return;
    }

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

      // Derive primary key column names from the resolved filters
      const primaryKeyNames = new Set(filters.map(f => f.column));
      
      const columns = Object.keys(this.editFormGroup!.value)
        .filter(key => !primaryKeyNames.has(key))
        .map(key => ({
          name: key,
          value: this.editFormGroup!.get(key)?.value
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

      this.http.post(this.apiUrl('/api/dynamic-crud/execute'), request)
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

      this.http.post(this.apiUrl('/api/dynamic-crud/execute'), request)
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

  private buildGlobalSearchDescriptor(): GlobalSearchDescriptor | undefined {
    const trimmedValue = this.searchValue?.trim();
    if (!trimmedValue) {
      return undefined;
    }

    const descriptor: GlobalSearchDescriptor = {
      term: trimmedValue,
      matchMode: 'CONTAINS'
    };

    if (this.globalSearchColumns.length > 0) {
      descriptor.columns = [...this.globalSearchColumns];
    }

    return descriptor;
  }

  private buildFilterGroupPayload(): FilterGroup[] | undefined {
    if (!this.filterChips.length) {
      return undefined;
    }

    return [{
      operator: this.filterGroupOperator,
      filters: this.filterChips.map(chip => ({
        column: chip.column,
        operator: chip.operator,
        value: chip.value
      }))
    }];
  }

  private buildSearchFilters(): Array<{column: string, operator: ComparisonOperator, value: any}> {
    const fallbackColumns = this.globalSearchColumns.length > 0
      ? this.globalSearchColumns
      : this.columns.filter(col => this.isTextColumn(col.type)).map(col => col.name);

    if (fallbackColumns.length === 0) {
      return [];
    }

    return fallbackColumns.map(column => ({ column, operator: 'LIKE', value: `%${this.searchValue}%` }));
  }

  private isTextColumn(type: string): boolean {
    const lowerType = type.toLowerCase();
    return lowerType.includes('char') || lowerType.includes('varchar') || lowerType.includes('text') || lowerType.includes('clob');
  }

  private resetSearchBuilderState(): void {
    this.searchValue = '';
    this.globalSearchColumns = [];
    this.filterChips = [];
    this.filterGroupOperator = 'AND';
    this.globalSearchControl.setValue('', { emitEvent: false });
    this.filterBuilderForm.reset({ column: null, operator: 'LIKE', value: '' });
  }

  private createChipId(): string {
    const cryptoApi = globalThis.crypto;
    if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
      return cryptoApi.randomUUID();
    }
    return `chip-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
  }

  private getPrimaryKeyColumns(): ColumnMeta[] {
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
        this.apiUrl(`/api/dynamic-crud/primary-keys?table=${encodeURIComponent(tableName)}`)
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

  private apiUrl(path: string): string {
    if (!path.startsWith('/')) {
      return `${this.apiBaseUrl}/${path}`;
    }
    return `${this.apiBaseUrl}${path}`;
  }
}