# Security & Compliance

This section outlines the complete security roadmap in phases. See **Security & Compliance Delivery Plan** table below for detailed phase breakdown, key deliverables, and compliance hooks.

## Security & Compliance Delivery Plan

| Phase | Focus | Key Deliverables | Test & Compliance Hooks |
|-------|-------|------------------|-------------------------|
| **Phase 0 – Foundations & Readiness** | Requirements capture, architecture guardrails | Auth flow inventory (`SecurityConfig`, `JwtAuthenticationFilter`), Angular UX spikes for MFA/admin consoles, observability placeholders for MFA/encryption metrics, regulatory acceptance criteria + data-classification matrix | Update `docs/IMPLEMENTATION_SUMMARY.md`, add Grafana placeholders, document baseline controls for GDPR/SOX |
| **Phase 1 – Multi-Factor Authentication Stack** | End-to-end MFA (TOTP, SMS, WebAuthn, recovery) | New `service/security/mfa` module, QR provisioning + rate-limited verification, SMS provider integration with Resilience4j, WebAuthn registration/assertion flow, backup code lifecycle + admin overrides, CI + Cypress coverage, env var docs | `WebGraphQlTester` unit tests, frontend E2E flows, audit logging for every MFA event, README/HELP updates |
| **Phase 2 – Advanced Audit & Compliance** | Operator visibility + policy enforcement | Flyway migrations for normalized audit events, GraphQL admin viewer with filtering/export, MinIO-based archiving jobs, compliance report templates (GDPR/SOX), automated retention + deletion pipelines documented in `infra/cronjobs` | Integration tests covering export + retention, docs in `docs/CSP_IMPLEMENTATION.md`, Grafana dashboards for audit volumes |
| **Phase 3 – Data Encryption & Security** | TDE enablement + app-level crypto | Oracle TDE rollout with DBA runbook, `EncryptionService` for sensitive DTO fields, HSM/KMS integration + rotation workflows, Micrometer timers for crypto latency, regression + load tests validating impact | Runbooks in `docs/optimization/`, metrics + alerts for encryption latency, key-rotation audits |
| **Phase 4 – Advanced RBAC & Governance** | Granular permissions + governance UX | New RBAC schema + Flyway scripts, policy engine cached via Caffeine/Redis, GraphQLInstrumentation hooks for field-level enforcement, admin UI for dynamic assignment, permission audit reports + approval workflow, frontend guard directives | Scenario tests for deny paths, Cypress admin coverage, Grafana + audit tables for RBAC changes |

Each phase gates on prior compliance and documentation updates; rollout should use feature toggles for gradual enablement and adhere to security runbooks before promotion.