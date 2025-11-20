package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.DashboardStatsDto;
import com.rcs.ssf.service.DashboardService;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Controller
public class DashboardQuery {

    private static final Logger log = LoggerFactory.getLogger(DashboardQuery.class);

    @Autowired
    private DashboardService dashboardService;

    @QueryMapping
    @Timed(value = "graphql.resolver.duration", percentiles = {0.5, 0.95, 0.99})
    public DashboardStatsDto getDashboardStats() {
        return dashboardService.getDashboardStats();
    }

    @SubscriptionMapping
    public Flux<DashboardStatsDto> dashboardStats() {
        return Flux.interval(Duration.ofSeconds(5))
                .flatMap(tick -> Mono.fromCallable(dashboardService::getDashboardStats)
                        .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                                .maxBackoff(Duration.ofSeconds(2))
                                .doBeforeRetry(signal -> log.warn(
                                        "Retrying dashboard stats fetch (attempt {}/3): {}",
                                        signal.totalRetries() + 1,
                                        signal.failure().getMessage()))
                                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                                    log.error("Dashboard stats fetch failed after max retries", retrySignal.failure());
                                    return retrySignal.failure();
                                })));
    }
}