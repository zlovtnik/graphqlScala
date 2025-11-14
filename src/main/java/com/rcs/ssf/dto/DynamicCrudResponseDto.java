package com.rcs.ssf.dto;

import java.util.List;
import java.util.Map;

public class DynamicCrudResponseDto {
    private List<Map<String, Object>> rows;
    private int totalCount;
    private List<ColumnMeta> columns;

    public DynamicCrudResponseDto(List<Map<String, Object>> rows, int totalCount, List<ColumnMeta> columns) {
        this.rows = rows;
        this.totalCount = totalCount;
        this.columns = columns;
    }

    // Getters
    public List<Map<String, Object>> getRows() { return rows; }
    public int getTotalCount() { return totalCount; }
    public List<ColumnMeta> getColumns() { return columns; }

    public static class ColumnMeta {
        private String name;
        private String type;
        private boolean nullable;
        private boolean primaryKey;

        public ColumnMeta(String name, String type, boolean nullable) {
            this(name, type, nullable, false);
        }

        public ColumnMeta(String name, String type, boolean nullable, boolean primaryKey) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
            this.primaryKey = primaryKey;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isNullable() { return nullable; }
        public boolean isPrimaryKey() { return primaryKey; }
    }
}