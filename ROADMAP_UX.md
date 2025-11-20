# 🚀 SSF Application Enhancement Roadmap - UX/UI Enhancements

## Overview

This roadmap outlines comprehensive improvements for UX/UI and Oracle database performance optimization. The application is a Spring Boot 3 GraphQL backend with Angular frontend, featuring JWT authentication, Oracle database integration, and MinIO object storage.

## 📊 Current State Assessment

- **Backend**: Well-architected Spring Boot 3 with GraphQL, Oracle JDBC integration via stored procedures
- **Frontend**: Angular 18 with NG-Zorro Ant Design components
- **Database**: Oracle with dynamic CRUD operations, audit logging
- **Performance**: Basic HikariCP (max 5 connections), Gatling load tests exist
- **Security**: JWT authentication, audit trails, but limited RBAC

---

## 🎯 UX/UI Enhancements

### **Priority 1: Core Feature Completeness** 🔴

- [ ] **Rebuild Main Dashboard** (frontend `pages/main.component.*`, backend `DashboardService`)
  - [x] Wire total users / active sessions / login attempts / system health cards to `/api/dashboard/stats` so UI is backed by live Oracle counts instead of mock data.
  - [x] Ship actionable CTAs (Users, Settings, Dynamic CRUD, Logout) and basic alert banners fed by `DashboardStats`.
  - [x] Replace the temporary `chartData` bars with a real charting lib (e.g., `ngx-echarts`) sourced from audit aggregates so trends and performance metrics reflect `audit_login_attempts`.
  - [x] Stream stats via HTTP polling (5-second intervals) with GraphQL subscription fallback so cards + alerts auto-refresh without page reload. Configured in `DashboardService` with polling on `/api/dashboard/stats`.
  - [ ] Surface system health + dependency alerts from `/actuator/health`, `Resilience4jConfig`, and MinIO/Redis health contributors instead of only calculating in the component.

- [ ] **Implement Dynamic CRUD Interface** (frontend `features/dynamic-crud/table-browser`, backend `DynamicCrudController`)
  - [x] Build the table browser with pagination, client-side sorting, column-aware filtering, and CSV export backed by `/api/dynamic-crud/execute`.
  - [x] Provide insert/update/delete modals plus primary-key introspection, optimistic refresh, and keyboard shortcuts.
  - [ ] Generate dynamic forms from server-provided metadata (data types, nullable, FK relationships) so validation rules mirror Oracle constraints instead of generic text inputs.
  - [ ] Add bulk operations (multi-row insert/update/delete) with progress feedback and audit logging routed through `DynamicCrudGateway`.
  - [ ] Implement JSON/CSV import with validation + dry-run preview, then reuse the existing export helpers for roundtrips.

- [ ] **Complete User Management System** (backend `UserController`/`UserService`, frontend `features/users`)
  - [x] Expose REST endpoints for CRUD with validation + password hashing; reuse dynamic CRUD fallbacks when JDBC unavailable.
  - [ ] Build the `/users` Angular feature module (list view, detail drawer, create/edit modals) that consumes `/api/users` and GraphQL, using `NzTable` for pagination/search/filtering.
  - [ ] Add role / permission modeling to `User` (service + repository) and surface assignments in the UI with guardrails tied into `CustomUserDetailsService`.
  - [ ] Wire up activity log panes per user by querying `audit_login_attempts` / `audit_sessions`, with date filters and export.
  - [ ] Implement client-side validation + async uniqueness checks to mirror `ensureUsernameAvailable` / `ensureEmailAvailable`.

- [ ] **Add Settings & Profile Management** (frontend `pages/settings`, backend MinIO + auth services)
  - [x] Provide theme switching + PWA install prompts on the dashboard via `ThemeService`/`PwaService`.
  - [ ] Stand up a dedicated `/settings` route (replace placeholder loader) with sub-pages for profile, preferences, API keys, and notifications.
  - [ ] Build a profile screen that uploads avatars to MinIO (respecting `MinioConfig`) and exposes password reset flow backed by existing auth endpoints.
  - [ ] Persist user preferences (theme, language, notification toggles) via GraphQL mutations + Redis-backed cache for fast reads.
  - [ ] Add API key lifecycle management plus account deactivate/reactivate flows, documenting new env vars in `README.md`/`HELP.md`.

- [ ] **Implement Navigation & Guards** (frontend `authGuard`, layout shell)
  - [x] Enforce authentication on private routes via `authGuard` that waits for `AuthService` to finish resolving state.
  - [ ] Layer role-aware guards once RBAC lands so `/dynamic-crud` etc. honor server-side permissions.
  - [ ] Add global route-level loading states/skeletons and breadcrumb navigation inside the layout so transitions are clear.
  - [ ] Provide error boundaries + fallback pages for GraphQL/REST failures (leverage Angular error handlers + `nz-result`).

### **Priority 2: Usability & Accessibility** 🟡

- [ ] **Progressive Web App Features**
  - Service worker for offline functionality
  - Web app manifest for native-like experience
  - Push notifications support
  - Install prompt for desktop/mobile

- [ ] **Enhanced Theme System**
  - Persistent theme storage (localStorage)
  - System theme detection (prefers-color-scheme)
  - Custom theme builder for branding
  - Theme switcher in header/navigation

- [ ] **Keyboard Shortcuts & Accessibility**
  - Global keyboard shortcuts (Ctrl+K for search, etc.)
  - Full keyboard navigation support
  - Screen reader compatibility
  - High contrast mode support

- [ ] **Toast Notifications System**
  - Success/error/info/warning notifications
  - Auto-dismiss with configurable timing
  - Action buttons in notifications
  - Notification history and management

- [ ] **Responsive Design Audit**
  - Mobile-first responsive design
  - Tablet optimization
  - Touch gesture support
  - Mobile navigation patterns

### **Priority 3: Advanced Features** 🟢

- [ ] **Data Export/Import System**
  - CSV/Excel export with custom formatting
  - Bulk import wizards with validation
  - Progress indicators for large operations
  - Error reporting and recovery

- [ ] **Real-time Updates**
  - WebSocket integration for live data
  - GraphQL subscriptions for real-time updates
  - Live activity feeds and notifications
  - Real-time collaboration features

- [ ] **Advanced Search & Filtering**
  - Global search across all entities
  - Saved search filters and queries
  - Advanced filtering with multiple criteria
  - Search result highlighting

- [ ] **User Activity & Notifications**
  - Activity timeline for user actions
  - Notification center with categories
  - Email/SMS notification preferences
  - Notification templates and customization

- [ ] **Onboarding & Guidance**
  - Interactive onboarding tours
  - Feature introduction tooltips
  - Help documentation integration
  - Video tutorials and guides

---
