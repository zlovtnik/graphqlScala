package com.rcs.ssf.service;

import com.rcs.ssf.dto.DynamicCrudRequest;
import com.rcs.ssf.dto.DynamicCrudResponseDto;
import com.rcs.ssf.dynamic.*;
import com.rcs.ssf.dynamic.streaming.QueryStreamOptions;
import com.rcs.ssf.dynamic.streaming.QueryStreamingService;
import lombok.extern.slf4j.Slf4j;
import oracle.sql.TIMESTAMP;
import oracle.sql.TIMESTAMPTZ;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DynamicCrudService {

    private final Optional<JdbcTemplate> jdbcTemplate;
    private final DynamicCrudGateway dynamicCrudGateway;
    private final QueryStreamingService queryStreamingService;
    private final String requiredRole;
    private static final int DEFAULT_GLOBAL_SEARCH_COLUMN_LIMIT = 8;

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "audit_login_attempts", "audit_sessions", "audit_dynamic_crud", "audit_error_log");

    private static final Set<String> SENSITIVE_COLUMN_NAMES = Set.of(
            "PASSWORD", "PASSWORD_HASH", "SECRET", "SECRET_KEY", "ACCESS_KEY", "API_KEY", "TOKEN", "REFRESH_TOKEN");

    private static final int STREAM_FETCH_SIZE = 250;

    public DynamicCrudService(
            Optional<DataSource> dataSource,
            DynamicCrudGateway dynamicCrudGateway,
            QueryStreamingService queryStreamingService,
            @Value("${security.dynamicCrud.requiredRole:ROLE_ADMIN}") String requiredRole) {
        this.jdbcTemplate = dataSource.map(JdbcTemplate::new);
        this.dynamicCrudGateway = dynamicCrudGateway;
        this.queryStreamingService = queryStreamingService;
        this.requiredRole = requiredRole;
    }

    public DynamicCrudResponseDto executeSelect(DynamicCrudRequest request) {
        assertAuthorizedForDynamicCrud();
        validateTable(request.getTableName());

        List<DynamicCrudResponseDto.ColumnMeta> columnMetadata = getColumnMetadata(request.getTableName());
        Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup = buildColumnLookup(columnMetadata);

        // Build explicit SELECT list, excluding sensitive columns
        String selectList = columnMetadata.stream()
                .filter(col -> !SENSITIVE_COLUMN_NAMES.contains(col.getName().toUpperCase(Locale.ROOT)))
                .map(col -> "\"" + col.getName() + "\"")
                .collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder("SELECT ").append(selectList).append(" FROM ")
                .append(request.getTableName());
        List<String> whereClauses = new ArrayList<>();
        List<Object> filterParams = new ArrayList<>();

        appendFilters(whereClauses, filterParams, request.getFilters(), columnLookup);
        appendFilterGroups(whereClauses, filterParams, request.getFilterGroups(), columnLookup);
        appendGlobalSearchClause(whereClauses, filterParams, request.getGlobalSearch(), columnMetadata, columnLookup,
                request.getTableName());

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        if (request.getOrderBy() != null) {
            String orderColumn = resolveColumnName(columnLookup, request.getOrderBy());
            sql.append(" ORDER BY ").append(orderColumn);
            if (request.getOrderDirection() != null) {
                sql.append(" ").append(request.getOrderDirection().name());
            }
        }

        List<Object> queryParams = new ArrayList<>(filterParams);

        if (request.getLimit() != null) {
            sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            queryParams.add(request.getOffset() != null ? request.getOffset() : 0);
            queryParams.add(request.getLimit());
        }

        Set<String> visibleColumns = columnLookup.keySet();

        RowMapper<Map<String, Object>> rowMapper = (rs, rowNum) -> {
            Map<String, Object> row = new HashMap<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String columnName = meta.getColumnName(i);
                if (!visibleColumns.contains(columnName.toUpperCase(Locale.ROOT))) {
                    continue;
                }
                row.put(columnName, readColumnValue(rs, i));
            }
            return row;
        };

        List<Map<String, Object>> rows;
        QueryStreamOptions streamOptions = request.getLimit() != null
                ? new QueryStreamOptions("dynamic-crud-" + request.getTableName(), STREAM_FETCH_SIZE)
                : null;

        try (Stream<Map<String, Object>> stream = queryStreamingService.stream(
                sql.toString(),
                queryParams,
                rowMapper,
                streamOptions)) {
            rows = stream.collect(Collectors.toList());
        }

        String countSql = "SELECT COUNT(*) FROM " + request.getTableName();
        if (!whereClauses.isEmpty()) {
            countSql += " WHERE " + String.join(" AND ", whereClauses);
        }

        Integer totalCount = jdbcTemplate.isEmpty() ? null
                : (filterParams.isEmpty()
                        ? jdbcTemplate.get().queryForObject(countSql, Integer.class)
                        : jdbcTemplate.get().queryForObject(countSql, Integer.class, filterParams.toArray()));

        return new DynamicCrudResponseDto(rows, totalCount, columnMetadata, !jdbcTemplate.isEmpty());
    }

    private void appendFilters(List<String> whereClauses,
                               List<Object> filterParams,
                               List<DynamicCrudRequest.Filter> filters,
                               Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        for (DynamicCrudRequest.Filter filter : filters) {
            FilterSql sql = buildFilterSql(filter, columnLookup);
            whereClauses.add(sql.clause());
            filterParams.add(sql.value());
        }
    }

    private void appendFilterGroups(List<String> whereClauses,
                                    List<Object> filterParams,
                                    List<DynamicCrudRequest.FilterGroup> groups,
                                    Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        for (DynamicCrudRequest.FilterGroup group : groups) {
            if (group == null || group.getFilters() == null || group.getFilters().isEmpty()) {
                continue;
            }

            List<String> groupedClauses = new ArrayList<>();
            List<Object> groupedParams = new ArrayList<>();

            for (DynamicCrudRequest.Filter filter : group.getFilters()) {
                FilterSql sql = buildFilterSql(filter, columnLookup);
                groupedClauses.add(sql.clause());
                groupedParams.add(sql.value());
            }

            if (groupedClauses.isEmpty()) {
                continue;
            }

            DynamicCrudRequest.LogicalOperator operator = group.getOperator() != null
                    ? group.getOperator()
                    : DynamicCrudRequest.LogicalOperator.OR;
            String joiner = " " + operator.name() + " ";
            whereClauses.add("(" + String.join(joiner, groupedClauses) + ")");
            filterParams.addAll(groupedParams);
        }
    }

    private void appendGlobalSearchClause(List<String> whereClauses,
                                          List<Object> filterParams,
                                          DynamicCrudRequest.GlobalSearch globalSearch,
                                          List<DynamicCrudResponseDto.ColumnMeta> columnMetadata,
                                          Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup,
                                          String tableName) {
        if (globalSearch == null) {
            return;
        }

        String rawTerm = globalSearch.getTerm();
        if (rawTerm == null) {
            return;
        }

        String trimmedTerm = rawTerm.trim();
        if (trimmedTerm.isEmpty()) {
            return;
        }

        List<String> resolvedColumns;
        if (globalSearch.getColumns() != null && !globalSearch.getColumns().isEmpty()) {
            resolvedColumns = globalSearch.getColumns().stream()
                    .map(column -> resolveColumnName(columnLookup, column))
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            resolvedColumns = columnMetadata.stream()
                    .filter(this::isTextColumn)
                    .map(DynamicCrudResponseDto.ColumnMeta::getName)
                    .limit(DEFAULT_GLOBAL_SEARCH_COLUMN_LIMIT)
                    .collect(Collectors.toList());
        }

        if (resolvedColumns.isEmpty()) {
            log.debug("Skipping global search for table {} because no searchable columns were resolved", tableName);
            return;
        }

        DynamicCrudRequest.MatchMode matchMode = globalSearch.getMatchMode() != null
                ? globalSearch.getMatchMode()
                : DynamicCrudRequest.MatchMode.CONTAINS;

        String pattern = switch (matchMode) {
            case EXACT -> trimmedTerm;
            case STARTS_WITH -> trimmedTerm + "%";
            case ENDS_WITH -> "%" + trimmedTerm;
            case CONTAINS -> "%" + trimmedTerm + "%";
        };

        boolean caseSensitive = globalSearch.isCaseSensitive();
        String normalizedPattern = caseSensitive ? pattern : pattern.toUpperCase(Locale.ROOT);

        List<String> columnExpressions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (String column : resolvedColumns) {
            String targetColumn = caseSensitive ? column : "UPPER(" + column + ")";
            columnExpressions.add(targetColumn + " LIKE ?");
            params.add(normalizedPattern);
        }

        if (columnExpressions.isEmpty()) {
            return;
        }

        whereClauses.add("(" + String.join(" OR ", columnExpressions) + ")");
        filterParams.addAll(params);
    }

    private FilterSql buildFilterSql(DynamicCrudRequest.Filter filter,
                                     Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }
        String columnName = resolveColumnName(columnLookup, filter.getColumn());
        DynamicCrudRequest.Operator operator = filter.getOperator();
        if (operator == null) {
            throw new IllegalArgumentException("Operator cannot be null in filter");
        }
        return new FilterSql(columnName + " " + operator.getSymbol() + " ?", filter.getValue());
    }

    private record FilterSql(String clause, Object value) {}

    public DynamicCrudResponseDto executeMutation(DynamicCrudRequest request) {
        assertAuthorizedForDynamicCrud();
        validateTable(request.getTableName());

        List<DynamicCrudResponseDto.ColumnMeta> columnMetadata = getColumnMetadata(request.getTableName());
        Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup = buildColumnLookup(columnMetadata);

        List<DynamicCrudColumnValue> columns = request.getColumns() != null ? request.getColumns().stream()
                .map(c -> new DynamicCrudColumnValue(resolveColumnName(columnLookup, c.getName()), c.getValue()))
                .toList() : List.of();

        List<DynamicCrudFilter> filters = request.getFilters() != null ? request.getFilters().stream()
                .map(f -> new DynamicCrudFilter(resolveColumnName(columnLookup, f.getColumn()),
                        f.getOperator().getSymbol(), f.getValue()))
                .toList() : List.of();

        DynamicCrudOperation op = switch (request.getOperation()) {
            case SELECT -> throw new IllegalArgumentException(
                    "SELECT operations must use executeSelect() method, not executeMutation()");
            case INSERT -> DynamicCrudOperation.CREATE;
            case UPDATE -> DynamicCrudOperation.UPDATE;
            case DELETE -> DynamicCrudOperation.DELETE;
        };

        com.rcs.ssf.dynamic.DynamicCrudRequest crudRequest = new com.rcs.ssf.dynamic.DynamicCrudRequest(
                request.getTableName(),
                op,
                columns,
                filters,
                null, // audit context set by gateway layer
                null // bulkRows
        );

        DynamicCrudResponse response = dynamicCrudGateway.execute(crudRequest);
        long affectedRowsLong = response.affectedRows();
        int affectedRowsInt;
        try {
            affectedRowsInt = Math.toIntExact(affectedRowsLong);
        } catch (ArithmeticException e) {
            // Log and cap at Integer.MAX_VALUE if overflow occurs
            affectedRowsInt = Integer.MAX_VALUE;
            log.warn("Affected rows count {} exceeds Integer.MAX_VALUE, capping at {}", affectedRowsLong,
                    Integer.MAX_VALUE);
        }
        return new DynamicCrudResponseDto(List.of(), affectedRowsInt, List.of(), !jdbcTemplate.isEmpty()); // No rows
                                                                                                           // for
                                                                                                           // mutations,
                                                                                                           // but
        // affected count
    }

    public String[] getAvailableTables() {
        return ALLOWED_TABLES.stream()
                .sorted()
                .toArray(String[]::new);
    }

    public DynamicCrudResponseDto.SchemaMetadata getTableSchema(String tableName) {
        assertAuthorizedForDynamicCrud();
        validateTable(tableName);
        List<DynamicCrudResponseDto.ColumnMeta> columns = getColumnMetadata(tableName);
        return new DynamicCrudResponseDto.SchemaMetadata(tableName, columns);
    }

    private void validateTable(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Table not allowed: " + tableName);
        }
    }

    private List<DynamicCrudResponseDto.ColumnMeta> getColumnMetadata(String tableName) {
        if (jdbcTemplate.isEmpty()) {
            throw new IllegalStateException(
                    "Dynamic CRUD metadata requires a JDBC DataSource/JdbcTemplate; check DB configuration");
        }
        final String sql = """
                SELECT utc.column_name,
                       utc.data_type,
                       utc.nullable,
                       utc.column_id,
                       utc.data_length,
                       utc.data_precision,
                       utc.data_scale,
                       utc.data_default,
                       CASE
                         WHEN EXISTS (
                             SELECT 1
                             FROM user_cons_columns ucc
                             JOIN user_constraints uc ON ucc.constraint_name = uc.constraint_name
                             WHERE uc.constraint_type = 'P'
                               AND uc.table_name = utc.table_name
                               AND ucc.column_name = utc.column_name
                       ) THEN 'Y' ELSE 'N' END AS is_primary_key,
                       CASE
                         WHEN EXISTS (
                             SELECT 1
                             FROM user_cons_columns ucc
                             JOIN user_constraints uc ON ucc.constraint_name = uc.constraint_name
                             WHERE uc.constraint_type = 'U'
                               AND uc.table_name = utc.table_name
                               AND ucc.column_name = utc.column_name
                       ) THEN 'Y' ELSE 'N' END AS is_unique,
                       NVL(ucc.comments, '') AS column_comment,
                       fkc.ref_table_name,
                       fkc.ref_column_name
                FROM user_tab_columns utc
                LEFT JOIN user_col_comments ucc ON utc.table_name = ucc.table_name
                                                AND utc.column_name = ucc.column_name
                LEFT JOIN (
                    SELECT ucc1.table_name,
                           ucc1.column_name,
                           ucc2.table_name AS ref_table_name,
                           ucc2.column_name AS ref_column_name
                    FROM user_cons_columns ucc1
                    JOIN user_constraints uc1 ON ucc1.constraint_name = uc1.constraint_name
                    JOIN user_constraints uc2 ON uc1.r_constraint_name = uc2.constraint_name
                    JOIN user_cons_columns ucc2 ON uc2.constraint_name = ucc2.constraint_name
                    WHERE uc1.constraint_type = 'R'
                ) fkc ON utc.table_name = fkc.table_name AND utc.column_name = fkc.column_name
                WHERE utc.table_name = UPPER(?)
                ORDER BY utc.column_id
                """;

        List<DynamicCrudResponseDto.ColumnMeta> columns = jdbcTemplate.get().query(
            sql,
            (rs, rowNum) -> new DynamicCrudResponseDto.ColumnMeta(
                rs.getString("column_name"),
                rs.getString("data_type"),
                "Y".equals(rs.getString("nullable")),
                "Y".equals(rs.getString("is_primary_key")),
                rs.getObject("data_length") != null ? rs.getInt("data_length") : null,
                safeReadColumnDefault(rs),
                rs.getObject("data_precision") != null ? rs.getInt("data_precision") : null,
                rs.getObject("data_scale") != null ? rs.getInt("data_scale") : null,
                "Y".equals(rs.getString("is_unique")),
                rs.getString("column_comment"),
                rs.getString("ref_table_name"),
                rs.getString("ref_column_name")),
            tableName);

        return columns.stream()
                .filter(meta -> !isSensitiveColumn(meta.getName()))
                .collect(Collectors.toList());
    }

    private Map<String, DynamicCrudResponseDto.ColumnMeta> buildColumnLookup(
            List<DynamicCrudResponseDto.ColumnMeta> columnMetadata) {
        return columnMetadata.stream()
                .collect(Collectors.toMap(
                        meta -> meta.getName().toUpperCase(Locale.ROOT),
                        meta -> meta));
    }

    private String resolveColumnName(Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup,
            String requestedColumn) {
        String normalized = requestedColumn == null ? null : requestedColumn.toUpperCase(Locale.ROOT);
        if (normalized == null || !columnLookup.containsKey(normalized)) {
            throw new IllegalArgumentException("Column not allowed: " + requestedColumn);
        }
        return columnLookup.get(normalized).getName();
    }

    private void assertAuthorizedForDynamicCrud() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Dynamic CRUD operations require authentication");
        }

        boolean hasRequiredRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredRole::equals);

        if (!hasRequiredRole) {
            throw new AccessDeniedException("Dynamic CRUD operations require administrative privileges");
        }
    }

    private boolean isSensitiveColumn(String columnName) {
        if (columnName == null) {
            return false;
        }
        return SENSITIVE_COLUMN_NAMES.contains(columnName.toUpperCase(Locale.ROOT));
    }

    private boolean isTextColumn(DynamicCrudResponseDto.ColumnMeta columnMeta) {
        if (columnMeta == null || columnMeta.getType() == null) {
            return false;
        }
        String type = columnMeta.getType().toUpperCase(Locale.ROOT);
        return type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT") || type.contains("VARCHAR");
    }

    private String safeReadColumnDefault(ResultSet rs) throws SQLException {
        try {
            return rs.getString("data_default");
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 17027) {
                log.debug("Skipping data_default for column {} due to ORA-17027", rs.getString("column_name"));
                return null;
            }
            throw ex;
        }
    }

    private Object readColumnValue(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        if (value == null) {
            return null;
        }

        if (value instanceof TIMESTAMPTZ timestamptz) {
            Connection connection = resolveConnection(rs);
            if (connection != null) {
                return timestamptz.offsetDateTimeValue(connection);
            }
            return timestamptz.stringValue();
        }

        if (value instanceof TIMESTAMP timestamp) {
            Timestamp ts = timestamp.timestampValue();
            return ts != null ? ts.toInstant() : null;
        }

        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }

        if (value instanceof Date date) {
            return date.toLocalDate();
        }

        if (value instanceof Time time) {
            return time.toLocalTime();
        }

        if (value instanceof Clob clob) {
            return readClob(clob);
        }

        return value;
    }

    private Connection resolveConnection(ResultSet rs) throws SQLException {
        Statement statement = rs.getStatement();
        return statement != null ? statement.getConnection() : null;
    }

    private String readClob(Clob clob) throws SQLException {
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } catch (IOException ex) {
            throw new SQLException("Failed to read CLOB value", ex);
        }
    }
}