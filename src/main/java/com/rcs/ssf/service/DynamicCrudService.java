package com.rcs.ssf.service;

import com.rcs.ssf.dto.DynamicCrudRequest;
import com.rcs.ssf.dto.DynamicCrudResponseDto;
import com.rcs.ssf.dynamic.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.*;

@Service
public class DynamicCrudService {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicCrudGateway dynamicCrudGateway;

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "users", "audit_login_attempts", "audit_sessions", "audit_dynamic_crud", "audit_error_log"
    );

    public DynamicCrudService(@NonNull DataSource dataSource, DynamicCrudGateway dynamicCrudGateway) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.dynamicCrudGateway = dynamicCrudGateway;
    }

    public DynamicCrudResponseDto executeSelect(DynamicCrudRequest request) {
        validateTable(request.getTableName());

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(request.getTableName());
        List<Object> params = new ArrayList<>();

        // Add WHERE clause
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            sql.append(" WHERE ");
            List<String> conditions = new ArrayList<>();
            for (DynamicCrudRequest.Filter filter : request.getFilters()) {
                conditions.add(filter.getColumn() + " " + filter.getOperator().getSymbol() + " ?");
                params.add(filter.getValue());
            }
            sql.append(String.join(" AND ", conditions));
        }

        // Add ORDER BY
        if (request.getOrderBy() != null) {
            sql.append(" ORDER BY ").append(request.getOrderBy());
            if (request.getOrderDirection() != null) {
                sql.append(" ").append(request.getOrderDirection().name());
            }
        }

        // Add LIMIT/OFFSET (Oracle syntax)
        if (request.getLimit() != null) {
            sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            params.add(request.getOffset() != null ? request.getOffset() : 0);
            params.add(request.getLimit());
        }

        // Execute query
        List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> row = new HashMap<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
            return row;
        }, params.toArray());

        // Get total count
        String countSql = "SELECT COUNT(*) FROM " + request.getTableName();
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            countSql += " WHERE " + String.join(" AND ",
                request.getFilters().stream()
                    .map(f -> f.getColumn() + " " + f.getOperator() + " ?")
                    .toList());
        }
        Integer totalCount = jdbcTemplate.queryForObject(countSql, Integer.class,
            request.getFilters() != null ?
                request.getFilters().stream().map(DynamicCrudRequest.Filter::getValue).toArray() :
                new Object[0]);

        // Get column metadata
        List<DynamicCrudResponseDto.ColumnMeta> columns = getColumnMetadata(request.getTableName());

        return new DynamicCrudResponseDto(rows, totalCount != null ? totalCount : 0, columns);
    }

    public DynamicCrudResponseDto executeMutation(DynamicCrudRequest request) {
        validateTable(request.getTableName());

        List<DynamicCrudColumnValue> columns = request.getColumns() != null ?
            request.getColumns().stream()
                .map(c -> new DynamicCrudColumnValue(c.getName(), c.getValue()))
                .toList() : List.of();

        List<DynamicCrudFilter> filters = request.getFilters() != null ?
            request.getFilters().stream()
                .map(f -> new DynamicCrudFilter(f.getColumn(), f.getOperator().getSymbol(), f.getValue()))
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
            null, // auditContext
            null  // bulkRows
        );

        DynamicCrudResponse response = dynamicCrudGateway.execute(crudRequest);
        return new DynamicCrudResponseDto(List.of(), response.affectedRows(), List.of()); // No rows for mutations, but affected count
    }

    public String[] getAvailableTables() {
        return ALLOWED_TABLES.toArray(new String[0]);
    }

    private void validateTable(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName.toLowerCase())) {
            throw new IllegalArgumentException("Table not allowed: " + tableName);
        }
    }

    private List<DynamicCrudResponseDto.ColumnMeta> getColumnMetadata(String tableName) {
        return jdbcTemplate.query(
            "SELECT column_name, data_type, nullable FROM user_tab_columns WHERE table_name = UPPER(?) ORDER BY column_id",
            (rs, rowNum) -> new DynamicCrudResponseDto.ColumnMeta(
                rs.getString("column_name"),
                rs.getString("data_type"),
                "Y".equals(rs.getString("nullable"))
            ),
            tableName
        );
    }
}