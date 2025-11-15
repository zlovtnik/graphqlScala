package com.rcs.ssf.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartitionPlanTest {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> ORACLE = new GenericContainer<>("gvenzl/oracle-free:23.5-faststart")
            .withEnv("ORACLE_PASSWORD", "DevPass123")
            .withEnv("APP_USER", "APPUSER")
            .withEnv("APP_USER_PASSWORD", "DevPass123")
            .withEnv("ORACLE_DATABASE", "FREEPDB1")
            .withExposedPorts(1521)
            .waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1))
            .withReuse(false);

    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void setUpDataSource() {
        var dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(oracle.jdbc.OracleDriver.class);
        dataSource.setUrl(buildJdbcUrl());
        dataSource.setUsername("APPUSER");
        dataSource.setPassword("DevPass123");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("sql/tests/partitioning/partitioning_init.sql"))
                .execute(dataSource);
    }

    @Test
    void explainPlanShowsPartitionPruning() {
        Timestamp rangeStart = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS));
        Timestamp rangeEnd = Timestamp.from(Instant.now());

        List<PlanScenario> scenarios = List.of(
                new PlanScenario("SELECT COUNT(*) FROM audit_dynamic_crud WHERE created_at BETWEEN ? AND ?",
                        List.of(rangeStart, rangeEnd)),
                new PlanScenario(
                        "SELECT username, COUNT(*) FROM audit_login_attempts WHERE created_at >= ? GROUP BY username",
                        List.of(rangeStart)),
                new PlanScenario(
                        "SELECT user_id, COUNT(*) FROM audit_sessions WHERE created_at BETWEEN ? AND ? GROUP BY user_id",
                        List.of(rangeStart, rangeEnd)),
                new PlanScenario(
                        "SELECT error_code, COUNT(*) FROM audit_error_log WHERE created_at >= ? GROUP BY error_code",
                        List.of(rangeStart)));

        scenarios.forEach(this::assertSinglePartitionAccess);
    }

    @Test
    void partitionsRemainAdvancedCompressed() {
        Integer uncompressedPartitions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_tab_partitions WHERE table_name IN ('AUDIT_DYNAMIC_CRUD', 'AUDIT_LOGIN_ATTEMPTS', 'AUDIT_SESSIONS', 'AUDIT_ERROR_LOG') AND (compression <> 'ENABLED' OR compress_for <> 'ADVANCED')",
                Integer.class);
        assertEquals(0, uncompressedPartitions);
    }

    private void assertSinglePartitionAccess(PlanScenario scenario) {
        String statementId = "PARTITION_" + UUID.randomUUID().toString().replace('-', '_');

        jdbcTemplate.execute((Connection connection) -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "EXPLAIN PLAN SET STATEMENT_ID = '" + statementId + "' FOR " + scenario.sql())) {
                for (int i = 0; i < scenario.binds().size(); i++) {
                    ps.setTimestamp(i + 1, scenario.binds().get(i));
                }
                ps.execute();
            }
            return null;
        });

        Integer partitionsTouched = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT partition_start) FROM plan_table WHERE statement_id = ? AND partition_start IS NOT NULL",
                Integer.class, statementId);
        assertEquals(1, partitionsTouched,
                () -> "Expected single partition pruning for query: " + scenario.sql());

                jdbcTemplate.update("DELETE FROM plan_table WHERE statement_id = ?", statementId);
        }

        private String buildJdbcUrl() {
                return String.format("jdbc:oracle:thin:@//%s:%d/FREEPDB1", ORACLE.getHost(), ORACLE.getMappedPort(1521));
        }

    private record PlanScenario(String sql, List<Timestamp> binds) {
    }
}
