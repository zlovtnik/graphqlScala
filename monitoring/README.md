# SSF GraphQL Monitoring Setup

This directory contains Prometheus configuration for monitoring the SSF GraphQL application.

## Files

- `prometheus.yml`: Main Prometheus configuration with scrape targets
- `alerts.yml`: Alert rules for common issues (memory, connections, errors)

## Setup

1. Install Prometheus:
   ```bash
   # Using Docker
   docker run -d -p 9090:9090 -v $(pwd)/monitoring/prometheus:/etc/prometheus prom/prometheus

   # Or download from https://prometheus.io/download/
   ```

2. Access Prometheus UI at http://localhost:9090

3. Verify metrics are being scraped by checking targets at http://localhost:9090/targets

## Metrics Available

### JVM Metrics
- `jvm_memory_used_bytes` / `jvm_memory_max_bytes`: Memory usage
- `jvm_gc_pause_seconds`: Garbage collection pause times
- `jvm_threads_live`: Active thread count

### Database Metrics
- `hikaricp_connections_active`: Active JDBC connections
- `hikaricp_connections_idle`: Idle JDBC connections
- `r2dbc_pool_acquired`: Active R2DBC connections

### Application Metrics
- `graphql_requests_total{operation="query|mutation"}`: GraphQL request count by operation type
- `graphql_responses_total{status="success|error"}`: GraphQL response count by status
- `graphql_resolver_duration_seconds`: GraphQL resolver execution time (P50/P95/P99)
- `ssf_failed_login_attempts_total`: Failed authentication attempts
- `ssf_encryption_coverage`: Data encryption coverage percentage

### Business Metrics
- `ssf_mfa_enrollment_rate`: MFA enrollment percentage
- `ssf_audit_log_completeness`: Audit log completeness percentage
- `ssf_sox_control_status`: SOX compliance status percentage

## Grafana Integration

Import the following dashboard for visualization:
- [JVM Micrometer Dashboard](https://grafana.com/grafana/dashboards/4701-jvm-micrometer/)
- [Spring Boot Statistics](https://grafana.com/grafana/dashboards/6756-spring-boot-statistics/)

## Production Deployment

For production, update `prometheus.yml` with:
- Correct target URLs and ports
- TLS configuration for secure scraping
- Service discovery instead of static targets
- Alertmanager configuration for notifications