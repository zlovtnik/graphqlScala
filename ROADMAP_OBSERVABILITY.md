# 🚀 SSF Application Enhancement Roadmap - Performance Monitoring & Analytics

## 📊 Performance Monitoring & Analytics

### **Priority 1: Observability Foundation (Prometheus + Grafana Dashboards)** 🔴

Foundation for all downstream monitoring and alerting; Phase 0 partially complete—extend and operationalize

- [ ] **Prometheus Metrics Collection & Export**
  - **Current State**: Micrometer + `micrometer-registry-prometheus` integrated; `BatchMetricsRecorder`, `QueryPlanAnalyzer`, `ComplianceMetricsService`, `PlsqlInstrumentationSupport` already exporting metrics
  - **Gaps**: Missing application-level request metrics (GraphQL resolver latency, query complexity distribution), missing JVM GC metrics aggregation, missing custom business metrics (login success rate, MFA enrollment)
  - **Concrete Tasks**:
    - [ ] **Extend Micrometer Instrumentation**:
      - Add `@Timed` interceptor to all GraphQL resolvers (`@GraphqlResolver` methods) via custom `GraphQLInstrumentation` + `MicrometerInstrumentation`; capture P50/P95/P99 per resolver
      - Add counters for resolver errors (`graphql.resolver.errors.total{resolver=<name>, error_type=<type>}`) and fields with auth failures
      - Export resolver context (dataloader batching, N+1 detection) as gauges: `graphql.dataloader.batch_size`, `graphql.query.n_plus_one_detector`
      - Integrate reactive metrics: emit `graphql.query.execution_time_ms{reactive=true/false}` tags to distinguish blocking vs non-blocking paths
    - [ ] **Collect JVM & Oracle JDBC Metrics**:
      - Enable Micrometer's JVM observability module for GC pause tracking (`jvm.gc.pause` P50/P95/P99), thread pool utilization (`jvm.threads.live`, `.peak`), memory pressure (`jvm.memory.used{area=heap}`)
      - Add Oracle JDBC metrics via `oracle.r2dbc.OracleR2DBCConnectionFactory` event listeners for prepared statement cache efficiency (`jdbc.prepared_statement_cache.hits` / `.misses`), array operation throughput
      - Export connection pool metrics via `HikariCP` native gauges (`hikaricp.connections{state=active|idle}`, `.connections.pending`, `.connections.timeout`)
    - [ ] **Custom Business Metrics**:
      - Add gauges for security/compliance: `ssf_mfa_enrollment_rate` (already exists), `ssf_audit_log_completeness` (already exists), `ssf_failed_login_rate_per_hour`, `ssf_data_encryption_coverage`
      - Add request volume counter: `http.requests.total{method, status, endpoint}` with 1-minute roll-up; track top N slowest endpoints
    - [ ] **Prometheus Scrape Configuration**:
      - Create `monitoring/prometheus/prometheus.yml` with Spring Actuator scrape config (`/actuator/prometheus`, interval: 15s for real-time, 1m for aggregation)
      - Add `monitoring/prometheus/alerts.yml` with baseline alert rules (see Alerting section below)
      - Document scrape SLO in `docs/observability/prometheus-scrape-strategy.md`
  - **Success Criteria**: `curl http://localhost:9090/metrics` returns 100+ distinct metric names; Prometheus UI shows all metrics with <30s staleness; no gaps in resolver latency or pool metrics

