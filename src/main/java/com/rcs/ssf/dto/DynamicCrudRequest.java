package com.rcs.ssf.dto;

import java.util.List;

public class DynamicCrudRequest {
    private String tableName;
    private String operation;
    private List<ColumnValue> columns;
    private List<Filter> filters;
    private Integer limit;
    private Integer offset;
    private String orderBy;
    private String orderDirection;

    // Getters and setters
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public List<ColumnValue> getColumns() { return columns; }
    public void setColumns(List<ColumnValue> columns) { this.columns = columns; }

    public List<Filter> getFilters() { return filters; }
    public void setFilters(List<Filter> filters) { this.filters = filters; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }

    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }

    public String getOrderDirection() { return orderDirection; }
    public void setOrderDirection(String orderDirection) { this.orderDirection = orderDirection; }

    public static class ColumnValue {
        private String name;
        private Object value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    public static class Filter {
        private String column;
        private String operator;
        private Object value;

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }

        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }
}