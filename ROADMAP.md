# 🚀 SSF Application Enhancement Roadmap

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
- [ ] **Rebuild Main Dashboard**
  - Implement real-time statistics cards (user count, active sessions, system health)
  - Add charts for audit logs, user activity trends, database performance metrics
  - Create quick action buttons for common tasks
  - Add system status indicators and alerts

- [ ] **Implement Dynamic CRUD Interface**
  - Build table browser with pagination, sorting, filtering
  - Create dynamic form generator for CRUD operations
  - Add bulk operations support (bulk insert, update, delete)
  - Implement data validation and error handling
  - Add export/import functionality (CSV, JSON)

- [ ] **Complete User Management System**
  - Finish `/users` page with comprehensive data table
  - Add user creation/editing forms with validation
  - Implement user roles and permissions management
  - Add user search and filtering capabilities
  - Create user activity logs viewer

- [ ] **Add Settings & Profile Management**
  - User profile page with avatar upload to MinIO
  - Password change functionality with strength validation
  - User preferences (theme, language, notifications)
  - API keys management for external integrations
  - Account deactivation/reactivation

- [ ] **Implement Navigation & Guards**
  - Add proper route guards for authentication
  - Implement loading states and skeletons
  - Add breadcrumb navigation
  - Create error boundaries and fallback pages

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

## ⚡ Backend Performance Optimizations (Oracle DB Focus)

### **Priority 1: Observability & Prerequisites** 🔴
*(Foundation for all downstream optimizations and tuning)*

- [ ] **Metrics & Performance Monitoring Infrastructure**
  - Integrate Prometheus for application and JVM metrics
  - Export HikariCP metrics (active, idle, pending connections, wait time)
  - Track Oracle JDBC metrics (prepared statement cache hit/miss, array operations)
  - Add custom business metrics (API response times P50/P95/P99, error rates by endpoint)
  - Configure metric scrape intervals (15s for real-time, 1m for aggregation)

- [ ] **Database Connection Pool Monitoring**
  - Add real-time dashboards for pool utilization, wait times, and connection age
  - Implement connection leak detection alerts (connections held >5min idle)
  - Add pool pressure indicators (queue depth, saturation thresholds)
  - Configure Grafana dashboards for pool health visualization
  - Set up alerting for pool degradation (utilization >80%, wait time >2s)

- [ ] **Query Result Streaming & Memory Optimization**
  - Add streaming responses for large datasets (cursor-based pagination)
  - Implement query result chunking to reduce memory footprint
  - Add memory pressure monitoring and heap usage tracking
  - Implement automatic result set chunk size tuning
  - Add streaming export functionality (CSV, JSON) for large result sets

- [ ] **Connection Leak Detection & Diagnostics**
  - Enable HikariCP leak detection with 60s threshold
  - Add thread dump capture on leak detection events
  - Implement automated leak reporting to logging infrastructure
  - Create leak analysis dashboards (connection origin, age, owner thread)
  - Set up automated alerts with stack traces for investigation

- [ ] **Optimize HikariCP Configuration**
  - Increase max-pool-size from 5 to 20-50 for production based on concurrency target
  - Add connection validation and health checks (validationQuery: `SELECT 1 FROM DUAL`)
  - Enable prepared statement caching (oracle.jdbc.implicitStatementCacheSize: 25)
  - Configure connection timeout (30s), max lifetime (30min), idle timeout (10min)
  - Configure leak detection threshold (60s) and enable test on borrow

- [ ] **Enable Oracle Fast Connection Failover**
  - Configure Oracle DataSource with FAN (Fast Application Notification)
  - Implement connection pool failover strategies (affinity, load balancing)
  - Add connection state monitoring and recovery (3-second recovery SLA)
  - Enable RAC-aware connection selection

- [ ] **Database Indexing Strategy**
  - Analyze slow queries using Oracle AWR reports
  - Add composite indexes for common query patterns (e.g., `(table_name, created_at)` for audit queries)
  - Implement index monitoring and maintenance (rebuild threshold 10% imbalance)
  - Add covering indexes for audit table queries to avoid table lookups
  - Create index performance dashboards tracking usage and efficiency

### **Priority 1 (continued): Core Caching & Optimization** 🔴

- [ ] **Implement Multi-Level Caching**
  - Application-level caching with Caffeine (max 500 entries, 10min TTL)
  - Distributed caching with Redis for session data (session TTL: token lifetime)
  - Database query result caching (invalidation strategy: on-write)
  - HTTP response caching with ETags for read-only endpoints
  - Cache warm-up strategies for critical queries

- [ ] **Optimize JDBC Batch Operations**
  - Increase batch_size from 25 to 100-500 based on memory profiling
  - Implement batch retry logic with exponential backoff (max 3 retries)
  - Add batch performance monitoring (throughput, error rates)
  - Optimize batch vs individual operation decisions (break-even: ~50 rows)
  - Monitor memory pressure during batch operations

### **Priority 2: Stored Procedure Optimization** 🟡
- [ ] **Profile PL/SQL Package Performance**
  - **AWR/ASH workflow**: Schedule weekly AWR/ASH snapshot reviews for `dynamic_crud_pkg`, `user_pkg`, and any ad-hoc packages touched by GraphQL resolvers; document the top SQL_IDs and wait events with recommended fixes in Confluence before each sprint demo.
  - **Cursor + array audit**: Enable `DBMS_PROFILER` on critical packages for one full load test run, flag implicit cursor usage >5% of total execution time, and rewrite to explicit BULK COLLECT/FORALL where it removes context switches.
  - **PL/SQL result cache**: Baseline hit/miss ratio using `V$RESULT_CACHE_STATISTICS`, then add `RESULT_CACHE RELIES_ON (...)` clauses for deterministic functions; target >80% cache hit rate for read-heavy packages.
  - **Runtime instrumentation**: Add `DBMS_APPLICATION_INFO.set_action` plus custom `DBMS_MONITOR.SERV_MOD_ACT_STAT_ENABLE` hooks so Prometheus exporters can emit per-package latency, executions, and error counts.

- [ ] **Implement Oracle Optimizer Hints**
  - **Hint catalog**: Create a living catalog that maps each high-cost statement to approved hints (e.g., `/*+ GATHER_PLAN_STATISTICS */`, `LEADING`, `USE_NL`), and store it alongside the package specs to keep code reviews consistent.
  - **Parallel operations**: For ETL-style procedures processing >1M rows, test `/*+ PARALLEL(table 4) */` during Gatling load runs and ensure CPU headroom stays below 70% before enabling in production.
  - **Index awareness**: Coordinate with the indexing roadmap to align `INDEX(table index_name)` hints with the newest composite indexes; add guardrails that automatically disable hints when `DBA_INDEX_USAGE` shows <5% utilization.
  - **Query rewrite + plan baselines**: Leverage `/*+ RESULT_CACHE */`, `MATERIALIZE`, and SQL Plan Management baselines; capture explain plans pre/post deployment to guarantee regressions are caught in CI.

- [ ] **Optimize Array Processing**
  - **OracleArrayUtils profiling**: Run Java Flight Recorder + Mission Control on batch-heavy endpoints to capture allocation hotspots inside `OracleArrayUtils`; convert any reflection-based copying to direct `System.arraycopy` or driver-native APIs.
  - **Direct JDBC array ops**: Where stored procedures accept associative arrays, switch to `oracle.jdbc.OraclePreparedStatement#setARRAY` / `setPlsqlIndexTable` so the driver skips intermediate object creation.
  - **Chunking policy**: Introduce adaptive chunk sizes (default 2,000 rows, max 10,000) driven by heap pressure metrics; log chunk decisions for later tuning.
  - **Memory guardrails**: Emit Micrometer gauges for array buffer utilization and pause the producer when Eden occupancy exceeds 80% to avoid GC spikes.