- [ ] **Grafana Dashboards & Visualization**
  - **Current State**: Only `compliance-dashboard.json` exists (MFA enrollment, audit completeness, encryption coverage, SOX status)
  - **Gaps**: No performance dashboard (API latency, throughput), no database dashboard (query execution, pool health), no GraphQL dashboard (resolver breakdown, complexity distribution), no system health dashboard
  - **Concrete Tasks**:
    - [ ] **Performance Dashboard** (`monitoring/grafana/performance-dashboard.json`):
      - **Section 1 – API Request Metrics**:
        - Graph: API response time P50/P95/P99 (Y-axis: ms, X-axis: time); overlay error rate on secondary axis (%)
        - Heatmap: Endpoint latency distribution (rows: endpoint paths, columns: time buckets, color intensity: response time)
        - Table: Top 10 slowest GraphQL queries (query hash, avg latency, P95, max, error count)
        - Gauge: Current request throughput (req/sec), target: >500 req/sec
      - **Section 2 – Resolver & Data Loading**:
        - Breakdown: Avg latency per resolver (bar chart, sorted by latency)
        - Breakdown: Query complexity distribution (histogram: P50/P95/P99 query complexity scores)
        - Counter: DataLoader batching efficiency (avg batch size, %hit rate on cached results)
        - Table: N+1 query detection (query pattern, occurrence count, impact latency ms)
      - **Section 3 – Cache Performance** (Redis + Caffeine):
        - Gauges: Cache hit rate (%), cache eviction rate per minute, avg TTL remaining
        - Graph: Cache miss impact on API latency (latency when cache hit vs miss)
        - Table: Top 10 cache keys by hit count
    - [ ] **Database Dashboard** (`monitoring/grafana/database-dashboard.json`):
      - **Section 1 – Connection Pool Health**:
        - Gauges: Active connections (%), idle connections, pending acquire queue depth
        - Graph: Connection acquire time P50/P95/P99 over time; alert if >2s
        - Counter: Connection timeouts, leaks (with link to stack trace logs)
      - **Section 2 – Query Performance** (from `QueryPlanAnalyzer`):
        - Graph: SQL execution time P50/P95/P99 per query type (SELECT, UPDATE, INSERT)
        - Heatmap: Slow query detection (rows: query patterns, columns: hourly buckets, color: count of queries >threshold)
        - Table: Top 10 slowest SQL statements (query text truncated, avg latency ms, exec count, last exec time)
      - **Section 3 – Stored Procedure Metrics** (from `PlsqlInstrumentationSupport`):
        - Graph: PL/SQL execution time P50/P95/P99 per procedure (module.action breakdown)
        - Counter: PL/SQL error rate by error code (with trend line)
        - Gauge: Procedure execution count (top 5 by invocation frequency)
      - **Section 4 – Oracle AWR Integration** (Phase 2+):
        - Link to Oracle AWR snapshot viewer UI (hosted via MinIO pre-signed URL)
        - Query performance analysis tab with recommended indexes
    - [ ] **GraphQL Observability Dashboard** (`monitoring/grafana/graphql-dashboard.json`):
      - **Section 1 – Query Metrics**:
        - Graph: Query execution time P50/P95/P99 (with query complexity overlay on secondary axis)
        - Gauge: Avg complexity score per query type (target: <5000 for 95%)
        - Table: Rejected queries (exceeded complexity threshold)
      - **Section 2 – Resolver Performance Breakdown**:
        - Flame graph simulation (bar chart: resolver latency stacked by depth)
        - Graph: Resolver cache hit rate over time
      - **Section 3 – Field-Level Auth**:
        - Counter: Fields with authorization failures (by field name, error type)
        - Timeline: Auth failures by user role
    - [ ] **System Health Dashboard** (`monitoring/grafana/system-health-dashboard.json`):
      - **Section 1 – Overview**:
        - Gauges: API availability (%), error rate (%), p99 latency (ms)
        - Status indicators: Database (UP/DOWN), Redis (UP/DOWN), MinIO (UP/DOWN)
      - **Section 2 – Alerts & Anomalies**:
        - Alerts table (timestamp, severity, service, message)
        - Anomaly detection results (baseline vs current for top queries/endpoints)
      - **Section 3 – Circuit Breaker State** (Resilience4j):
        - Status per breaker: database, redis, minio, auth-service, audit-service
        - Counter: State transitions (CLOSED→OPEN) per day
        - Graph: Failure rate trend (alert if >50%)
    - [ ] **Frontend Performance Dashboard** (`monitoring/grafana/frontend-dashboard.json`):
      - Lighthouse score trends (LCP, FID, CLS)
      - Bundle size tracking (JS, CSS, HTML uncompressed/gzipped/brotli)
      - User experience metrics: page load time, time to interactive
  - **Dashboard Configuration**:
    - [ ] Create templated Grafana provisioning config (`infra/docker/grafana/provisioning/dashboards/dashboard.yml`)
    - [ ] Auto-import dashboards on container startup via Docker Compose volumes
  - **Success Criteria**: 5+ Grafana dashboards live; all metrics display without errors; dashboards refresh <1s; avg panel load time <500ms

