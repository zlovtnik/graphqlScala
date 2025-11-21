package com.rcs.ssf.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public class DynamicCrudRequest {
    public enum Operation {
        SELECT, INSERT, UPDATE, DELETE
    }

    public enum Operator {
        EQ("="), NE("!="), GT(">"), LT("<"), GE(">="), LE("<="), LIKE("LIKE");

        private final String symbol;

        Operator(String symbol) { this.symbol = symbol; }

        public String getSymbol() { return symbol; }

        public static Operator fromString(String symbol) {
            for (Operator op : values()) {
                if (op.symbol.equals(symbol)) return op;
            }
            throw new IllegalArgumentException("Invalid operator: " + symbol);
        }
    }

    public enum OrderDirection {
        ASC, DESC
    }

    @NotBlank
    private String tableName;
    @NotNull
    private Operation operation;
    @Valid
    private List<ColumnValue> columns;
    @Valid
    private List<Filter> filters;
    @Valid
    private List<FilterGroup> filterGroups;
    @Valid
    private GlobalSearch globalSearch;
    @Positive
    private Integer limit;
    @Min(0)
    private Integer offset;
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "orderBy must contain only alphanumeric characters and underscores")
    private String orderBy;
    private OrderDirection orderDirection;

    // Getters and setters
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }

    public List<ColumnValue> getColumns() { return columns; }
    public void setColumns(List<ColumnValue> columns) { this.columns = columns; }

    public List<Filter> getFilters() { return filters; }
    public void setFilters(List<Filter> filters) { this.filters = filters; }

    public List<FilterGroup> getFilterGroups() { return filterGroups; }
    public void setFilterGroups(List<FilterGroup> filterGroups) { this.filterGroups = filterGroups; }

    public GlobalSearch getGlobalSearch() { return globalSearch; }
    public void setGlobalSearch(GlobalSearch globalSearch) { this.globalSearch = globalSearch; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }

    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }

    public OrderDirection getOrderDirection() { return orderDirection; }
    public void setOrderDirection(OrderDirection orderDirection) { this.orderDirection = orderDirection; }

    public static class ColumnValue {
        @NotBlank
        private String name;
        @NotNull
        private Object value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    public static class Filter {
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_.]+$", message = "column must contain only alphanumeric characters, underscores, and dots")
        private String column;
        @NotNull
        private Operator operator;
        @NotNull
        private Object value;

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }

        public Operator getOperator() { return operator; }
        public void setOperator(Operator operator) { this.operator = operator; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    public enum LogicalOperator {
        AND, OR
    }

    public static class FilterGroup {
        @NotNull
        private LogicalOperator operator = LogicalOperator.OR;
        @Valid
        @NotEmpty
        private List<Filter> filters;

        public LogicalOperator getOperator() { return operator; }
        public void setOperator(LogicalOperator operator) { this.operator = operator; }

        public List<Filter> getFilters() { return filters; }
        public void setFilters(List<Filter> filters) { this.filters = filters; }
    }

    public enum MatchMode {
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        EXACT
    }

    public static class GlobalSearch {
        @NotBlank
        private String term;
        private List<@Pattern(regexp = "^[A-Za-z0-9_.]+$", message = "column must contain only alphanumeric characters, underscores, and dots") String> columns;
        private MatchMode matchMode = MatchMode.CONTAINS;
        private boolean caseSensitive;

        public String getTerm() { return term; }
        public void setTerm(String term) { this.term = term; }

        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }

        public MatchMode getMatchMode() { return matchMode; }
        public void setMatchMode(MatchMode matchMode) { this.matchMode = matchMode; }

        public boolean isCaseSensitive() { return caseSensitive; }
        public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }
    }
}