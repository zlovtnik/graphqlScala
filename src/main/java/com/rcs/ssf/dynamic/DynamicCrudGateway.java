package com.rcs.ssf.dynamic;

import oracle.jdbc.OracleConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class DynamicCrudGateway {

    private static final String TYPE_COLUMN_NAMES = "DYN_COLUMN_NAME_NT";
    private static final String TYPE_COLUMN_VALUES = "DYN_COLUMN_VALUE_NT";
    private static final String TYPE_FILTERS = "DYN_FILTER_NT";
    private static final String TYPE_FILTER_REC = "DYN_FILTER_REC";
    private static final String TYPE_AUDIT = "DYN_AUDIT_CTX_REC";
    private static final String TYPE_ROW_OP = "DYN_ROW_OP_NT";
    private static final String TYPE_ROW_OP_REC = "DYN_ROW_OP_REC";

    private final JdbcTemplate jdbcTemplate;

    public DynamicCrudGateway(@NonNull DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public DynamicCrudResponse execute(DynamicCrudRequest request) {
        Objects.requireNonNull(request, "request is required");

        if (request.optionalBulkRows().filter(list -> !list.isEmpty()).isPresent()) {
            return executeBulk(request);
        }
        return executeSingle(request);
    }

    private DynamicCrudResponse executeSingle(DynamicCrudRequest request) {
        return jdbcTemplate.execute((Connection connection) -> {
            Array columnNamesArray = null;
            Array columnValuesArray = null;
            Array filterArray = null;
            try (CallableStatement cs = connection.prepareCall("{ call dynamic_crud_pkg.execute_operation(?, ?, ?, ?, ?, ?, ?, ?, ?) }")) {
                try {
                    int index = 1;
                    cs.setString(index++, request.table());
                    cs.setString(index++, request.operation().toPlsqlLiteral());

                    columnNamesArray = toColumnNamesArray(connection, request);
                    columnValuesArray = toColumnValuesArray(connection, request);
                    filterArray = toFiltersArray(connection, request);
                    Object auditStruct = toAuditStruct(connection, request);

                    if (columnNamesArray != null) {
                        cs.setArray(index++, columnNamesArray);
                    } else {
                        cs.setNull(index++, Types.ARRAY, TYPE_COLUMN_NAMES);
                    }

                    if (columnValuesArray != null) {
                        cs.setArray(index++, columnValuesArray);
                    } else {
                        cs.setNull(index++, Types.ARRAY, TYPE_COLUMN_VALUES);
                    }

                    if (filterArray != null) {
                        cs.setArray(index++, filterArray);
                    } else {
                        cs.setNull(index++, Types.ARRAY, TYPE_FILTERS);
                    }

                    if (auditStruct != null) {
                        cs.setObject(index++, auditStruct);
                    } else {
                        cs.setNull(index++, Types.STRUCT, TYPE_AUDIT);
                    }

                    int outMessageIndex = index++;
                    int outGeneratedIdIndex = index++;
                    int outAffectedIndex = index;
                    
                    cs.registerOutParameter(outMessageIndex, Types.VARCHAR);
                    cs.registerOutParameter(outGeneratedIdIndex, Types.VARCHAR);
                    cs.registerOutParameter(outAffectedIndex, Types.INTEGER);

                    cs.execute();

                    String message = cs.getString(outMessageIndex);
                    String generatedId = cs.getString(outGeneratedIdIndex);
                    int affected = cs.getInt(outAffectedIndex);

                    return new DynamicCrudResponse(affected, message, generatedId);
                } finally {
                    freeArray(columnNamesArray);
                    freeArray(columnValuesArray);
                    freeArray(filterArray);
                }
            }
        });
    }

    private DynamicCrudResponse executeBulk(DynamicCrudRequest request) {
        return jdbcTemplate.execute((Connection connection) -> {
            Array rowsArray = null;
            Array filterArray = null;
            List<Array> dependentArrays = new ArrayList<>();
            try (CallableStatement cs = connection.prepareCall("{ call dynamic_crud_pkg.execute_bulk(?, ?, ?, ?, ?, ?, ?) }")) {
                try {
                    int index = 1;
                    cs.setString(index++, request.table());
                    cs.setString(index++, request.operation().toPlsqlLiteral());

                    rowsArray = toRowOperationsArray(connection, request, dependentArrays);
                    filterArray = toFiltersArray(connection, request);
                    Object auditStruct = toAuditStruct(connection, request);

                    if (rowsArray != null) {
                        cs.setArray(index++, rowsArray);
                    } else {
                        cs.setNull(index++, Types.ARRAY, TYPE_ROW_OP);
                    }

                    if (filterArray != null) {
                        cs.setArray(index++, filterArray);
                    } else {
                        cs.setNull(index++, Types.ARRAY, TYPE_FILTERS);
                    }

                    if (auditStruct != null) {
                        cs.setObject(index++, auditStruct);
                    } else {
                        cs.setNull(index++, Types.STRUCT, TYPE_AUDIT);
                    }

                    cs.registerOutParameter(index++, Types.VARCHAR);
                    cs.registerOutParameter(index, Types.INTEGER);

                    cs.execute();

                    String message = cs.getString(index - 1);
                    int affected = cs.getInt(index);

                    return new DynamicCrudResponse(affected, message, null);
                } finally {
                    freeArray(rowsArray);
                    for (Array dependent : dependentArrays) {
                        freeArray(dependent);
                    }
                    freeArray(filterArray);
                }
            }
        });
    }

    private Array toColumnNamesArray(Connection connection, DynamicCrudRequest request) {
        var optional = request.optionalColumns().filter(list -> !list.isEmpty());
        if (optional.isEmpty()) {
            return null;
        }
        List<String> names = optional.get().stream().map(DynamicCrudColumnValue::column).toList();
        return createVarcharArray(connection, TYPE_COLUMN_NAMES, names);
    }

    private Array toColumnValuesArray(Connection connection, DynamicCrudRequest request) {
        var optional = request.optionalColumns().filter(list -> !list.isEmpty());
        if (optional.isEmpty()) {
            return null;
        }
        List<String> values = optional.get().stream()
                .map(value -> value.value() != null ? value.value().toString() : null)
                .toList();
        return createVarcharArray(connection, TYPE_COLUMN_VALUES, values);
    }

    private Array toFiltersArray(Connection connection, DynamicCrudRequest request) {
        var optionalFilters = request.optionalFilters().filter(list -> !list.isEmpty());
        if (optionalFilters.isEmpty()) {
            return null;
        }

        List<Object[]> attributes = new ArrayList<>();
        for (DynamicCrudFilter filter : optionalFilters.get()) {
            attributes.add(new Object[]{
                    filter.column(),
                    filter.operator(),
                    filter.value() != null ? filter.value().toString() : null
            });
        }

        return createStructArray(connection, TYPE_FILTERS, TYPE_FILTER_REC, attributes);
    }

    private Array toRowOperationsArray(Connection connection, DynamicCrudRequest request, List<Array> dependentArrays) {
        var optionalRows = request.optionalBulkRows().filter(list -> !list.isEmpty());
        if (optionalRows.isEmpty()) {
            return null;
        }

        List<Object[]> attributes = new ArrayList<>();
        for (DynamicCrudRow row : optionalRows.get()) {
            Array namesArray = createVarcharArray(connection, TYPE_COLUMN_NAMES,
                    row.columns().stream().map(DynamicCrudColumnValue::column).toList());
            Array valuesArray = createVarcharArray(connection, TYPE_COLUMN_VALUES,
                    row.columns().stream()
                            .map(value -> value.value() != null ? value.value().toString() : null)
                            .toList());
            dependentArrays.add(namesArray);
            dependentArrays.add(valuesArray);
            attributes.add(new Object[]{namesArray, valuesArray});
        }

        return createStructArray(connection, TYPE_ROW_OP, TYPE_ROW_OP_REC, attributes);
    }

    private Object toAuditStruct(Connection connection, DynamicCrudRequest request) {
        return request.optionalAuditContext()
                .map(ctx -> {
                    try {
                        // Check if connection can be unwrapped to OracleConnection before attempting
                        if (!connection.isWrapperFor(OracleConnection.class)) {
                            // Not an Oracle connection (e.g., H2 for testing) - return null
                            return null;
                        }
                        
                        OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
                        Object[] attributes = new Object[]{
                                ctx.actor(),
                                ctx.traceId(),
                                ctx.clientIp(),
                                ctx.metadata()
                        };
                        return oracleConnection.createStruct(TYPE_AUDIT, attributes);
                    } catch (SQLException ex) {
                        throw new RuntimeException("Unable to create audit struct", ex);
                    }
                })
                .orElse(null);
    }

    private Array createVarcharArray(Connection connection, String typeName, List<String> values) {
        try {
            return OracleArrayUtils.toVarcharArray(connection, typeName, values);
        } catch (SQLException ex) {
            throw new RuntimeException("Unable to create array for type " + typeName, ex);
        }
    }

    private Array createStructArray(Connection connection, String collectionType, String elementType, List<Object[]> attributes) {
        try {
            return OracleArrayUtils.toStructArray(connection, collectionType, elementType, attributes);
        } catch (SQLException ex) {
            throw new RuntimeException("Unable to create struct array for type " + collectionType, ex);
        }
    }

    private void freeArray(Array array) {
        if (array != null) {
            try {
                array.free();
            } catch (SQLException ignored) {
                // Swallow cleanup errors
            }
        }
    }
}