### **Priority 2: Application Performance Monitoring (APM)** 🟡

Distributed tracing, request correlation, bottleneck identification

- [ ] **OpenTelemetry / Distributed Tracing Integration**
  - **Setup**:
    - Integrate `io.opentelemetry:opentelemetry-api` + `opentelemetry-sdk` + exporter (Jaeger or Tempo)
    - Wire `otel:otel-exporter-otlp` for Jaeger/Tempo export
    - Add `@WithSpan` annotations to GraphQL resolvers, service methods, repository methods to capture latency and exceptions
    - Create `com.rcs.ssf.tracing.OtelConfig` bean to configure global tracer
  - **Concrete Tasks**:
    - [ ] **Request Correlation**:
      - Generate unique request ID (`X-Request-ID` header) at HTTP entry point; propagate via `ThreadLocal` to all service calls
      - Create span context from request ID; link all DB queries, cache lookups, external API calls to parent span
      - Export trace context to logs (JSON format: `trace_id`, `span_id`, `user_id`) for correlation in ELK
    - [ ] **Span Instrumentation**:
      - GraphQL resolvers: span name = `graphql.<query_or_mutation>.<field>`, attributes: query_complexity, resolver_latency_ms
      - Database queries: span name = `db.query.<procedure_or_sql_type>`, attributes: query_text_hash, row_count, execution_time_ms
      - Cache operations: span name = `cache.<operation>`, attributes: cache_name, hit/miss, value_size_bytes
      - MFA operations: span name = `mfa.<operation>`, attributes: mfa_method, status (success/failure)
    - [ ] **Latency Attribution** (bottleneck detection):
      - Create `com.rcs.ssf.tracing.LatencyAnalyzer` component to aggregate spans by service layer (HTTP → GraphQL → Service → DAO → JDBC)
      - Emit histogram metric `tracing.latency_by_layer{layer, resolver}` showing distribution of latency per layer
      - Export to Prometheus; trigger alert if any layer contributes >60% of total latency (indicates optimization opportunity)
    - [ ] **Jaeger / Tempo Deployment**:
      - Add Jaeger all-in-one container to `docker-compose.yml` (port 6831 UDP for spans, 16686 for UI)
      - Document Jaeger query examples in `docs/observability/distributed-tracing.md`
    - [ ] **Grafana Tracing Integration**:
      - Link Grafana to Jaeger datasource; enable trace browsing from metrics dashboard (click on slow request → view trace)
  - **Success Criteria**: Every API request traces end-to-end; traces visualizable in Jaeger UI; trace latency breakdown matches Prometheus metrics; no trace drops (collection success rate >99%)

- [ ] **Bottleneck Detection & Alerting**
  - **Queries**: Automated detection of slow queries via `QueryPlanAnalyzer` baseline regression (>5% variance from baseline triggers investigation alert)
  - **Resolvers**: Alert if resolver latency P95 increases >20% vs 7-day baseline
  - **Cache**: Alert if cache hit rate drops <70% or eviction rate spikes (indicates sizing issue)
  - **Database Pool**: Alert if avg acquire time >2s or leak detection threshold exceeded
  - **Implementation**: Create `com.rcs.ssf.metrics.BottleneckDetector` scheduled task (runs every 5 minutes) computing baselines and emitting alerts to `AUDIT_PERFORMANCE_ANOMALIES` table
  - **Success Criteria**: 3+ anomaly types detected; alerts surface in Grafana + audit table; false positive rate <5%

