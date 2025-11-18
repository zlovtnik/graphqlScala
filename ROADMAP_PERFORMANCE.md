# 🚀 SSF Application Enhancement Roadmap - Backend Performance Optimizations

## ⚡ Backend Performance Optimizations (Oracle DB Focus)

### **Priority 1: Observability & Prerequisites** 🔴

(Foundation for all downstream optimizations and tuning)

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
  - **Licensing**: Requires Oracle RAC or Data Guard licensing; infrastructure prerequisites include FAN-enabled RAC cluster or Data Guard setup.
  - **Alternatives**: For non-RAC environments, implement custom connection retry logic with `oracle.jdbc.ReadTimeout` and `oracle.net.CONNECT_TIMEOUT`; consider PgBouncer or HAProxy for load balancing without Oracle licensing.
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

> **Licensing Note**: AWR/ASH/ADDM are part of Oracle Diagnostics Pack, requiring separate licensing. For cost-effective alternatives, consider Statspack (free, basic reports) or complementary tools like Oracle Enterprise Manager Express (free tier). Cost-benefit: Diagnostics Pack ~$12k/core/year; Statspack provides 70% of insights at 0 cost.

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
    - Annotate resolvers with custom `@GraphqlCacheable` metadata: `@GraphqlCacheable(maxAge = 300, isPublic = false, vary = ["Authorization"])` to auto-generate correct headers.
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
