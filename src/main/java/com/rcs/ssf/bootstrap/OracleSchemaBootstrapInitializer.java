package com.rcs.ssf.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.core.io.Resource;

/**
 * Custom schema initializer that plays raw SQL scripts directly against the Oracle
 * datasource whenever the validation table is missing.
 */
class OracleSchemaBootstrapInitializer extends DataSourceScriptDatabaseInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OracleSchemaBootstrapInitializer.class);

    private final SchemaBootstrapProperties properties;

    OracleSchemaBootstrapInitializer(DataSource dataSource, SchemaBootstrapProperties properties) {
        super(dataSource, buildSettings(properties));
        this.properties = properties;
    }

    @jakarta.annotation.PostConstruct
    public void triggerBootstrap() {
        LOGGER.info("Evaluating Oracle schema bootstrap");
        if (!isOracleDatabase()) {
            LOGGER.info("Skipping Oracle schema bootstrap because database is not Oracle");
            return;
        }
        initializeDatabase();
    }

    private boolean isOracleDatabase() {
        try (Connection connection = getDataSource().getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return "Oracle".equalsIgnoreCase(productName);
        } catch (SQLException ex) {
            LOGGER.warn("Failed to determine database product; assuming not Oracle", ex);
            return false;
        }
    }

    private static DatabaseInitializationSettings buildSettings(SchemaBootstrapProperties properties) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(properties.getScripts());
        settings.setContinueOnError(properties.isContinueOnError());
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setEncoding(StandardCharsets.UTF_8);
        return settings;
    }

    @Override
    public boolean initializeDatabase() {
        if (schemaAlreadyInitialized()) {
            LOGGER.info("Schema bootstrap skipped because table '{}' already exists", properties.getValidationTable());
            return false;
        }
        return super.initializeDatabase();
    }

    private boolean schemaAlreadyInitialized() {
        String validationTable = properties.getValidationTable();
        if (validationTable == null || validationTable.isBlank()) {
            return false;
        }
        String tableName = validationTable.trim().toUpperCase(Locale.ROOT);
        try (Connection connection = getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT COUNT(*) FROM user_tables WHERE table_name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
        catch (SQLException ex) {
            LOGGER.warn("Failed to inspect schema state; attempting bootstrap anyway", ex);
            return false;
        }
    }

    @Override
    protected void runScripts(org.springframework.boot.sql.init.AbstractScriptDatabaseInitializer.Scripts scripts) {
        Charset encoding = scripts.getEncoding() != null ? scripts.getEncoding() : StandardCharsets.UTF_8;
        for (Resource resource : scripts) {
            executeResource(resource, encoding, scripts.isContinueOnError());
        }
    }

    private void executeResource(Resource resource, Charset encoding, boolean continueOnError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), encoding));
                Connection connection = getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            LOGGER.info("Bootstrapping schema using {}", resource.getFilename());
            streamStatements(reader, sql -> {
                try {
                    executeStatement(resource, sql, continueOnError, statement);
                }
                catch (SQLException ex) {
                    throw new IllegalStateException("Failed to execute SQL resource %s".formatted(resource), ex);
                }
            });
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to read SQL resource %s".formatted(resource), ex);
        }
        catch (SQLException ex) {
            throw new IllegalStateException("Failed to execute SQL resource %s".formatted(resource), ex);
        }
    }

    private void streamStatements(BufferedReader reader, java.util.function.Consumer<String> consumer) throws IOException {
        StringBuilder current = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().equals("/")) {
                dispatchStatement(current.toString(), consumer);
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        if (current.length() > 0) {
            dispatchStatement(current.toString(), consumer);
        }
    }

    private void dispatchStatement(String buffer, java.util.function.Consumer<String> consumer) {
        String sql = buffer.trim();
        if (sql.isEmpty()) {
            return;
        }
        List<String> statements = SqlStatementParser.parse(sql);
        for (String statement : statements) {
            if (!statement.isBlank()) {
                consumer.accept(statement);
            }
        }
    }

    private void executeStatement(Resource resource, String sql, boolean continueOnError, Statement statement)
            throws SQLException {
        try {
            statement.execute(sql);
        }
        catch (SQLException ex) {
            if (continueOnError) {
                LOGGER.debug("Ignoring SQL error while executing bootstrap statement from {}\n{}", resource,
                        ex.getMessage());
            }
            else {
                throw ex;
            }
        }
    }

    /**
     * Minimal Oracle aware SQL splitter that understands lone slash delimiters for
     * PL/SQL blocks while still supporting regular DDL statements separated by
     * semicolons.
     */
    private static final class SqlStatementParser {

        static List<String> parse(String script) {
            if (script == null || script.isBlank()) {
                return List.of();
            }
            List<Segment> segments = splitSegments(script);
            List<String> statements = new ArrayList<>();
            for (Segment segment : segments) {
                if (segment.sql().isBlank()) {
                    continue;
                }
                if (segment.slashDelimited()) {
                    statements.add(segment.sql().trim());
                }
                else {
                    statements.addAll(splitBySemicolon(segment.sql()));
                }
            }
            return statements;
        }

        private static List<Segment> splitSegments(String script) {
            List<Segment> segments = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new java.io.StringReader(script))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().equals("/")) {
                        addSegment(segments, current, true);
                    }
                    else {
                        current.append(line).append('\n');
                    }
                }
            }
            catch (IOException ex) {
                throw new IllegalStateException("Failed to parse SQL script", ex);
            }
            addSegment(segments, current, false);
            return segments;
        }

        private static void addSegment(List<Segment> segments, StringBuilder current, boolean slashDelimited) {
            if (current.length() == 0) {
                return;
            }
            String sql = current.toString().trim();
            if (!sql.isEmpty()) {
                segments.add(new Segment(sql, slashDelimited));
            }
            current.setLength(0);
        }

        private static List<String> splitBySemicolon(String sql) {
            List<String> statements = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            for (int i = 0; i < sql.length(); i++) {
                char ch = sql.charAt(i);
                if (ch == '\'' && !inDoubleQuote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        current.append("''");
                        i++;
                        continue;
                    }
                    inSingleQuote = !inSingleQuote;
                }
                else if (ch == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }

                if (ch == ';' && !inSingleQuote && !inDoubleQuote) {
                    appendStatement(statements, current);
                }
                else {
                    current.append(ch);
                }
            }
            appendStatement(statements, current);
            return statements;
        }

        private static void appendStatement(List<String> statements, StringBuilder current) {
            String candidate = current.toString().trim();
            if (!candidate.isEmpty()) {
                statements.add(candidate);
            }
            current.setLength(0);
        }

        private record Segment(String sql, boolean slashDelimited) {
        }
    }
}