### **Priority 2 (continued): Database Performance Monitoring** 🟡

Oracle AWR integration, wait event monitoring, index effectiveness

- [ ] **Oracle AWR Report Integration & Visualization**
  - **Current State**: `PlsqlInstrumentationSupport` already hooks into `DBMS_MONITOR`; now integrate with AWR exports
  - **Concrete Tasks**:
    - [ ] **AWR Snapshot Collection & Export**:
      - Create PL/SQL job (`sql/jobs/awr_export_job.sql`) that runs every 24 hours (off-peak); exports AWR snapshots (last 24h) as JSON via Oracle REST API or direct query
      - Store JSON exports in MinIO (`awr-reports/` bucket) with metadata table (`AUDIT_AWR_EXPORTS`: snapshot_id, export_timestamp, report_url)
      - Document in `docs/observability/awr-export-strategy.md`
    - [ ] **Grafana Dashboard Integration**:
      - Create `monitoring/grafana/oracle-awr-dashboard.json`:
        - **Section 1 – AWR Summary**:
          - Embed link to latest AWR HTML report (hosted via MinIO pre-signed URL)
          - Display top wait events (table: event name, % of DB time, trend chart)
          - Display top SQL (table: SQL_ID, executions, avg latency ms, CPU%, physical reads)
        - **Section 2 – Wait Event Trends**:
          - Graph: CPU time vs DB time vs wait time (stacked area, 7-day history)
          - Breakdown by event class: Application, Concurrency, Commit, I/O, Other
        - **Section 3 – Instance Performance**:
          - Gauges: Load average, active sessions, DB CPU
          - Table: Background process activity (SMON, DBWR, etc.)
    - [ ] **Real-Time Wait Event Monitoring**:
      - Query `V$SESSION_WAIT_CLASS` every 60s via `PlsqlInstrumentationSupport.withAction(...)` + Micrometer gauge export
      - Emit `oracle.wait_event{event_name, class}` gauges for top 5 events
      - Alert if any wait event exceeds 30% of active session time
    - [ ] **SQL Performance Analytics**:
      - Query `V$SQL` + `V$SQL_PLAN` to extract query execution plans; store in `AUDIT_SQL_PERFORMANCE` table with hash, plan_xml, execution_count, avg_latency_ms
      - Detect plan changes (PQ degree, index usage) via plan_xml diff; emit alert with recommendations
  - **Success Criteria**: AWR report viewable in Grafana; wait events tracked in real-time; top SQL queries identified with execution plans; 1+ performance recommendations surfaced per week

- [ ] **Index Effectiveness & Query Optimization Recommendations**
  - **Tasks**:
    - [ ] **Index Usage Analysis**:
      - Query `DBA_INDEX_USAGE` every 24h; calculate utility metric = `(used_reads * weight_read - unused_write_cost * weight_write) / total_object_size`
      - Flag unused indexes (utility <10%) for potential removal; flag missing indexes on hot columns (high query count without matching index)
      - Export to `monitoring/grafana/index-effectiveness-dashboard.json`
    - [ ] **Slow Query Capture & Analysis**:
      - Enable Oracle extended statistics via `DBMS_STATS.GATHER_TABLE_STATS` (already in migration scripts); identify column correlation issues
      - Create `com.rcs.ssf.metrics.SlowQueryAnalyzer` to intercept queries >1s, capture execution plan, suggest indexes or hint rewrites
      - Store suggestions in `AUDIT_QUERY_RECOMMENDATIONS` table; surface in admin dashboard
  - **Success Criteria**: Missing indexes identified; unused indexes catalogued; query plan regressions detected within 24h

### **Priority 3: Log Aggregation & Analysis (ELK Stack)** 🟢

Centralized logging, structured logs, correlation with metrics/traces

