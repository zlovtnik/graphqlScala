# Resilience Patterns Guide

## Overview

This guide covers fault-tolerant architecture using Resilience4j patterns. The system is designed to maintain **99.95% uptime** even when dependencies fail.

## Circuit Breaker Pattern

### What is it?

A circuit breaker prevents cascading failures by monitoring service calls and stopping them when failure rates exceed a threshold.

```
States:
┌─────────┐
│ CLOSED  │ (Normal operation)
│ ✓✓✓✓✓  │
└────┬────┘
     │ Failure rate > threshold
     ↓
┌─────────┐
│  OPEN   │ (Rejecting requests)
│ ✗✗✗✗✗  │
└────┬────┘
     │ Wait 30s
     ↓
┌─────────────┐
│ HALF_OPEN   │ (Testing recovery)
│ ? ? ? ? ?   │
└────┬────────┘
     │ Success → CLOSED
     │ Failure → OPEN
```

### Configuration by Service

#### Database Circuit Breaker
```yaml
resilience4j.circuitbreaker.database:
  failure-rate-threshold: 10.0  # % - 5 failures in 50 calls
  wait-duration-open: PT30S      # Time before half-open
  permitted-calls-half-open: 3   # Test calls
  slow-call-threshold: 500ms     # Slow call duration
```

**Rationale:** Database is critical. Low threshold (10%) to fail fast.

#### Redis Circuit Breaker
```yaml
resilience4j.circuitbreaker.redis:
  failure-rate-threshold: 15.0   # % - 7 failures in 50 calls
  wait-duration-open: PT30S
  permitted-calls-half-open: 3
  slow-call-threshold: 200ms     # Redis should be fast
```

**Rationale:** Redis is cache. Slightly higher threshold (15%) as we have Caffeine fallback.

#### MinIO Circuit Breaker
```yaml
resilience4j.circuitbreaker.minio:
  failure-rate-threshold: 20.0   # % - 10 failures in 50 calls
  wait-duration-open: PT60S      # Longer recovery
  permitted-calls-half-open: 3
  slow-call-threshold: 1000ms
```

**Rationale:** Storage is less critical. Higher threshold (20%), longer recovery.

#### Auth Service Circuit Breaker
```yaml
resilience4j.circuitbreaker.auth-service:
  failure-rate-threshold: 5.0    # % - 2 failures in 50 calls - MOST STRICT
  wait-duration-open: PT15S      # Fast recovery
  permitted-calls-half-open: 2   # Conservative
  slow-call-threshold: 100ms
```

**Rationale:** Security-critical. Lowest threshold (5%), fast recovery.

### Usage in Code

```java
@Service
public class UserService {
  @CircuitBreaker(name = "database")
  public User getUser(String id) {
    return userRepository.findById(id).orElse(null);
  }

  @CircuitBreaker(name = "redis")
  public String getCachedValue(String key) {
    return redisTemplate.opsForValue().get(key);
  }
}
```

## Retry Pattern

Automatic retry with exponential backoff for transient failures.

### Retry Strategy

```
Attempt 1: Fail → Wait 100ms
Attempt 2: Fail → Wait 200ms (2x)
Attempt 3: Fail → Wait 400ms (2x)
Attempt 4: Fail → Give up
```

### Configuration

```yaml
resilience4j.retry:
  database:
    max-attempts: 3
    wait-duration: 100ms
    interval-function: exponential_backoff(100ms, 2x multiplier, max 1000ms)
    
  redis:
    max-attempts: 3
    wait-duration: 50ms
    
  minio:
    max-attempts: 2
    wait-duration: 200ms
```

### Retryable vs Non-Retryable Exceptions

**Retryable (auto-retry):**
- `TimeoutException`
- `ConnectException`
- `SocketTimeoutException`
- `IOException` (except 401/403)

**Non-Retryable (fail immediately):**
- `AuthenticationException` (401)
- `AuthorizationException` (403)
- `ValidationException` (400)
- `NotFoundException` (404)

## Bulkhead Pattern

Isolate resources to prevent total system outage.

```yaml
resilience4j.bulkhead:
  database:
    max-concurrent-calls: 50
    max-wait-duration: 2s
    
  redis:
    max-concurrent-calls: 100
    max-wait-duration: 1s
```

```java
@Bulkhead(name = "database", type = Bulkhead.Type.THREADPOOL)
public List<User> searchUsers(String query) {
  return userRepository.search(query);
}
```

