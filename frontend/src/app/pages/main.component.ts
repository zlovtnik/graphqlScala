import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzStatisticModule } from 'ng-zorro-antd/statistic';
import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { Subject, Observable } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService, User } from '../core/services/auth.service';
import { ThemeService } from '../core/services/theme.service';
import { DashboardService, DashboardStats } from '../core/services/dashboard.service';

@Component({
  selector: 'app-main',
  standalone: true,
  imports: [
    CommonModule,
    NzCardModule,
    NzButtonModule,
    NzGridModule,
    NzIconModule,
    NzStatisticModule,
    NzAvatarModule
  ],
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class MainComponent implements OnInit, OnDestroy {
  currentUser: User | null = null;
  stats$: Observable<DashboardStats>;
  private destroy$ = new Subject<void>();

  private authService = inject(AuthService);
  protected themeService = inject(ThemeService);
  private router = inject(Router);
  private dashboardService = inject(DashboardService);

  constructor() {
    // Initialize stats$ from dashboard service with error handling
    this.stats$ = this.dashboardService.getStats();
  }

  ngOnInit(): void {
    // Subscribe to user changes with proper cleanup
    // Route access is controlled by authGuard
    this.authService.getCurrentUser$()
      .pipe(takeUntil(this.destroy$))
      .subscribe((user: User | null) => {
        this.currentUser = user;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  navigateToUsers(): void {
    this.router.navigate(['/users']);
  }

  navigateToDynamicCrud(): void {
    this.router.navigate(['/dynamic-crud']);
  }

  navigateToSettings(): void {
    this.router.navigate(['/settings']);
  }
}
