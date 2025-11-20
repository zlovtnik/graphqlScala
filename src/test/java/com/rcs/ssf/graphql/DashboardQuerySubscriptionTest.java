package com.rcs.ssf.graphql;

import com.rcs.ssf.dto.DashboardStatsDto;
import com.rcs.ssf.graphql.type.HealthStatus;
import com.rcs.ssf.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Dashboard GraphQL Subscription Tests")
class DashboardQuerySubscriptionTest {

    @InjectMocks
    private DashboardQuery dashboardQuery;

    @Mock
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should create Flux that emits dashboard stats periodically")
    void testDashboardStatsSubscriptionEmitsValues() {
        // Arrange
        var mockStats = new DashboardStatsDto();
        mockStats.setTotalUsers(100L);
        mockStats.setActiveSessions(10L);
        mockStats.setTotalAuditLogs(500L);
        mockStats.setSystemHealth(HealthStatus.UP);
        mockStats.setLoginAttemptsToday(50L);
        mockStats.setFailedLoginAttempts(2L);
        mockStats.setTotalLoginAttempts(50L);

        when(dashboardService.getDashboardStats()).thenReturn(mockStats);

        // Act
        Flux<DashboardStatsDto> subscription = dashboardQuery.dashboardStats();

        // Assert
        StepVerifier.create(subscription.take(3))
            .expectNextCount(3)
            .verifyComplete();

        verify(dashboardService, times(3)).getDashboardStats();
    }

    @Test
    @DisplayName("Should return non-null DashboardStatsDto from subscription")
    void testSubscriptionReturnsValidStats() {
        // Arrange
        var mockStats = new DashboardStatsDto();
        mockStats.setTotalUsers(50L);
        mockStats.setActiveSessions(5L);

        when(dashboardService.getDashboardStats()).thenReturn(mockStats);

        // Act
        Flux<DashboardStatsDto> subscription = dashboardQuery.dashboardStats();

        // Assert
        StepVerifier.create(subscription.take(1))
            .assertNext(stats -> {
                assertNotNull(stats);
                assertEquals(50L, stats.getTotalUsers());
                assertEquals(5L, stats.getActiveSessions());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should handle service returning different stats values")
    void testSubscriptionWithVaryingStats() {
        // Arrange
        var stats1 = new DashboardStatsDto();
        stats1.setTotalUsers(100L);
        stats1.setActiveSessions(10L);

        var stats2 = new DashboardStatsDto();
        stats2.setTotalUsers(105L);
        stats2.setActiveSessions(12L);

        when(dashboardService.getDashboardStats())
            .thenReturn(stats1)
            .thenReturn(stats2)
            .thenReturn(stats1);

        // Act
        Flux<DashboardStatsDto> subscription = dashboardQuery.dashboardStats();

        // Assert
        StepVerifier.create(subscription.take(3))
            .assertNext(stats -> assertEquals(100L, stats.getTotalUsers()))
            .assertNext(stats -> assertEquals(105L, stats.getTotalUsers()))
            .assertNext(stats -> assertEquals(100L, stats.getTotalUsers()))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map interval ticks to dashboard stats")
    void testSubscriptionMappingLogic() {
        // Arrange
        var expectedStats = new DashboardStatsDto();
        expectedStats.setTotalUsers(42L);
        expectedStats.setSystemHealth(HealthStatus.UP);

        when(dashboardService.getDashboardStats()).thenReturn(expectedStats);

        // Act
        Flux<DashboardStatsDto> subscription = dashboardQuery.dashboardStats();

        // Assert
        StepVerifier.create(subscription.take(2))
            .assertNext(stats -> {
                assertEquals(42L, stats.getTotalUsers());
                assertEquals(HealthStatus.UP, stats.getSystemHealth());
            })
            .assertNext(stats -> {
                assertEquals(42L, stats.getTotalUsers());
                assertEquals(HealthStatus.UP, stats.getSystemHealth());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should allow cancellation of subscription")
    void testSubscriptionCancellation() {
        // Arrange
        var mockStats = new DashboardStatsDto();
        when(dashboardService.getDashboardStats()).thenReturn(mockStats);

        // Act
        Flux<DashboardStatsDto> subscription = dashboardQuery.dashboardStats();

        // Assert - cancel after first emission
        StepVerifier.create(subscription.take(10))
            .expectNextCount(1)
            .thenCancel()
            .verify();

        // Verify service was called at least once
        verify(dashboardService, atLeast(1)).getDashboardStats();
    }

    @Test
    @DisplayName("Should emit multiple values continuously")
    void testContinuousEmission() {
        // Arrange
        var mockStats = new DashboardStatsDto();
        mockStats.setTotalUsers(100L);
        mockStats.setSystemHealth(HealthStatus.UP);

        when(dashboardService.getDashboardStats()).thenReturn(mockStats);

        // Act
        Flux<DashboardStatsDto> subscription = dashboardQuery.dashboardStats();

        // Assert
        StepVerifier.create(subscription.take(5))
            .expectNextCount(5)
            .verifyComplete();

        verify(dashboardService, times(5)).getDashboardStats();
    }

    @Test
    @DisplayName("Should preserve stats values across emissions")
    void testStatsValuePreservation() {
        // Arrange
        var stats = new DashboardStatsDto();
        stats.setTotalUsers(999L);
        stats.setActiveSessions(77L);
        stats.setSystemHealth(HealthStatus.DEGRADED);

        when(dashboardService.getDashboardStats()).thenReturn(stats);

        // Act
        Flux<DashboardStatsDto> subscription = dashboardQuery.dashboardStats();

        // Assert
        StepVerifier.create(subscription.take(3))
            .assertNext(s -> {
                assertEquals(999L, s.getTotalUsers());
                assertEquals(77L, s.getActiveSessions());
                assertEquals(HealthStatus.DEGRADED, s.getSystemHealth());
            })
            .assertNext(s -> {
                assertEquals(999L, s.getTotalUsers());
                assertEquals(77L, s.getActiveSessions());
                assertEquals(HealthStatus.DEGRADED, s.getSystemHealth());
            })
            .assertNext(s -> {
                assertEquals(999L, s.getTotalUsers());
                assertEquals(77L, s.getActiveSessions());
                assertEquals(HealthStatus.DEGRADED, s.getSystemHealth());
            })
            .verifyComplete();
    }
}
