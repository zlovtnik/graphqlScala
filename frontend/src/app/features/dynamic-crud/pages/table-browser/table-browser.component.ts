import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { Subject, Observable } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { ModalService } from '../../../../core/services/modal.service';

interface TableData {
  [key: string]: any;
}

interface TableColumn {
  name: string;
  type: string;
  nullable: boolean;
}

interface DynamicCrudRequest {
  tableName: string;
  operation: 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE';
  columns?: Array<{name: string, value: any}>;
  filters?: Array<{column: string, operator: string, value: any}>;
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
export class TableBrowserComponent implements OnInit, OnDestroy {
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
  private modalService!: ModalService;

  constructor(
    private http: HttpClient,
    private message: NzMessageService,
    private router: Router
  ) {
    this.modalService = inject(ModalService);
  }

  ngOnInit(): void {
    this.loadAvailableTables();
  }

  ngOnDestroy(): void {
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

    const columns = Object.keys(this.formData).map(key => ({
      name: key,
      value: this.formData[key]
    }));

    const request: DynamicCrudRequest = {
      tableName: this.selectedTable,
      operation: 'UPDATE',
      columns,
      filters: [{
        column: 'id', // Assuming ID column
        operator: '=',
        value: this.editingRecord['id']
      }]
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
  }

  handleDelete(record: TableData): void {
    if (!this.selectedTable) return;

    const request: DynamicCrudRequest = {
      tableName: this.selectedTable,
      operation: 'DELETE',
      filters: [{
        column: 'id', // Assuming ID column
        operator: '=',
        value: record['id']
      }]
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
  }

  exportData(): void {
    // Simple CSV export
    if (this.tableData.length === 0) return;

    const headers = this.columns.map(col => col.name).join(',');
    const rows = this.tableData.map(row =>
      this.columns.map(col => {
        let value = row[col.name] || '';
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

  private buildSearchFilters(): Array<{column: string, operator: string, value: any}> {
    if (this.selectedSearchColumn) {
      return [{ column: this.selectedSearchColumn, operator: 'LIKE', value: `%${this.searchValue}%` }];
    } else {
      const textColumns = this.columns.filter(col => this.isTextColumn(col.type));
      if (textColumns.length > 0) {
        return textColumns.map(col => ({ column: col.name, operator: 'LIKE', value: `%${this.searchValue}%` }));
      } else {
        return [];
      }
    }
  }

  private isTextColumn(type: string): boolean {
    const lowerType = type.toLowerCase();
    return lowerType.includes('char') || lowerType.includes('varchar') || lowerType.includes('text') || lowerType.includes('clob');
  }
}