- [ ] **Implement Query Result Streaming**
  - **Server-side cursors**: Enable `setFetchSize(500)` and `ResultSet.TYPE_FORWARD_ONLY` on long-running queries, wiring them into Spring GraphQL data fetchers via `StreamSupport` so consumers can subscribe without loading everything into memory.
  - **Cursor-based pagination**: Standardize on opaque `pageState` tokens (table PK + `ROWID`) returned from stored procedures; add contract tests that validate monotonic ordering and deterministic replays.
  - **Export pipelines**: Build a reusable exporter that pipes JDBC `ResultSet` rows into `SseEmitter`/`ZipOutputStream` so CSV/JSON exports stream incrementally, keeping memory usage flat.
  - **Back-pressure + monitoring**: Surface per-stream throughput, client disconnects, and cursor lifetime metrics; auto-close any cursor idle for >30s.

- [ ] **Database Connection Pool Monitoring**
  - **Metrics + tracing**: Expand the Micrometer registry with gauges for active/idle pool size, wait duration histograms, and connection age; propagate pool context via `TracingDataSource` so slow stored procedures can be tied to specific pool events.
  - **Leak detection**: Set `leakDetectionThreshold=60000`, emit structured logs with stack traces, and push them into Loki/ELK; add PagerDuty alerts when more than three leaks fire within 15 minutes.
  - **Dashboards**: Publish Grafana dashboards showing utilization, queue depth, average borrow time, and 95th percentile acquisition latency; tag panels by environment (dev/stage/prod).
  - **Auto-scaling policy**: Feed pool metrics into the Platform HPA (or custom controller) that scales the Spring Boot pods between 2 and 6 replicas; tie max pool size to `CPU >70%` and `wait time >2s` rules so the pool grows predictably.

### **Priority 3: Advanced Oracle Features** 🟢
- [ ] **Enable Oracle Advanced Compression**
  - **Audit table DDL**: Rebuild `AUDIT_*` tables online with `ROW STORE COMPRESS ADVANCED`, snapshot row counts pre/post, and keep rollback scripts under `sql/tables/rollback/`.
  - **Index compaction**: Convert high-cardinality indexes to `COMPRESS ADVANCED LOW`, capture `DBA_INDEX_USAGE` before/after, and alert if logical reads increase >5%.
  - **Historical tiers**: Move partitions older than 12 months into `COMPRESS FOR ARCHIVE HIGH` tablespaces; document retention windows per compliance requirement.
  - **Compression observability**: Add nightly job that logs compression ratios, CPU overhead, and IO savings into Grafana; define rollback trigger if CPU >70% during peak.

- [ ] **Implement Oracle In-Memory Column Store**
  - **Sizing + pool carve-out**: Reserve 10–15% of SGA for `INMEMORY_SIZE`, document approval from DBA lead, and script auto-tuning based on AWR hit ratios.
  - **Heat maps + population**: Tag top audit fact tables and materialized views with `INMEMORY PRIORITY HIGH DISTRIBUTE AUTO`; enable heat map to confirm access frequency.
  - **Query rewrites**: Update critical analytics packages to use vector transformation hints (`/*+ VECTOR_TRANSFORM */`) and verify plan changes via SQL Monitor.
  - **Runtime monitoring**: Export IM column store statistics (population status, evictions, IMCU compression) to Prometheus; fail deployment if eviction rate exceeds 2%/hr.

- [ ] **Database Partitioning Strategy**
  - **Range partition design**: Partition `AUDIT_*` tables by `created_at` monthly, align local indexes, and codify naming convention (`P_YYYY_MM`).
    - Update `sql/tables/audit_*.sql` templates plus new Flyway script `db/migration/V220__audit_monthly_partitions.sql` so prod + lower envs share the same canonical DDL.
    - Add companion index script (`V221__audit_partition_indexes.sql`) to keep local indexes aligned with each partition and assert the naming rule in a Flyway callback.
  - **Pruning validation**: Add regression tests that capture `EXPLAIN PLAN` output to confirm `PARTITION RANGE ITERATOR` usage for top 5 queries.
    - Create SQL harness under `sql/tests/partitioning/` to snapshot `EXPLAIN PLAN FOR <query>`; pipe results into `src/test/java/com/rcs/ssf/db/PartitionPlanTest.java` using Testcontainers + Oracle XE.
    - Fail the test suite if the plan omits `PARTITION RANGE ITERATOR` or touches >2 partitions so CI protects against regressions.
  - **Automated maintenance**: Build Flyway tasks that create next-quarter partitions, merge old ones into archive storage, and purge >24 month partitions.
    - Implement `db/migration/R__partition_rollover.sql` (Repeatable) that reads partition metadata via PL/SQL and auto-creates `P_<YYYY_MM>` partitions 90 days ahead.
    - Add `scripts/partition-maintenance.sh` invoked by `cronjob.yaml` to merge and purge partitions, emitting metrics to Grafana via `healthcheck.sh` hook.
  - **Partition-wise joins**: Refactor ETL procedures to leverage `PARTITION-WISE HASH` joins; document optimizer hints and monitor elapsed time deltas.
    - Touch `sql/packages/dynamic_crud_pkg_body.sql` and `sql/packages/user_pkg_body.sql` to add `/*+ PARTITION(WISE HASH) */` hints plus chunked processing.
    - Capture elapsed time deltas inside `oracle_profiler` tables and surface them via a new Grafana panel so tuning wins stay visible.

- [ ] **Oracle RAC Optimization**
  - **Connection affinity**: Configure UCP/ONS with `RuntimeLoadBalancingFeature` and `ONSConfiguration` so Spring DataSource pins write workloads to local instances.
    - Extend `src/main/resources/application-prod.yml` with UCP stanza (ONS hosts, FAN enabled) and wire custom `RacAwareDataSourceConfig` bean under `com.rcs.ssf.config`.
    - Document rollout steps in `docs/rac-playbook.md`, including credential rotation and fallback plan.
  - **Load balancing**: Enable server-side load balancing with SCAN listeners; run Gatling failover drills and measure reconnection <3s.
    - Update `docker-compose.yml` dev topology to simulate multi-node RAC via SCAN VIP aliases for early testing.
    - Add Gatling scenario `gatling/src/gatling/scala/RacFailoverSimulation.scala` that forces node failure (via `sql/ops/kill_session.sql`) and asserts <3s reconnect.
  - **RAC telemetry**: Ship GV$ views (e.g., `GV$GES_BLOCKING_ENQUEUE`) into Grafana; define alerts for high interconnect latency (>5 ms) or block waits.
    - Publish new Micrometer `Gauge` exporters under `com.rcs.ssf.metrics.RacTelemetryCollector` plus Prometheus scrape config `monitoring/prometheus.yml` snippet.
    - Create Grafana dashboard JSON (`monitoring/grafana/rac.json`) with latency + block wait panels and alert rules stored in `monitoring/grafana/rac-alerts.json`.
  - **Interconnect tuning**: Validate jumbo frames + QoS on the interconnect VLAN, document `oradebug ipc` baselines, and re-run after each network change.
    - Automate `oradebug ipc` capture via Ansible playbook `infra/ansible/rac-interconnect.yml`; archive results in `infra/runbooks/rac-interconnect.md`.
    - Add CI gate that requires latest baseline (<=30 days old) before promoting networking changes.

