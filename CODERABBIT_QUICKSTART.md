#!/bin/zsh
# CodeRabbit Summary Instructions — Quick Reference

# FILE: .coderabbit-summary-instructions.md
# USE: Copy the instructions into CodeRabbit Pro "high_level_summary_instructions" setting
# LOCATION: Project root directory

# FILE: CODERABBIT_BRAINSTORM.md
# USE: Ten strategic improvements for SSF GraphQL platform PR summaries
# INCLUDES:
#   1. Multi-Stack Integration Matrix (backend/frontend/database/infra)
#   2. Compliance & Audit Trail Section (GDPR/SOX/MFA tracking)
#   3. Performance Baselines & Regression Detection
#   4. Migration Risk Scorecard (DBA-friendly)
#   5. Frontend Code Generation Impact
#   6. Observable / Metrics Export Checklist
#   7. Environment Validation & Deployment Readiness
#   8. Breaking Changes Impact & Mitigation
#   9. Test Evidence & Coverage Trends
#  10. Rollback & Incident Response Plan

# FILE: EXAMPLE_CODERABBIT_PR.md
# USE: Reference example showing how to apply all sections to a real PR
# DEMONSTRATES: MFA TOTP enrollment feature (backend + frontend)
# SHOWS: Complete PR summary using all sections

═══════════════════════════════════════════════════════════════════════════════

## 🚀 QUICK START — Apply to Your Next PR

### Step 1: Copy Summary Instructions to CodeRabbit Pro

1. Go to CodeRabbit.ai dashboard
2. Settings → Organization → high_level_summary_instructions
3. Paste content from `.coderabbit-summary-instructions.md`
4. Save

### Step 2: Create/Analyze a PR

1. Open/create a new PR on GitHub
2. CodeRabbit will auto-generate summary using your instructions
3. Review summary; adjust instructions if sections are unclear

### Step 3: Iterate Based on Feedback

1. Collect feedback from reviewers for 2–3 PRs
2. Update instructions in `.coderabbit-summary-instructions.md` based on:
   - Missing sections (e.g., "we always need performance impact")
   - Unclear wording (e.g., "what does 'risk level' mean?")
   - Incomplete examples
3. Re-save to CodeRabbit

═══════════════════════════════════════════════════════════════════════════════

## 📋 SIX-SECTION TEMPLATE (Core Summary Structure)

Every PR summary should include:

### 1. 📝 Executive Summary (50–70 words)
What changed and why, in plain English. One paragraph.

### 2. 🔧 Technology & Components Affected
Table of affected files/components with change type + impact.

### 3. 📦 Dependencies & Configuration Changes
New/updated dependencies, environment variables, migrations, configs.

### 4. 🔐 Security & Compliance Impact
Auth changes, data protection, audit logging, compliance implications.

### 5. 🧪 Test Coverage & Quality Assurance
Test counts, coverage trends, performance benchmarks, E2E scenarios.

### 6. ✔️ Pre-Deployment Checklist & Reviewer Notes
Deployment order, sign-offs, manual testing, rollback plan, incident response.

═══════════════════════════════════════════════════════════════════════════════

## 🎯 WHEN TO USE ADDITIONAL SECTIONS (From Brainstorm)

**Backend-Only PR**: Skip frontend sections; focus on security + database.

**Frontend-Only PR**: Skip backend sections; flag if Apollo codegen required.

**Full-Stack PR**: Include 🔄 Stack Impact Matrix; emphasize integration points.

**Database Migration PR**: Add 🗄️ Migration Risk Scorecard; DBA sign-off critical.

**Performance/Observability PR**: Add 📊 Performance Baselines; flag regressions.

**Breaking Changes**: Add 🚨 Breaking Changes Impact section with migration plan.

═══════════════════════════════════════════════════════════════════════════════

## 📊 TEMPLATE BY PR TYPE

### Backend + Frontend (Full-Stack) Changes
```
✅ Use all 6 core sections
✅ Add Stack Impact Matrix (4 layers: backend, database, frontend, infra)
✅ Add Frontend Code Generation Impact (if schema changed)
✅ Explicitly call out integration points
```

