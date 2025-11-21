package com.rcs.ssf.controller;

import com.rcs.ssf.service.ETagCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes ETag cache monitoring endpoint.
 */
@RestController
@RequestMapping("/actuator/etag-metrics")
public class ETagMetricsController {

    private final ETagCacheService etagCacheService;

    public ETagMetricsController(ETagCacheService etagCacheService) {
        this.etagCacheService = etagCacheService;
    }

    /**
     * Get ETag cache status and health information.
     */
    @GetMapping("/status")
    public Map<String, Object> getETagCacheStatus() {
        var stats = etagCacheService.getStats();
        return Map.of(
                "operational", stats.operational(),
                "ttlMinutes", stats.ttlMinutes(),
                "status", stats.status(),
                "description", "ETags are cached in Redis with " + stats.ttlMinutes() + 
                        " minute TTL to avoid repeated SHA-256 computation"
        );
    }
}