- [ ] **Database Change Notification**
  - **DCN plumbing**: Use `oracle.jdbc.dcn.DatabaseChangeRegistration` with secure callbacks, store subscription metadata per resolver, and auto-renew before TTL expiration.
    - Introduce `com.rcs.ssf.dcn.DcnRegistrar` service with unit tests in `src/test/java/com/rcs/ssf/dcn/DcnRegistrarTest.java`; persist metadata to `AUDIT_DCN_SUBSCRIPTIONS` via Flyway script `V230__audit_dcn_metadata.sql`.
    - Secure callbacks by mounting credentials in `k8s/overlays/prod/dcn-secret.yaml` and validating TLS mutual auth.
  - **Cache invalidation**: Wire Micronaut/Spring cache evictions to DCN payloads, ensuring row-level filters so only touched entities flush.
    - Extend `CacheInvalidationListener` to parse row payloads and evict `Caffeine` + Redis entries by composite key; add contract tests in `CacheInvalidationListenerTest` using mocked payloads.
  - **Filtering + QoS**: Apply query-level selectors (columns + where clause) and set QoS to `QUERY` to avoid flooding clients; load-test 1k notifications/minute.
    - Encode selectors in `sql/dcn/dcn_selectors.sql` and validate QoS via integration scenario `gatling/src/gatling/scala/DcnQoSSimulation.scala` (target 1k msg/min sustained).
  - **Observability + retries**: Emit metrics for missed notifications, re-subscribe on ORA-29970, and create runbooks for rotating DCN credentials.
    - Add Micrometer counters (`dcn.notified`, `dcn.retries`) plus structured logs for ORA codes.
    - Document manual retry + credential rotation steps in `docs/runbooks/dcn.md` and ensure PagerDuty alerts trigger when retries exceed 3/min.

### **Application Performance Optimizations**

- [ ] **GraphQL Query Optimization**
  - **Persisted Queries Implementation**:
    - Create registry for 50+ common queries mapped to hash IDs; store in Redis with versioning strategy (`v1`, `v2` for evolution).
    - Implement `PersistedQueryRegistry` service under `com.rcs.ssf.graphql.persistence` with database schema (`V225__persisted_queries.sql`) for audit trail.
    - Add APQ (Automatic Persisted Queries) fallback with configurable `apollo-server` client support; measure client adoption rate via Prometheus counter (`graphql.persisted_queries.hit_ratio`).
    - Document persisted query format in `docs/graphql/persisted-queries.md` with client integration examples (Apollo, Relay).
  - **Query Complexity Analysis**:
    - Integrate `graphql-java-extended-scalars` + custom complexity visitor; assign default complexity per type (scalar: 1, object: 10, list: 50); validate query complexity on every request.
    - Reject queries exceeding threshold (configurable, default 5000); return error with actual complexity and reduction recommendations in response.
    - Log rejected queries to `AUDIT_GRAPHQL_COMPLEXITY` table; create Grafana alert if rejection rate exceeds 5/min (indicates client abuse or legitimate scaling issue).
    - Add instrumentation endpoint (`GET /actuator/graphql/complexity-stats`) exposing P50/P95/P99 query complexity distribution and rejection counts.
  - **Query Execution Plan Caching**:
    - Cache parsed GraphQL plans (AST + execution path) in Caffeine (max 10k entries, 1-hour TTL); bypass parser for 95%+ hit rate on repeated queries.
    - Benchmark parser performance: capture baseline (current mean parse time), measure speedup post-caching; target 100x improvement on cache hits.
    - Add cache statistics to Prometheus (`graphql.execution_plan.cache.hits`, `.misses`, `.evictions`); monitor eviction rate (alert if >10%/min).
    - Store plan cache configuration in `application.yml` with environment-specific tuning (dev: small cache, prod: large cache).
  - **Success Criteria**: P50 query execution time reduced by 30%; cache hit rate >85%; rejection rate for overly complex queries <1%

- [ ] **Response Compression & Optimization**
  - **GZIP Compression Setup**:
    - Enable Spring Boot built-in compression (`spring.compression.enabled: true`, `min-response-size: 1024` bytes); configure compression level (default 6, range 1–9).
    - Add custom `CompressionFilter` under `com.rcs.ssf.http.filter` to handle edge cases (streaming responses, large result sets).
    - Measure compression ratio per content type (GraphQL JSON, audit CSV, static assets); track in Prometheus gauge (`http.response.compression_ratio`).
    - Document compression tuning in runbook `docs/runbooks/response-compression.md` (CPU vs bandwidth tradeoff).
  - **Brotli Compression for Modern Clients**:
    - Integrate `brotli4j` Maven dependency; add conditional Brotli encoder registration in `HttpEncodingAutoConfiguration`.
    - Detect client support via `Accept-Encoding` header; prioritize Brotli (7x better than GZIP for text) for modern browsers (Chrome, Firefox, Safari 11+).
    - Benchmark compression CPU overhead: establish baseline CPU usage and alert if Brotli encoding causes >10% CPU spike during load testing.
    - Create A/B test scenario in Gatling (`gatling/src/gatling/scala/CompressionSimulation.scala`) comparing GZIP vs Brotli throughput and latency.
  - **Response Size Optimization**:
    - Profile response payloads: use `spring-boot-actuator` + custom metrics to emit response size histograms (`http.response.size.bytes` P50/P95/P99).
    - Implement field masking in GraphQL schema: add `@Partial` directive to omit unnecessary nested objects (e.g., `user.audit_logs` on list endpoints); reduce median response size by 40%.
    - Add automatic response truncation for large collections: paginate by default (page size 50), reject requests for >10k items without explicit chunking.
    - Document best practices in `docs/graphql/response-optimization.md` (field selection, pagination, projection).
  - **Success Criteria**: 60% median response size reduction; compression CPU <8% of total; Brotli adoption >70% of modern clients

- [ ] **Reactive Programming Migration**
  - **Spring WebFlux Pilot Program**:
    - Pilot WebFlux on 2–3 low-risk, read-heavy endpoints (e.g., `GET /audit-logs/summary`) using `@GetExchange` reactive annotations.
    - Create baseline latency + thread utilization metrics for blocking endpoints; re-measure post-migration (target: 50% reduction in thread pool contention).
    - Implement `ReactiveDataSourceConfiguration` using `oracle.r2dbc.OracleR2DBCConnectionFactory` (R2DBC driver for Oracle 12.2+).
    - Document migration pattern in `docs/reactive/migration-guide.md` with pitfalls (blocking I/O in reactive pipeline, subscription leaks).
  - **Reactive Database Operations**:
    - Convert high-volume audit insert operations from blocking `jdbcTemplate.batchUpdate()` to R2DBC `r2dbc.execute()` with backpressure handling.
    - Implement `ReactiveAuditService` under `com.rcs.ssf.service.reactive` using Project Reactor's `Flux` / `Mono` for non-blocking database access.
    - Add subscription timeout (default 30s) and retry logic with exponential backoff (max 3 retries, base 100ms).
    - Benchmark memory usage (reactive uses less heap for concurrent streams); measure GC pause reduction (target: <50ms P95 pause time).
  - **Thread Pool Optimization**:
    - Profile thread usage via JFR (Java Flight Recorder) during load test; capture thread allocation patterns, context switches, and blocking points.
    - Tune Netty event loop pool size: set to `cpu_cores * 2` for CPU-bound, `cpu_cores * 4–8` for I/O-bound workloads; validate via thread histogram (`jdk.ThreadPark` events).
    - Configure Reactor scheduler: set `reactor.netty.ioWorkerCount`, `reactor.netty.ioSelectCount` for optimal I/O throughput; measure latency P99 <200ms on reactive endpoints.
    - Document thread pool tuning in `src/main/resources/application-prod.yml` with environment-specific configs (dev: 4 threads, prod: 16–32 threads).
  - **Success Criteria**: 50% reduction in thread pool contention on reactive endpoints; GC pause time P95 <50ms; latency P99 <200ms on reactive GET endpoints