### Database Migration PR
```
✅ Use all 6 core sections
✅ Add Migration Risk Scorecard (backward compat, rollback, lock duration)
✅ Include exact migration filenames (V###__description.sql)
✅ DBA review is REQUIRED
```

### Security / Compliance PR
```
✅ Use all 6 core sections
✅ Add Compliance & Audit Trail Section (GDPR/SOX/MFA checklist)
✅ Audit logging details (what events, retention policy, PII safeguards)
✅ Security team review is REQUIRED
```

### Performance / Observability PR
```
✅ Use all 6 core sections
✅ Add Performance Baselines & Regression Detection table
✅ Add New Metrics & Observability (Prometheus, Grafana, health checks)
✅ Gatling load test results required
```

### Breaking Change PR
```
✅ Use all 6 core sections
✅ Add Breaking Changes Impact section (old vs new, migration steps)
✅ Clear deprecation timeline (e.g., v1.1 + old, v1.2 remove old)
✅ Frontend/client impact explicitly documented
```

═══════════════════════════════════════════════════════════════════════════════

## 🔧 CONFIGURATION FOR CODERABBIT PRO

### File: .coderabbit.yaml (in repo root)

```yaml
summary:
  high_level_summary_instructions: |
    [PASTE ENTIRE CONTENT OF .coderabbit-summary-instructions.md HERE]
  
  high_level_summary_in_walkthrough: false
  include_affected_files_summary: true
  security_focus: true
  highlight_breaking_changes: true

rules:
  - pattern: "src/main/resources/graphql/schema.graphqls"
    description: "GraphQL schema changes require frontend regeneration"
    reviewers: ["@frontend-team", "@backend-team"]

  - pattern: "db/migration/V*.sql"
    description: "Database migrations need DBA review + rollback plan"
    reviewers: ["@dba-team"]

  - pattern: "frontend/src/app/**/*.ts"
    description: "Frontend changes; verify no API contract breaks"
    reviewers: ["@frontend-team"]

  - pattern: "docker-compose.yml"
    description: "Infrastructure changes; update deployment docs"
    reviewers: ["@devops-team"]
```

═══════════════════════════════════════════════════════════════════════════════

## 💡 WHY EACH SECTION MATTERS (For Different Stakeholders)

### 📝 Executive Summary
- **Audience**: All reviewers, non-technical stakeholders
- **Benefit**: Instant understanding of scope; prevents "what is this PR for?" questions

### 🔧 Technology & Components Affected
- **Audience**: Code reviewers (backend, frontend, DBA)
- **Benefit**: Exact files to review; risk level signals priority

### 📦 Dependencies & Configuration
- **Audience**: DevOps, QA, Operations
- **Benefit**: Environment setup clear; deployment doesn't fail on "missing var"

### 🔐 Security & Compliance
- **Audience**: Security, compliance, legal teams
- **Benefit**: Audit trails captured; GDPR/SOX gaps caught early

### 🧪 Test Coverage
- **Audience**: QA, architects
- **Benefit**: Confidence in code quality; regression trends visible

### ✔️ Pre-Deployment Checklist
- **Audience**: On-call engineers, DevOps, DBAs
- **Benefit**: Clear deployment order; sub-2-minute incident response

═══════════════════════════════════════════════════════════════════════════════

## 📈 ROLLOUT SCHEDULE

### Week 1: Standardize Core Sections
- [ ] Use 6-section template for 3 backend PRs
- [ ] Gather feedback from reviewers
- [ ] Adjust section wording based on feedback

### Week 2: Add Domain-Specific Sections
- [ ] MFA/Security PRs → Add Compliance & Audit
- [ ] Database PRs → Add Migration Risk Scorecard
- [ ] Full-stack PRs → Add Stack Impact Matrix

### Week 3: Optimize & Automate
- [ ] Create PR template with CodeRabbit fields
- [ ] Add automation (extract coverage, migration names, etc.)
- [ ] Link to Copilot Instructions