## Fallback Pattern

Graceful degradation when services fail.

### Example: Redis Fallback to Caffeine

```java
@Service
@RequiredArgsConstructor
public class CacheService {
  private final RedisTemplate<String, Object> redis;
  private final CaffeineCacheManager caffeine;
  private final MeterRegistry metrics;

  @CircuitBreaker(name = "redis", fallbackMethod = "getCachedFromCaffeine")
  public Object getCached(String key) {
    return redis.opsForValue().get(key);
  }

  public Object getCachedFromCaffeine(String key, Exception ex) {
    metrics.counter("cache.fallback.caffeine").increment();
    return caffeine.getCache("default").get(key);
  }
}
```

### Example: Database Fallback to Cached Copy

```java
@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final UserCache userCache;
  private final MeterRegistry metrics;

  @CircuitBreaker(name = "database", fallbackMethod = "getUserFromCache")
  public User getUser(String id) {
    return userRepository.findById(id);
  }

  public User getUserFromCache(String id, Exception ex) {
    metrics.counter("database.fallback.cache").increment();
    log.warn("Database down, returning cached user: {}", id);
    User cached = userCache.get(id);
    if (cached != null) {
      cached.setStale(true);
      return cached;
    }
    throw ex; // Re-throw if no cached copy
  }
}
```

### Example: Auth Service Fallback (Fail Secure)

```java
@Service
@RequiredArgsConstructor
public class AuthService {
  private final JwtValidator jwtValidator;
  private final MeterRegistry metrics;

  @CircuitBreaker(name = "auth-service", fallbackMethod = "validateTokenFallback")
  public boolean validateToken(String token) {
    return jwtValidator.validate(token);
  }

  public boolean validateTokenFallback(String token, Exception ex) {
    metrics.counter("auth.fallback.denied").increment();
    log.warn("Auth service down, denying request");
    return false; // Deny access on auth service failure
  }
}
```

## Metrics & Monitoring

### Circuit Breaker Metrics

```prometheus
# State (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="database"}

# Calls
resilience4j_circuitbreaker_calls_total{name="database",kind="successful"}
resilience4j_circuitbreaker_calls_total{name="database",kind="failed"}

# Slow calls
resilience4j_circuitbreaker_slow_calls_total{name="database"}
resilience4j_circuitbreaker_slow_calls_duration_ms{name="database",percentile="p95"}
```

### Alerting Rules

```yaml
groups:
  - name: resilience4j
    rules:
      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state == 1
        for: 2m
        annotations:
          summary: "Circuit breaker {{ $labels.name }} is OPEN"
      
      - alert: HighFailureRate
        expr: rate(resilience4j_circuitbreaker_calls_total{kind="failed"}[5m]) > 0.1
        for: 5m
        annotations:
          summary: "{{ $labels.name }} failure rate > 10%"
      
      - alert: SlowCalls
        expr: resilience4j_circuitbreaker_slow_calls_duration_ms{percentile="p99"} > 5000
        annotations:
          summary: "{{ $labels.name }} P99 latency > 5s"
```

## Recovery Procedures

### When Circuit Breaker Opens

1. **Monitor:** Check circuit breaker state in Prometheus
2. **Investigate:** Review error logs and dependency status
3. **Mitigate:** May automatically recover after `wait-duration-open`
4. **Manual:** Reset if needed (don't do this lightly!)

```bash
# Check state
curl http://localhost:8080/actuator/resilience4j/circuitbreakers/database

# Manual reset (emergency only)
curl -X POST http://localhost:8080/actuator/resilience4j/circuitbreakers/database/forceClosed
```

### Fallback Degradation Checklist

When fallback activates:

- [ ] Metrics show elevated fallback counter
- [ ] Check downstream service health
- [ ] Consider manual failover if needed
- [ ] Monitor for stale data (if using cache fallback)
- [ ] Plan upgrade/recovery

## Performance Impact

| Pattern | Latency Overhead | Throughput Impact |
|---------|------------------|-------------------|
| Circuit Breaker | <1ms | None (prevents cascade) |
| Retry | 100-1000ms (per retry) | Slight decrease on failure |
| Bulkhead | 1-5ms | Queue wait time |
| Fallback | <1ms | Massive improvement (if available) |

## Related

- [Caching Strategy](caching-strategy-guide.md)
- [Metrics Reference](metrics-reference.md)
- [Troubleshooting](troubleshooting.md)