- [ ] **ELK Stack Deployment**
  - **Setup**:
    - Add Elasticsearch 8.x + Kibana + Logstash containers to `docker-compose.yml`
    - Configure Logstash `logstash/config/pipelines.conf` to ingest logs from:
      - Spring Boot JSON logs (via `org.springframework.boot:spring-boot-starter-logging` + custom JSON formatter)
      - Oracle audit logs (archived from `AUDIT_*` tables via PL/SQL job)
      - Nginx access logs (if CDN integration deployed)
  - **Concrete Tasks**:
    - [ ] **Structured Logging in Spring Boot**:
      - Configure Logback XML (`src/main/resources/logback-spring.xml`) to output JSON format:

```json
{
  "timestamp": "2025-11-18T10:30:45Z",
  "level": "ERROR",
  "logger": "com.rcs.ssf.service",
  "message": "Database query failed",
  "trace_id": "abc123",
  "user_id": "42",
  "tags": ["database", "error"]
}
```      - Add MDC (Mapped Diagnostic Context) bindings for `trace_id`, `user_id`, `request_id` at HTTP filter level (`com.rcs.ssf.http.filter.TraceIdFilter`)
    - [ ] **Log Correlation**:
      - Wire `X-Request-ID` header → MDC `request_id`; propagate to Elasticsearch as `request_id` field for searching related logs
      - Link logs to metrics/traces: add `trace_id` to log JSON; Kibana/Grafana can cross-reference to Jaeger traces
    - [ ] **Index Strategy**:
      - Create daily indexes in Elasticsearch: `logs-app-yyyy.mm.dd`, `logs-oracle-yyyy.mm.dd`, `logs-nginx-yyyy.mm.dd`
      - Set retention policy: 30-day hot tier, 90-day warm (searchable) tier, 7-year cold tier (archival in S3)
    - [ ] **Kibana Dashboards**:
      - Create `kibana/dashboards/log-analysis.ndjson` with:
        - **Section 1 – Error Analysis**: errors by service, error rate trend, top error types
        - **Section 2 – User Activity**: login/logout/mutation activity by user, failed auth attempts
        - **Section 3 – Audit Trail**: all audit events (searchable by user_id, operation, timestamp)
        - **Section 4 – Performance Logs**: slow queries, slow API requests, timeouts (linked to Jaeger traces)
  - **Success Criteria**: Logs flowing into Elasticsearch within <1s; Kibana dashboards return results <2s; log retention policy automated via ILM

- [ ] **Compliance & Audit Log Archival**
  - **Tasks**:
    - [ ] **Immutable Audit Log Storage**:
      - Archive `AUDIT_*` tables weekly to MinIO (`audit-archive/year/month/day/AUDIT_*.parquet` format) for compliance retention (7 years for SOX)
      - Create PL/SQL job `sql/jobs/audit_archival_job.sql` that exports via `DBMS_DATAPUMP` or direct INSERT into archive table
      - Verify archival integrity: hash each archive file, store in `AUDIT_ARCHIVE_MANIFEST` with SHA-256 hash
    - [ ] **Log Retention Policies** (in `docs/compliance/log-retention.md`):
      - Production audit logs: 7 years on cold storage
      - Application logs (errors): 90 days on warm tier, then archive
      - Access logs (API/auth): 30 days on hot, 90 days warm
### **Priority 3 (continued): Alerting & Incident Response** 🟢

Real-time alerting, on-call escalation, runbooks

- [ ] **Alert Rule Definition & Execution**unbooks)*

