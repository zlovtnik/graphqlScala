package com.rcs.ssf.service;

import com.rcs.ssf.dto.DynamicCrudRequest;
import com.rcs.ssf.dto.DynamicCrudResponseDto;
import com.rcs.ssf.dynamic.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DynamicCrudService {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicCrudGateway dynamicCrudGateway;
    private final String requiredRole;

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "audit_login_attempts", "audit_sessions", "audit_dynamic_crud", "audit_error_log"
    );

    private static final Set<String> SENSITIVE_COLUMN_NAMES = Set.of(
        "PASSWORD", "PASSWORD_HASH", "SECRET", "SECRET_KEY", "ACCESS_KEY", "API_KEY", "TOKEN", "REFRESH_TOKEN"
    );

    public DynamicCrudService(
        @NonNull DataSource dataSource,
        DynamicCrudGateway dynamicCrudGateway,
        @Value("${security.dynamicCrud.requiredRole:ROLE_ADMIN}") String requiredRole
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.dynamicCrudGateway = dynamicCrudGateway;
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

        StringBuilder sql = new StringBuilder("SELECT ").append(selectList).append(" FROM ").append(request.getTableName());
        List<String> whereClauses = new ArrayList<>();
        List<Object> filterParams = new ArrayList<>();

        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            for (DynamicCrudRequest.Filter filter : request.getFilters()) {
                String columnName = resolveColumnName(columnLookup, filter.getColumn());
                DynamicCrudRequest.Operator operator = filter.getOperator();
                
                // Validate operator is not null (should not happen with @NotNull but be defensive)
                if (operator == null) {
                    throw new IllegalArgumentException("Operator cannot be null in filter");
                }
                
                whereClauses.add(columnName + " " + operator.getSymbol() + " ?");
                filterParams.add(filter.getValue());
            }
        }

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

        List<Map<String, Object>> rows = jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String columnName = meta.getColumnName(i);
                    if (!visibleColumns.contains(columnName.toUpperCase(Locale.ROOT))) {
                        continue;
                    }
                    row.put(columnName, rs.getObject(i));
                }
                return row;
            },
            queryParams.toArray()
        );

        String countSql = "SELECT COUNT(*) FROM " + request.getTableName();
        if (!whereClauses.isEmpty()) {
            countSql += " WHERE " + String.join(" AND ", whereClauses);
        }

        Integer totalCount = filterParams.isEmpty()
            ? jdbcTemplate.queryForObject(countSql, Integer.class)
            : jdbcTemplate.queryForObject(countSql, Integer.class, filterParams.toArray());

        return new DynamicCrudResponseDto(rows, totalCount != null ? totalCount : 0, columnMetadata);
    }

    public DynamicCrudResponseDto executeMutation(DynamicCrudRequest request) {
        assertAuthorizedForDynamicCrud();
        validateTable(request.getTableName());

        List<DynamicCrudResponseDto.ColumnMeta> columnMetadata = getColumnMetadata(request.getTableName());
        Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup = buildColumnLookup(columnMetadata);

        List<DynamicCrudColumnValue> columns = request.getColumns() != null ?
            request.getColumns().stream()
                .map(c -> new DynamicCrudColumnValue(resolveColumnName(columnLookup, c.getName()), c.getValue()))
                .toList() : List.of();

        List<DynamicCrudFilter> filters = request.getFilters() != null ?
            request.getFilters().stream()
                .map(f -> new DynamicCrudFilter(resolveColumnName(columnLookup, f.getColumn()), f.getOperator().getSymbol(), f.getValue()))
                .toList() : List.of();

        DynamicCrudOperation op = switch (request.getOperation()) {
            case INSERT -> DynamicCrudOperation.CREATE;
            case UPDATE -> DynamicCrudOperation.UPDATE;
            case DELETE -> DynamicCrudOperation.DELETE;
            default -> throw new IllegalArgumentException("Unsupported operation: " + request.getOperation());
        };

        com.rcs.ssf.dynamic.DynamicCrudRequest crudRequest = new com.rcs.ssf.dynamic.DynamicCrudRequest(
            request.getTableName(),
            op,
            columns,
            filters,
            null,  // audit context set by gateway layer
            null   // bulkRows
        );

        DynamicCrudResponse response = dynamicCrudGateway.execute(crudRequest);
        return new DynamicCrudResponseDto(List.of(), response.affectedRows(), List.of()); // No rows for mutations, but affected count
    }

    public String[] getAvailableTables() {
        return ALLOWED_TABLES.stream()
            .sorted()
            .toArray(String[]::new);
    }

    private void validateTable(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Table not allowed: " + tableName);
        }
    }

    private List<DynamicCrudResponseDto.ColumnMeta> getColumnMetadata(String tableName) {
        final String sql = """
                SELECT utc.column_name,
                       utc.data_type,
                       utc.nullable,
                       CASE
                         WHEN EXISTS (
                             SELECT 1
                             FROM user_cons_columns ucc
                             JOIN user_constraints uc ON ucc.constraint_name = uc.constraint_name
                             WHERE uc.constraint_type = 'P'
                               AND uc.table_name = utc.table_name
                               AND ucc.column_name = utc.column_name
                       ) THEN 'Y' ELSE 'N' END AS is_primary_key
                FROM user_tab_columns utc
                WHERE utc.table_name = UPPER(?)
                ORDER BY utc.column_id
                """;

        List<DynamicCrudResponseDto.ColumnMeta> columns = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new DynamicCrudResponseDto.ColumnMeta(
                rs.getString("column_name"),
                rs.getString("data_type"),
                "Y".equals(rs.getString("nullable")),
                "Y".equals(rs.getString("is_primary_key"))
            ),
            tableName
        );

        return columns.stream()
                .filter(meta -> !isSensitiveColumn(meta.getName()))
                .collect(Collectors.toList());
    }

    private Map<String, DynamicCrudResponseDto.ColumnMeta> buildColumnLookup(List<DynamicCrudResponseDto.ColumnMeta> columnMetadata) {
        return columnMetadata.stream()
                .collect(Collectors.toMap(
                        meta -> meta.getName().toUpperCase(Locale.ROOT),
                        meta -> meta
                ));
    }

    private String resolveColumnName(Map<String, DynamicCrudResponseDto.ColumnMeta> columnLookup, String requestedColumn) {
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
}