### Week 4: Rollout & Iterate
- [ ] Publish to team; link in README.md "Contributing" section
- [ ] Collect feedback at sprint retro
- [ ] Update quarterly as project evolves

═══════════════════════════════════════════════════════════════════════════════

## 📚 DOCUMENTATION FILES

| File | Purpose | Audience |
|------|---------|----------|
| `.coderabbit-summary-instructions.md` | Core 6-section template + style guide | All developers |
| `CODERABBIT_BRAINSTORM.md` | 10 strategic improvements + implementation roadmap | Tech leads, architects |
| `EXAMPLE_CODERABBIT_PR.md` | Real MFA TOTP PR showing all sections applied | All developers (reference) |
| `.github/copilot-instructions.md` | Backend conventions (existing) | Backend developers |
| `frontend/.github/copilot-instructions.md` | Frontend conventions (existing) | Frontend developers |

═══════════════════════════════════════════════════════════════════════════════

## ✅ VALIDATION CHECKLIST FOR EACH PR SUMMARY

### Before Publishing PR:

- [ ] **Executive Summary**: 1 paragraph, 50–70 words, explains "what changed" and "why"
- [ ] **Components Affected**: Table includes all files touched (backend, frontend, DB, infra)
- [ ] **Dependencies**: Lists new/updated packages, env vars, migrations in order
- [ ] **Security**: Auth changes explained; audit logging documented; GDPR/SOX noted
- [ ] **Tests**: Coverage metrics shown; failure scenarios clear; performance impact quantified
- [ ] **Pre-Deploy**: Checklist itemized; sign-offs listed; rollback plan included
- [ ] **Breaking Changes**: If any, in separate section with migration timeline
- [ ] **Links**: All docs linked (SECURITY_ARCHITECTURE.md, MFA_IMPLEMENTATION.md, etc.)

### If ANY section is missing or incomplete:
- → Not ready for review; return to author

### If ALL sections present and clear:
- → Ready for code review

═══════════════════════════════════════════════════════════════════════════════

## 🎓 KEY INSIGHTS FOR YOUR PROJECT

### 1. Multi-Stack Complexity
Your project has 5+ layers (Spring Boot backend, Angular frontend, Oracle DB, Redis cache, MinIO storage, Prometheus/Grafana). PR summaries must surface **integration points** to avoid siloed reviews.

### 2. Compliance-First Design
GDPR/SOX roadmap is front-and-center. Every PR should explicitly state compliance impact. "No PII added" is not good enough; include audit trail details.

### 3. Performance Baselines Essential
PROJECT_PLAN emphasizes performance as Phase 1 deliverable. Gatling + Prometheus mean every PR needs performance metrics (P50/P95/P99, not just "pass/fail").

### 4. Database Migrations Are High-Risk
Flyway auto-runs on startup. Migrations can lock tables, cause downtime, break rollbacks. DBA must review **every** migration, even small ones.

### 5. Frontend Codegen Dependency
GraphQL schema → Apollo codegen → TypeScript types → component bindings. If backend schema changes, frontend **must** regenerate or types go stale. CI must fail if codegen is out-of-sync.

───────────────────────────────────────────────────────────────────────────────

## 🚀 NEXT STEPS

1. **Copy `.coderabbit-summary-instructions.md` content to CodeRabbit Pro settings** ← START HERE
2. **Apply to next 2–3 PRs** and gather feedback from reviewers
3. **Update sections** based on feedback (clarity, missing fields, etc.)
4. **Create `.coderabbit.yaml`** to automate file-level review rules
5. **Link in README.md** under "Contributing" so team discovers instructions
6. **Measure**: Track review time, post-deploy issues, missed findings over 4 weeks

═══════════════════════════════════════════════════════════════════════════════

**Version**: 1.0 | **Date**: November 2025 | **Owner**: SSF Platform Team

Questions? Reach out to @backend-team or @frontend-team for clarification on specific sections.