- [ ] **Resilience & Circuit Breakers**
  - **Resilience4j Circuit Breaker Setup**:
    - Integrate Resilience4j Maven dependency; define circuit breakers for 5 critical services (Oracle DB, Redis, MinIO, external auth, audit service).
    - Configure per-breaker thresholds: `failureRateThreshold: 50%`, `slowCallRateThreshold: 100%`, `slowCallDurationThreshold: 2s`, `minimumNumberOfCalls: 10` (enter OPEN after 5 consecutive failures).
    - Wire breakers into GraphQL resolvers via `@CircuitBreaker` annotation; log state transitions (CLOSED → OPEN → HALF_OPEN) to `AUDIT_CIRCUIT_BREAKER_EVENTS` table for post-incident analysis.
    - Expose circuit breaker state via Prometheus metrics (`resilience4j_circuitbreaker_state`, `resilience4j_circuitbreaker_calls_total`); create Grafana dashboards per breaker.
  - **Retry Mechanisms with Exponential Backoff**:
    - Implement `@Retry` annotation with strategy: `maxAttempts: 3`, `waitDuration: 100ms`, `intervalFunction: exponential(multiplier: 2, randomizationFactor: 0.5)`.
    - Add jitter to prevent thundering herd (retry storms); validate via Gatling scenario `RetryStormSimulation.scala` with 1000 concurrent clients triggering retries.
    - Log all retry attempts with context (attempt #, error reason, next retry time) to structured logs (JSON format for ELK parsing).
    - Document retry policies per service in `docs/resilience/retry-strategy.md` (which operations are idempotent, which are not).
  - **Bulkhead Pattern for Resource Isolation**:
    - Define bulkheads for 3 resource pools: `audit-operations` (25 threads), `user-operations` (15 threads), `graphql-queries` (50 threads).
    - Configure via `@Bulkhead(name = "audit-operations", type = THREADPOOL)` with `maxThreadPoolSize`, `coreThreadPoolSize`, `queueCapacity` tuned per service.
    - Monitor bulkhead saturation: emit Prometheus gauges (`resilience4j_bulkhead_max_allowed_concurrent_calls`, `.current_concurrent_calls`); alert if utilization >80%.
    - Add queue rejection telemetry; log rejected requests with reason (bulkhead full, timeout) for analysis and tuning.
  - **Fallback Strategies**:
    - Implement `@Fallback` methods for degraded mode: e.g., audit service down → return cached/empty audit trail with `DEGRADED_MODE: true` flag in response.
    - Create fallback chain: primary endpoint → cached response → default empty response; document fallback SLA per endpoint (e.g., audit queries may be 5+ minutes stale).
    - Test fallback activation via chaos engineering scenario: kill dependency service, verify fallback invocation, measure response time degradation (<500ms increase acceptable).
    - Create runbook `docs/runbooks/degraded-mode.md` documenting fallback activation, duration, and escalation steps.
  - **Success Criteria**: Circuit breaker prevents cascading failures; retry success rate >95%; bulkhead prevents thread pool starvation; fallback responses served <500ms

- [ ] **HTTP Caching & Optimization**
  - **Cache-Control Headers Implementation**:
    - Add `CacheControlHeaderFilter` under `com.rcs.ssf.http.filter` generating per-endpoint headers: immutable assets (`Cache-Control: public, max-age=31536000, immutable`), audit queries (`Cache-Control: private, max-age=300`), real-time endpoints (`Cache-Control: no-cache, no-store`).
    - Annotate resolvers with custom `@Cacheable` metadata: `@GraphqlCacheable(maxAge = 300, isPublic = false, vary = ["Authorization"])` to auto-generate correct headers.
    - Validate headers via integration test `CacheControlHeaderTest` using Spring Mock MVC; assert correct `Cache-Control`, `Vary`, `ETag` headers on 20+ critical endpoints.
    - Document caching strategy per entity type in `docs/caching/http-cache-strategy.md` (user data: 5min, audit: 10min, static: 1yr).
  - **ETag & Conditional Request Support**:
    - Generate ETags from response hash (SHA-256 of serialized JSON); wire into `ResponseEntityExceptionHandler` to return `304 Not Modified` if `If-None-Match` matches.
    - Add conditional request logic: on `If-Modified-Since` / `If-None-Match`, compute ETag and compare with client header; short-circuit database query if match found.
    - Benchmark ETag generation overhead: target <1% CPU increase; cache ETags in Redis with 5-min TTL to avoid repeated computation.
    - Add Prometheus metric `http.conditional_requests.hits` to measure 304 response rate; target >15% cache hit rate on repeated queries.
  - **CDN Integration Preparation**:
    - Configure CDN-friendly cache headers: set `Cache-Control: public` for unauthenticated endpoints, add `Surrogate-Key` headers for fine-grained cache invalidation.
    - Implement cache invalidation webhooks: on audit log write, emit `PURGE /api/v1/audit-logs` to CDN; document in `docs/cdn/cache-invalidation.md`.
    - Create CDN routing rules config under `infra/cdn/routing-rules.yaml`: origin, cache TTL, header manipulation, compression settings.
    - Add CDN performance metrics dashboard: track origin bandwidth, CDN bandwidth (savings %), cache hit ratio; alert if hit ratio <70%.
  - **Static Resource Delivery Optimization**:
    - Fingerprint frontend assets (add content hash to filename: `app.abc123def.js`); serve with far-future `Cache-Control` headers (1 year).
    - Configure nginx `gzip_static on` to pre-compress assets during build; add Brotli pre-compression for `.js`, `.css`, `.json` files.
    - Implement SubResource Integrity (SRI) hashes for CDN-sourced libraries; document in `frontend/tsconfig.json` build configuration.
    - Measure frontend bundle size post-optimization; track via CI metric `frontend.bundle.size.bytes`; alert if >500KB (uncompressed).
  - **Success Criteria**: 30% reduction in origin bandwidth; 304 Not Modified rate >15%; CDN cache hit ratio >85%; frontend bundle <500KB (gzipped)

- [ ] **Advanced Query Plan Analysis & Execution Tracing**
  - **Query Plan Caching & Inspection**:
    - Capture GraphQL query execution plans (resolver chain, data loader batching, database queries) on every request; store sampled plans (1 in 100) in time-series database (InfluxDB or Prometheus).
    - Implement custom Spring Data instrumentation to log each JDBC statement with execution time, row count, and connection acquisition time; correlate with GraphQL plan.
    - Create Grafana dashboard showing top 10 slowest queries, resolver execution breakdown (p50/p95/p99 per resolver), and data loader efficiency (N+1 query detection).
    - Build anomaly detection: alert if query plan changes significantly (>5% execution time variance unexplained) post-deployment.
  - **Success Criteria**: Automated detection of query regressions; <3s p95 execution time on all queries

---

---

## 🔧 Essential Features to Add

### **Security & Compliance**

This section outlines the complete security roadmap in phases. See **Security & Compliance Delivery Plan** table below for detailed phase breakdown, key deliverables, and compliance hooks.

#### Security & Compliance Delivery Plan

| Phase | Focus | Key Deliverables | Test & Compliance Hooks |
|-------|-------|------------------|-------------------------|
| **Phase 0 – Foundations & Readiness** | Requirements capture, architecture guardrails | Auth flow inventory (`SecurityConfig`, `JwtAuthenticationFilter`), Angular UX spikes for MFA/admin consoles, observability placeholders for MFA/encryption metrics, regulatory acceptance criteria + data-classification matrix | Update `docs/IMPLEMENTATION_SUMMARY.md`, add Grafana placeholders, document baseline controls for GDPR/SOX |
| **Phase 1 – Multi-Factor Authentication Stack** | End-to-end MFA (TOTP, SMS, WebAuthn, recovery) | New `service/security/mfa` module, QR provisioning + rate-limited verification, SMS provider integration with Resilience4j, WebAuthn registration/assertion flow, backup code lifecycle + admin overrides, CI + Cypress coverage, env var docs | `WebGraphQlTester` unit tests, frontend E2E flows, audit logging for every MFA event, README/HELP updates |
| **Phase 2 – Advanced Audit & Compliance** | Operator visibility + policy enforcement | Flyway migrations for normalized audit events, GraphQL admin viewer with filtering/export, MinIO-based archiving jobs, compliance report templates (GDPR/SOX), automated retention + deletion pipelines documented in `infra/cronjobs` | Integration tests covering export + retention, docs in `docs/CSP_IMPLEMENTATION.md`, Grafana dashboards for audit volumes |
| **Phase 3 – Data Encryption & Security** | TDE enablement + app-level crypto | Oracle TDE rollout with DBA runbook, `EncryptionService` for sensitive DTO fields, HSM/KMS integration + rotation workflows, Micrometer timers for crypto latency, regression + load tests validating impact | Runbooks in `docs/optimization/`, metrics + alerts for encryption latency, key-rotation audits |
| **Phase 4 – Advanced RBAC & Governance** | Granular permissions + governance UX | New RBAC schema + Flyway scripts, policy engine cached via Caffeine/Redis, GraphQLInstrumentation hooks for field-level enforcement, admin UI for dynamic assignment, permission audit reports + approval workflow, frontend guard directives | Scenario tests for deny paths, Cypress admin coverage, Grafana + audit tables for RBAC changes |

Each phase gates on prior compliance and documentation updates; rollout should use feature toggles for gradual enablement and adhere to security runbooks before promotion.

### **Developer Experience**

- [ ] **API Documentation & Testing**
  - OpenAPI/Swagger specification generation
  - Interactive API documentation
  - Postman collection generation
  - API testing and mocking tools

- [ ] **Health Checks & Monitoring**
  - Detailed health indicators for all dependencies
  - Custom health checks for business logic
  - Health check dashboards and alerting
  - Dependency health visualization

- [ ] **Metrics & Observability**
  - Prometheus metrics integration
  - Distributed tracing with OpenTelemetry
  - Application performance monitoring
  - Custom business metrics

- [ ] **Development Environment**
  - Docker Compose for full development stack
  - Hot reload configuration for all components
  - Database seeding and test data
  - Development-specific configuration profiles

### **Production Readiness**

- [ ] **Database Migrations & Versioning**
  - Flyway integration for schema migrations
  - Migration testing and rollback capabilities
  - Schema versioning and documentation
  - Migration performance optimization

- [ ] **Configuration Management**
  - External configuration server (Spring Cloud Config)
  - Encrypted configuration properties
  - Configuration validation and monitoring
  - Environment-specific configuration management

- [ ] **Backup & Recovery**
  - Automated database backup strategies
  - Point-in-time recovery capabilities
  - Backup validation and testing
  - Disaster recovery planning

- [ ] **Load Testing & Performance**
  - Expanded Gatling test scenarios
  - Performance regression testing
  - Load testing automation in CI/CD
  - Performance benchmarking tools

---

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
        - Table: Top 10 slowest SQL statements (query text truncated, avg latency, exec count, last exec time)
      - **Section 3 – Stored Procedure Metrics** (from `PlsqlInstrumentationSupport`):
        - Graph: PL/SQL execution time P50/P95/P99 per procedure (module.action breakdown)
        - Counter: PL/SQL error rate by error code (with trend line)
        - Gauge: Procedure execution count (top 5 by invocation frequency)
      - **Section 4 – Oracle AWR Integration** (Phase 2+):
        - Link to Oracle AWR snapshot viewer UI (when AWR export is complete)
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
```

      - Add MDC (Mapped Diagnostic Context) bindings for `trace_id`, `user_id`, `request_id` at HTTP filter level (`com.rcs.ssf.http.filter.TraceIdFilter`)
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
      - Create incident summary template (`docs/runbooks/incident-postmortem-template.md`) for critical incidents
      - Require postmortem within 48h; document root cause, remediation, prevention measures
  - **Success Criteria**: Critical alerts surface in PagerDuty within <1min; MTTR <30min for database-related incidents; <5 min for API latency alerts

### **Priority 4: Advanced Observability (Real-Time Dashboards, Anomaly Detection)** 🟢

Streaming metrics, ML-based anomaly detection, predictive alerting

- [ ] **Real-Time Metrics Streaming (WebSocket-based Dashboard)**
  - Allow WebSocket subscription to live metrics (latency, throughput, errors) for real-time on-screen updates
  - Emit delta updates every 5s to subscribed clients via Spring WebFlux reactive streams
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

---

## 🚀 Implementation Roadmap & Sprint Allocation

### **Phase 2.1: Observability Sprint (Weeks 5–6)** 🔴

Extend Prometheus + build Grafana dashboards (extend Phase 0 foundation)

| Task | Owner | Effort | Dependencies | Success Criteria |
|------|-------|--------|--------------|-----------------|
| **Extend Micrometer Instrumentation (Resolvers + JDBC)** | Backend | 3 days | `BatchMetricsRecorder` exists | GraphQL resolver latency exported; 30+ new metrics visible in Prometheus |
| **Build 5 Core Grafana Dashboards** | DevOps/Backend | 4 days | Prometheus scrape config | Performance, Database, GraphQL, System Health, Frontend dashboards live; all panels render |
| **Prometheus Alert Rules (v1)** | DevOps | 2 days | Alertmanager config | 20+ alert rules defined; tested; no syntax errors |
| **OpenTelemetry Integration Spike** | Backend | 2 days | Jaeger container ready | Traces flowing to Jaeger; sample trace viewable in UI |

**Deliverables**: 5 Grafana dashboards live, 20+ alert rules, OpenTelemetry pipeline operational.

### **Phase 2.2: Distributed Tracing & Bottleneck Detection (Weeks 6–7)** 🟡

| Task | Owner | Effort | Dependencies | Success Criteria |
|------|-------|--------|--------------|-----------------|
| **Complete OpenTelemetry Instrumentation** | Backend | 3 days | Jaeger integration spike done | All resolvers, services, repos traced; traces show latency breakdown by layer |
| **Implement Bottleneck Detector** | Backend | 2 days | QueryPlanAnalyzer exists | Anomalies detected; alerts emitted; false positive rate <5% |
| **AWR Integration (Phase 1)** | DBA | 3 days | Oracle AWR snapshot collection | Top wait events, top SQL exported; Grafana dashboard displays metrics |
| **Link Grafana to Jaeger** | DevOps | 1 day | Jaeger + Grafana configured | Clicking slow request in Grafana opens Jaeger trace |

**Deliverables**: End-to-end tracing operational, bottleneck detection live, AWR integration phase 1 complete.

### **Phase 2.3: ELK Stack & Compliance Logging (Weeks 7–8)** 🟢

| Task | Owner | Effort | Dependencies | Success Criteria |
|------|-------|--------|--------------|-----------------|
| **Structured Logging Implementation** | Backend | 2 days | JSON formatter library | All logs in JSON format; trace_id/request_id propagated |
| **ELK Deployment & Integration** | DevOps | 3 days | Infrastructure provisioned | Elasticsearch receiving logs; Kibana dashboards populated |
| **Audit Log Archival Automation** | DBA | 2 days | Archive strategy documented | Weekly archival job runs; archives validated |
| **PagerDuty Integration** | DevOps | 1 day | Alert rules defined | Critical alerts trigger PagerDuty incidents |

**Deliverables**: ELK stack operational, centralized logging live, alert escalation to PagerDuty functional.

---

## 🎯 Success Metrics (Post-Phase 2)

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Observability Coverage** | 95% of code paths traced | Jaeger trace completeness audit |
| **Alert Precision** | <5% false positive rate | Track alert accuracy in Prometheus |
| **MTTR (Mean Time To Resolution)** | <15 min for critical incidents | PagerDuty incident log analysis |
| **Log Ingestion Latency** | <1s log-to-Kibana | Sample 100 logs, measure end-to-end time |
| **Metric Query Latency** | <500ms for any Grafana panel | Monitor Prometheus/Grafana response times |
| **Anomaly Detection Rate** | 80%+ of anomalies detected | Compare detected vs manual discovery over 1 week |

---

## 🎯 Implementation Timeline & Priorities

### **Phase 1: Foundation (Weeks 1–4) — Parallel Tracks with Dependencies**

**Overall Goal**: Establish observability foundation, complete core UX, and lock in performance baselines to enable Phase 2–4 optimization.

**Team Allocation**: 4.3 FTE (Backend 1.5, Frontend 1.0, DBA 0.5, DevOps 0.5, QA 0.5, PM 0.3)

#### **Sprint 1.1 (Weeks 1–2): Infrastructure & Observability (CRITICAL PATH)**

*These items unblock all downstream work and must run in parallel with UX sprint.*

| Task | Owner | Effort | Dependencies | Success Criteria |
|------|-------|--------|--------------|-----------------|
| **Set up Prometheus + Grafana** | DevOps | 3 days | Infrastructure access | Metrics collected for app, JVM, HikariCP; 3 sample dashboards live |
| **Configure HikariCP Monitoring** | Backend | 2 days | Prometheus setup | Pool metrics (active/idle/pending) exported; SLA: <2s alert on wait >2s |
| **Establish Performance Baselines** | QA | 2 days | Gatling + test env | Baseline P50/P95/P99 for all API endpoints recorded; memory profile established |
| **Database Indexing Audit** | DBA | 3 days | AWR access | Composite indexes created for audit queries; index stats dashboard ready |
| **Set up CI/CD for Performance Tests** | DevOps + QA | 2 days | CI/CD access | Gatling tests run on every merge; failure threshold defined (P95 +5%) |

**Deliverables**: Prometheus/Grafana live, baselines locked in, automated regression detection active.

#### **Sprint 1.2 (Weeks 2–3): Core UX Completeness (Frontend + Backend in Parallel)**

*Can proceed once observability is ready; unblocked by Phase 1.1.*

| Task | Owner | Effort | Dependencies | Success Criteria |
|------|-------|--------|--------------|-----------------|
| **Dashboard Rebuild (Real-time Stats)** | Frontend | 5 days | Design mockups approved | Stats cards show live user count, active sessions, system health; refresh <500ms |
| **Dynamic CRUD Table Browser** | Frontend + Backend | 6 days | API schema ready | Table selection, pagination, sorting, filtering working; CSV export functional |
| **User Management Page** | Frontend + Backend | 4 days | User CRUD API | User list, add/edit/delete forms; role assignment working |
| **Notifications System** | Frontend + Backend | 3 days | Notification service spec | Toast notifications, notification history, action callbacks working |
| **Keyboard Shortcuts & Search** | Frontend | 2 days | KeyboardService skeleton | Ctrl+/ opens shortcuts help; Ctrl+K focuses search input |
| **RBAC Foundation (DB + API)** | Backend | 3 days | User roles schema | Role enum (ADMIN, USER), basic permission checks; audit logged |

**Deliverables**: Dashboard live and responsive, CRUD fully functional, notifications with actions, basic RBAC in place.

#### **Sprint 1.3 (Weeks 3–4): Caching & Connection Pool Tuning (Backend-Heavy)**

*Proceeds after observability baseline locked in (Phase 1.1).*

| Task | Owner | Effort | Dependencies | Success Criteria |
|------|-------|--------|--------------|-----------------|
| **Implement Multi-Level Caching (Caffeine + Redis)** | Backend | 4 days | Redis provisioned; caching strategy approved | Cache layer operational; hit rate >70% for audit queries |
| **Optimize HikariCP Configuration** | Backend | 2 days | Baseline metrics ready | Pool size 20–50; leak detection active; validation query working |
| **Implement Query Result Streaming** | Backend | 3 days | JDBC + Oracle specs | Large result sets use cursors; memory footprint reduced by 60% |
| **JDBC Batch Operations Tuning** | Backend | 2 days | Batch size analysis | Batch size 100–500; throughput increased by 3x |
| **Oracle Fast Connection Failover Setup** | DBA + Backend | 3 days | RAC credentials ready | Connection failover configured; failover time <3s (measured) |
| **Performance Regression Test Automation** | QA + DevOps | 2 days | CI/CD + baselines ready | Regression tests run post-merge; failure blocks merge if P95 +5% |

**Deliverables**: Caching operational, connection pool optimized, streaming enabled, regression tests active.

#### **Sprint 1.4 (Weeks 4): Integration & Testing (QA-Heavy + Cross-functional)**

*Proceeds in parallel; integrates outputs from Sprints 1.2–1.3.*

| Task | Owner | Effort | Dependencies | Success Criteria |
|------|-------|--------|--------------|-----------------|
| **Cross-browser & Mobile Testing** | QA + Frontend | 2 days | Dashboard + CRUD ready | Chrome, Firefox, Safari, mobile (iOS/Android) tested; <3 critical issues |
| **Load Testing Phase 1 Build** | QA | 3 days | Performance baselines ready | 100 concurrent users sustained; P95 <500ms achieved; error rate <0.5% |
| **Security Baseline Audit** | QA + Security | 2 days | RBAC + auth in place | No OWASP Top 10 critical issues; input validation tested |
| **Accessibility Compliance Check** | QA | 1 day | Dashboard + CRUD | axe-core audit run; WCAG 2.0 A compliance score >90% |
| **Documentation & Release Notes** | PM | 2 days | All features ready | Runbook for Phase 1 live; release notes prepared |

**Deliverables**: Phase 1 build tested and ready for Phase 2; performance baselines confirmed; documentation complete.

---

**Phase 1 Critical Path Dependencies**:

1. **Observability (Week 1–2)** → Enables all baseline measurements and regression detection
2. **UX Completion (Week 2–3)** → Runs in parallel with observability; unblocked by it
3. **Caching + Pool Tuning (Week 3–4)** → Depends on baselines from Phase 1.1
4. **Testing & Sign-Off (Week 4)** → Integrates Phase 1.2–1.3 output

**Risks & Mitigations** (Phase 1):

- **Risk**: Redis provisioning delayed → **Mitigation**: Use in-memory Caffeine-only fallback for Week 1–2
- **Risk**: DBA availability unavailable → **Mitigation**: Contract interim DBA for Weeks 1–3
- **Risk**: Mobile design not finalized → **Mitigation**: Use desktop-first MVP for Sprint 1.2; mobile polish in Phase 2
- **Risk**: Performance regression tests fail to establish baseline → **Mitigation**: Run Gatling 3x; use median as baseline

---

### **Phase 2: Enhancement (Weeks 5–8)**

1. Advanced UX features (search, WebSocket subscriptions, responsive design polish)
2. Database deep optimizations (partitioning, compression, PL/SQL tuning)
3. Advanced observability (ELK stack, distributed tracing, custom dashboards)

**Expected Outcomes**:
- API P95 reduced to <500ms (from 800ms)
- Concurrent users supported: 500+ (from 100)
- Page load time <2s (from 4s)
- Error rate <0.3% (from 0.5%)

---

### **Phase 3: Production Hardening (Weeks 9–12)**

1. Production hardening and testing (chaos engineering, failover drills)
2. Advanced Oracle features implementation (RAC optimization, in-memory column store)
3. Security hardening (MFA, data encryption, compliance audit)
4. Performance monitoring and alerting (SLA enforcement, incident response)

**Expected Outcomes**:
- System availability: 99.9% uptime (from 95%)
- Critical security vulnerabilities: 0 (from 2)
- Audit trail integrity: 100% (from 99%)

---

### **Phase 4: Scale (Weeks 13–16)**

1. Horizontal scaling capabilities (Kubernetes auto-scaling, CDN integration)
2. Advanced caching patterns (cache warming, intelligent invalidation)
3. Enterprise features (MFA, advanced RBAC, compliance reporting)
4. Sustained load testing and feedback loop

**Expected Outcomes**:
- Concurrent users supported: 1000+ (from 100)
- User adoption: 80% (from 60%)
- System availability: 99.95% (SLA exceeded)

---

## 📈 Success Metrics

### **Performance Targets**

**Format: Current → Target (Gap Remaining)**

| Metric | Measurement Method | Current | Target | Gap Remaining | Unit | Status |
|--------|-------------------|---------|--------|---------------|------|--------|
| **API Response Time (P95)** | Gatling load tests, Nov 2025 | 800ms | 500ms | −300ms (improvement needed) | ms | 🔴 |
| **API Response Time (P99)** | Gatling load tests, Nov 2025 | 3000ms | 2000ms | −1000ms (improvement needed) | ms | 🔴 |
| **Database Query Time (P95)** | Oracle AWR reports, Nov 2025 | 150ms | 100ms | −50ms (improvement needed) | ms | 🔴 |
| **Concurrent Users Supported** | Load testing, Nov 2025 | 100 | 1000 | +900 (users) | users | 🔴 |
| **Error Rate (Production)** | Application logs, Nov 2025 | 0.5% | 0.1% | −0.4% (improvement needed) | % | 🔴 |

*Note: Gap Remaining shows the absolute difference (Target − Current). Negative values indicate performance improvement needed.*

### **UX/Frontend Targets**

| Metric | Measurement Method | Current | Target | Gap Remaining | Unit | Status |
|--------|-------------------|---------|--------|---------------|------|--------|
| **Page Load Time (Initial)** | Lighthouse, Nov 2025 | 4.0s | 2.0s | −2.0s (improvement needed) | sec | 🔴 |
| **Time to Interactive** | Lighthouse, Nov 2025 | 5.0s | 3.0s | −2.0s (improvement needed) | sec | 🔴 |
| **Mobile Feature Parity** | Manual testing, Nov 2025 | 80% | 100% | +20% (features) | % | 🔴 |
| **Accessibility Compliance** | axe-core audit, Nov 2025 | WCAG 2.0 A | WCAG 2.1 AA | Upgrade scope | level | 🔴 |

### **Business & Reliability Targets**

| Metric | Measurement Method | Current | Target | Gap Remaining | Unit | Status |
|--------|-------------------|---------|--------|---------------|------|--------|
| **User Adoption (Feature Use)** | Analytics, Nov 2025 | 60% | 80% | +20% (adoption points) | % | 🔴 |
| **System Availability (Uptime)** | Monitoring, Nov 2025 | 95% | 99.9% | +4.9% (availability points) | % | 🔴 |
| **Audit Trail Integrity** | Data validation, Nov 2025 | 99% | 100% | +1% (integrity points) | % | 🔴 |
| **Security Incidents (Critical)** | Security audits, Nov 2025 | 2 | 0 | −2 (eliminate all critical) | count | 🔴 |

---

## 🔍 Risk Assessment & Mitigation

### **High-Risk Items & Concrete Mitigations**

#### **1. Database Migration Complexity** 🔴
**Risk**: Schema changes, data integrity issues, downtime during migrations.

**Concrete Mitigations**:
- **Flyway Integration**: Implement versioned migration scripts (V001_init_schema.sql, V002_add_indexes.sql) with rollback support (U001_rollback.sql)
- **Pre-Production Dry-Run**: Execute all migrations against a masked copy of production data 48 hours before deployment; validate row counts, constraints, and audit trail completeness
- **Schema-Change Review Checklist**: Require approval from DBA lead and architect; validate impact on stored procedures, indexes, and permissions before migration
- **Automated Backout Script**: Generate backout procedure that reverses schema changes within 5-minute recovery SLA; test rollback monthly
- **Monitoring During Migration**: Add DDL lock monitoring; auto-rollback if migration exceeds 15-minute window
- **Owner**: Database Architect + DBA Lead | **Timeline**: 2 days before each production release

#### **2. Oracle RAC Configuration Challenges** 🔴
**Risk**: Misconfigured failover, uneven load balancing, connection affinity failures.

**Concrete Mitigations**:
- **Dedicated DBA Lead Assignment**: Assign primary DBA to oversee RAC setup, with backup DBA for continuity; budget 80 hours for configuration and testing
- **External RAC Configuration Audit**: Engage Oracle consulting for 5-day audit of RAC setup, interconnect latency, and failover readiness; schedule pre-Phase 1
- **Failover Test Plans**: Document and execute monthly failover drills (graceful + forced node failure); measure recovery time and validate zero data loss
- **Connection Affinity Rules**: Implement Oracle connection failover algorithm (CONNECTION_FAILOVER_LIST) in JDBC DataSource; validate via load tests
- **Monitoring & Alerting**: Enable cluster alert log monitoring; alert on node down, interconnect latency >10ms, or cluster heartbeat failures
- **Owner**: Oracle DBA Lead | **Timeline**: 3 weeks pre-deployment, then ongoing (monthly drills)

#### **3. Performance Regression During Optimization** 🔴
**Risk**: Tuning changes cause unexpected performance degradation; impact production workloads.

**Concrete Mitigations**:
- **Automated Performance Regression Tests in CI/CD**: Add Gatling performance tests to build pipeline; baseline metrics for API P95, DB P95, memory usage (established in Phase 1)
- **Performance Baselines & Thresholds**: Define acceptable variance (±5% for P95, ±10% for error rate); fail build if thresholds breached
- **Synthetic Load Test Jobs**: Run load tests against each optimization candidate before merge (100 concurrent users, 5-minute ramp, 15-minute sustained)
- **Automated Rollback Triggers**: If P95 degrades >5% or error rate exceeds 0.5%, auto-revert change and alert on-call engineer
- **A/B Testing Framework**: Deploy optimizations to canary environment (10% traffic) for 24 hours before full rollout; monitor error rates, latency, resource usage
- **Owner**: Performance Engineer + SRE | **Timeline**: Ongoing (per deployment)

#### **4. Legacy System Integration Points** 🔴
**Risk**: Breaking changes in external integrations; backward compatibility issues; data format mismatches.

**Concrete Mitigations**:
- **Contract Testing**: Define and maintain API contracts (OpenAPI spec) for all integrations; use Pact broker for contract validation before merge
- **Explicit Backward-Compatibility Tests**: Add test cases for each legacy API endpoint; version API endpoints (v1, v2) to support parallel support windows
- **Integration Staging Environment**: Maintain full staging environment with sample data mirroring production; run integration tests against staging before production deployment
- **API Change Communication Plan**: Announce deprecations 6 weeks in advance; provide client migration guide; monitor deprecated endpoint usage and set sunset date (e.g., 90 days)
- **Webhook & Event Validation**: For event-driven integrations, validate event schema against multiple client versions; maintain schema versioning (e.g., event_version: "2.0")
- **Owner**: Integration Lead + API Architect | **Timeline**: Per API change (plan 2-3 weeks lead time)

---

**Summary**: Each high-risk item now has 4–5 concrete, measurable mitigation steps with assigned ownership and timelines. This ensures accountability and reduces the likelihood of surprises during implementation.

---

## 📋 Resource & Alignment (Team Capacity & Dependencies)

### **Team Structure & Headcount by Phase**

#### **Phase 1: Foundation (Weeks 1–4) — High Intensity**
| Role | FTE | Responsibilities | Gaps / Hiring Needs |
|------|-----|------------------|---------------------|
| **Backend Engineer** | 1.5 | HikariCP tuning, caching (Caffeine/Redis), connection pool monitoring setup | Need Redis/caching expertise if not present; 1 week ramp-up |
| **Frontend Engineer** | 1.0 | Dashboard rebuild, dynamic CRUD UI, notifications, keyboard shortcuts | Mobile UX design support (0.2 FTE contractor) |
| **Oracle DBA** | 0.5 | Indexing strategy, AWR/ASH analysis, connection failover setup | REQUIRED; may need interim contract DBA (40 hrs) if internal DBA capacity limited |
| **DevOps/SRE** | 0.5 | Prometheus/Grafana setup, alerting, monitoring dashboards, CI/CD tuning | Ensure infrastructure ready; may overlap with ops on-call |
| **QA/Performance Tester** | 0.5 | Gatling test expansion, regression baseline creation, synthetic load jobs setup | |
| **Scrum Master / PM** | 0.3 | Sprint planning, blockers unblocking, stakeholder sync | |
| **Total Phase 1 FTE** | **4.3 FTE** | — | — |

**Overall Budget**: ~**13.2 FTE-months** across 16 weeks | **Peak Load**: Phase 1 & 3 at 4+ FTE

---

### **Gaps & Hiring / Training Needs**

| Gap / Need | Phase | Impact if Unresolved | Mitigation | Timeline | Owner |
|-----------|-------|---------------------|-----------|----------|-------|
| **Redis/Distributed Caching Expertise** | 1 | Cannot implement multi-level caching effectively; suboptimal cache invalidation | Hire contract specialist (40 hrs) OR arrange 1-week training for backend engineer | Week 1 of Phase 1 | Tech Lead |
| **Oracle RAC Configuration** | 1 | RAC failover misconfigured; potential single point of failure | Engage external Oracle consulting firm (5-day audit, $15k–$25k) | 3 weeks pre-Phase 1 | DBA Lead |
| **Mobile/UX Design Support** | 1 | Dashboard and CRUD UI not mobile-optimized; 20% feature parity gap remains | Contract mobile UX designer (20% for 4 weeks) | Week 1 of Phase 1 | Frontend Lead |

---

### **Budget Estimates (Infrastructure & Monitoring)**

#### **Infrastructure Costs (16-week roadmap)**
| Component | Phase | Cost Estimate | Notes |
|-----------|-------|---------------|-------|
| **Prometheus + Grafana (self-hosted)** | 1 | $2–5k (setup), $500/mo (hosting) | Includes alerting, 1-year retention | 
| **ELK Stack (Elasticsearch, Logstash, Kibana)** | 2 | $5–8k (setup), $1.5k/mo (hosting for 1TB/day) | Log aggregation, 30-day retention |
| **Redis Cache (managed, e.g., AWS ElastiCache)** | 1 | $500–1k/mo (2–4GB, HA) | Session + query result caching |
| **Oracle RAC Consulting** | 1 (pre) | $15–25k | External DBA audit + optimization |
| **Total Infrastructure (16 weeks)** | — | **$45–60k capital + $8–10k/mo ops** | — |

#### **Personnel Costs (16-week roadmap)**
| Category | Headcount | Average Cost | Total |
|----------|-----------|--------------|-------|
| **Internal FTE (13.2 FTE-mo @ $10k/FTE-month)** | 13.2 FTE-mo | $10,000/FTE-month | **$132,000** |
| **Contract Specialists (3–4 heads @ $3–5k/week)** | 3–4 | $3–5k/week each | **$36–48k** |
| **Total Personnel (16 weeks)** | — | — | **$168–180k** |

**Total Roadmap Budget**: ~**$213–240k** (including infrastructure and personnel)

---

### **Dependent Teams & Alignment**

| Dependent Team | Phase | Dependency | SLA / Blocker | Communication Plan |
|----------------|-------|-----------|---------------|-------------------|
| **Database Operations** | All | DB instance provisioning, backup/DR, AWR access | DB ready by Week 1; SLA: 99.5% uptime | Weekly sync Tue 10am; escalation to DBA Lead |
| **Infrastructure / Cloud Ops** | 1–2 | Prometheus, Grafana, Redis provisioning; CI/CD pipeline updates | Setup complete by Week 0; SLA: 4-hour incident response | Weekly sync Wed 2pm; Slack #infra-roadmap |
| **Security / Compliance Team** | 3 | RBAC validation, encryption audit, compliance report | Audit in Week 9; blocks Phase 3 sign-off | Bi-weekly sync with Compliance Officer; security@company.com |
| **Product / Stakeholders** | All | Prioritization, UX feedback, success metrics validation | Steering committee bi-weekly; blockers escalated immediately | Bi-weekly demo / sprint review Thu 3pm |

---

## 📋 Dependencies & Prerequisites

### **Technical Dependencies**
- Oracle Database Enterprise Edition access
- MinIO enterprise features availability
- Kubernetes/OpenShift for container orchestration
- Monitoring stack (Prometheus, Grafana, ELK)

### **Team Prerequisites**
- Oracle DBA expertise for advanced features
- Frontend performance optimization experience
- Security auditing capabilities
- DevOps and SRE team availability

---

*This roadmap is a living document and should be reviewed quarterly for updates based on user feedback, technology changes, and business requirements.*