- [ ] **Alert Rule Definition & Execution**
  - **Setup**:
    - Create `monitoring/alertmanager/config.yml` for alert routing (email, Slack, PagerDuty)
    - Create `monitoring/prometheus/alerts.yml` with baseline alert rules
  - **Concrete Alert Rules**:
    - [ ] **API Performance Alerts**:
      - P95 API latency >1s for 5 min → Slack notification (channel: #backend-perf)
      - Error rate >1% for 10 min → Page on-call engineer
      - Resolver timeout >30s → Immediate critical alert (indicates query complexity abuse or database hang)
    - [ ] **Database Alerts**:
      - Connection pool utilization >90% → Warning (channel: #database)
      - Connection acquire time >5s → Critical (indicates pool exhaustion)
      - Slow query detected (>threshold) 3+ times in 1h → Investigation alert (with query text + plan)
      - Oracle wait event >30% of load → Alert with event name + remediation link
    - [ ] **Cache Alerts**:
      - Cache hit rate <70% for 15 min → Investigate (possible sizing issue)
      - Cache eviction rate >50/s → Warning (memory pressure)
    - [ ] **Security & Compliance Alerts**:
      - Failed login rate >10 per minute → Investigation (possible brute force)
      - MFA bypass attempts (e.g., backup code exhaustion) → Critical
      - Unauthorized field access (GraphQL auth failures) >5 per minute → Investigation alert
      - Audit log write failure → Critical (compliance risk)
    - [ ] **Circuit Breaker Alerts**:
      - Any breaker in OPEN state >30s → Investigation (service degradation)
      - Circuit breaker opens >3x in 24h → Alert (indicates systemic issue)
  - **Implementation**:
    - [ ] Create Prometheus alert rules in YAML (`monitoring/prometheus/alerts.yml`), organized by severity (critical, warning, info)
    - [ ] Test alert rules via Prometheus test runner before deployment
    - [ ] Document alert runbooks in `docs/runbooks/alert-response.md` (what each alert means, investigation steps, remediation)
  - **Success Criteria**: Alerts firing correctly; <5% false positive rate; runbooks address each alert type; team responds to critical alerts <15 minutes

- [ ] **Automated Incident Response & Escalation**
  - **Tasks**:
    - [ ] **PagerDuty Integration**:
      - Wire Alertmanager to PagerDuty API for critical incidents (Severity = CRITICAL)
      - Create escalation policy: On-call engineer → On-call manager (15 min) → CTO (30 min)
      - Auto-create incident with alert details, runbook link, Grafana dashboard link
    - [ ] **Incident Tracking**:
      - Log all incidents (created alerts) to `AUDIT_INCIDENT_RESPONSE` table with timestamp, alert_id, severity, responder_id, resolved_at
      - Track MTTR (Mean Time To Resolution) per alert type via Prometheus histogram
    - [ ] **Postmortem Automation**:
      - Create incident summary template (`docs/runbooks/incident-postmortem-template.md`) with critical incidents
      - Require postmortem within 48h; document root cause, remediation, prevention measures
  - **Success Criteria**: Critical alerts surface in PagerDuty within <1min; MTTR <30min for database-related incidents; <5 min for API latency alerts

### **Priority 4: Advanced Observability (Real-Time Dashboards, Anomaly Detection)** 🟢

Streaming metrics, ML-based anomaly detection, predictive alerting

- [ ] **Real-Time Metrics Streaming (WebSocket-based Dashboard)**
  - Allow WebSocket subscription to live metrics (latency, throughput, errors) for real-time on-screen updates
  - Emit delta updates every 5s to subscribed clients via Spring GraphQL reactive streams
  - Render in Angular dashboard with live line charts (no page reload)
  - Success Criteria: Dashboard updates <500ms latency; no lag for 100+ concurrent subscribers

- [ ] **ML-Based Anomaly Detection**
  - Train baseline models on 7-day metric history (baseline API latency, query execution time, error rate)
  - Detect anomalies: if current value >1.5x baseline + 3σ, emit anomaly alert
  - Implementation: Use lightweight statsmodels-like logic (mean + std deviation) or integrate lightweight TensorFlow Lite model
  - Success Criteria: Detect 80%+ of anomalies with <2% false positive rate

- [ ] **Predictive Alerting** (opt-in)
  - Predict resource exhaustion (e.g., pool saturation) 30 min in advance based on linear regression trend
  - Alert before capacity breach occurs, allowing proactive scaling
  - Success Criteria: 70%+ prediction accuracy; false positives <10%
