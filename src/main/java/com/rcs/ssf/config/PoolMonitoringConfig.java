package com.rcs.ssf.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

/**
 * Adds Micrometer-backed observability to the HikariCP data source so pool utilization,
 * wait times, and queue depth can be tracked in Grafana/Prometheus.
 */
@Configuration
public class PoolMonitoringConfig {

    @Bean
    public static DestructionAwareBeanPostProcessor hikariMonitoringPostProcessor(MeterRegistry meterRegistry) {
        return new DestructionAwareBeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
                if (bean instanceof HikariDataSource hikariDataSource) {
                    hikariDataSource.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
                    registerGauges(hikariDataSource, meterRegistry);
                }
                return bean;
            }

            @Override
            public void postProcessBeforeDestruction(@NonNull Object bean, @NonNull String beanName) throws BeansException {
                if (bean instanceof HikariDataSource hikariDataSource) {
                    removeGauges(hikariDataSource, meterRegistry);
                }
            }
        };
    }

    private static void registerGauges(HikariDataSource dataSource, MeterRegistry meterRegistry) {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        if (poolMXBean == null) {
            return;
        }

        Gauge.builder("ssf.hikari.connections.active", poolMXBean, HikariPoolMXBean::getActiveConnections)
                .tag("pool", dataSource.getPoolName())
                .description("Active JDBC connections in HikariCP")
                .register(meterRegistry);

        Gauge.builder("ssf.hikari.connections.idle", poolMXBean, HikariPoolMXBean::getIdleConnections)
                .tag("pool", dataSource.getPoolName())
                .description("Idle JDBC connections in HikariCP")
                .register(meterRegistry);

        Gauge.builder("ssf.hikari.connections.pending", poolMXBean, HikariPoolMXBean::getThreadsAwaitingConnection)
                .tag("pool", dataSource.getPoolName())
                .description("Threads waiting for a JDBC connection")
                .register(meterRegistry);

        Gauge.builder("ssf.hikari.connections.total", poolMXBean, HikariPoolMXBean::getTotalConnections)
                .tag("pool", dataSource.getPoolName())
                .description("Total JDBC connections (active + idle)")
                .register(meterRegistry);
    }

    private static void removeGauges(HikariDataSource dataSource, MeterRegistry meterRegistry) {
        String poolName = dataSource.getPoolName();
        removeIfPresent(meterRegistry.find("ssf.hikari.connections.active").tag("pool", poolName).meter(), meterRegistry);
        removeIfPresent(meterRegistry.find("ssf.hikari.connections.idle").tag("pool", poolName).meter(), meterRegistry);
        removeIfPresent(meterRegistry.find("ssf.hikari.connections.pending").tag("pool", poolName).meter(), meterRegistry);
        removeIfPresent(meterRegistry.find("ssf.hikari.connections.total").tag("pool", poolName).meter(), meterRegistry);
    }

    private static void removeIfPresent(io.micrometer.core.instrument.Meter meter, MeterRegistry registry) {
        if (meter != null) {
            registry.remove(meter);
        }
    }
}
