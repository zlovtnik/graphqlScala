package com.rcs.ssf.dynamic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Wraps PL/SQL calls with DBMS_APPLICATION_INFO and DBMS_MONITOR hooks so the
 * DBA team
 * can tie stored procedure latency back to specific GraphQL resolvers and REST
 * endpoints.
 */
@Slf4j
@Component
public class PlsqlInstrumentationSupport {

    public <T> T withAction(Connection connection, String module, String action, SqlSupplier<T> callback) {
        Objects.requireNonNull(connection, "connection is required");
        Objects.requireNonNull(callback, "callback is required");
        Objects.requireNonNull(module, "module is required");
        Objects.requireNonNull(action, "action is required");

        String moduleName = truncate(module, 48);
        String actionName = truncate(action, 32);
        long start = System.nanoTime();
        try {
            setModuleAndAction(connection, moduleName, actionName);
            enableMonitor(connection, moduleName, actionName);
            return callback.get();
        } catch (SQLException ex) {
            throw new DataAccessResourceFailureException("Failed to execute instrumented PL/SQL block", ex);
        } finally {
            disableMonitor();
            clearAction(connection);
            if (log.isTraceEnabled()) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                log.trace("PL/SQL action {}.{} completed in {} ms", moduleName, actionName, durationMs);
            }
        }
    }

    private void setModuleAndAction(Connection connection, String module, String action) {
        executeQuietly(connection, "begin dbms_application_info.set_module(module_name => ?, action_name => ?); end;",
                stmt -> {
                    stmt.setString(1, module);
                    stmt.setString(2, action);
                });
    }

    private void clearAction(Connection connection) {
        executeQuietly(connection, "begin dbms_application_info.set_action(null); end;", stmt -> {
        });
    }

    private void enableMonitor(Connection connection, String module, String action) {
        // DBMS_MONITOR service-level monitoring requires DBA privileges, proper service
        // configuration,
        // and may not be available in Oracle Free. Skip it for now;
        // DBMS_APPLICATION_INFO is sufficient
        // for module/action tracking in v$session. Can be re-enabled in enterprise
        // environments.
    }

    private void disableMonitor() {
        // DBMS_MONITOR disabled; see enableMonitor() for details.
    }

    private void executeQuietly(Connection connection, String plsql, StatementConfigurer configurer) {
        try (CallableStatement statement = connection.prepareCall(plsql)) {
            configurer.configure(statement);
            statement.execute();
        } catch (SQLException ex) {
            // Tests running on H2 or environments lacking DBMS_* packages should not fail
            // the request.
            log.debug("Unable to execute instrumentation block '{}': {}", plsql, ex.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(CallableStatement statement) throws SQLException;
    }
}
