package com.rcs.ssf.dto;

import java.util.List;
import java.util.Map;

public class DynamicCrudResponseDto {
    private List<Map<String, Object>> rows;
    private Integer totalCount;
    private List<ColumnMeta> columns;
    private boolean isAvailable;

    public DynamicCrudResponseDto(List<Map<String, Object>> rows, Integer totalCount, List<ColumnMeta> columns, boolean isAvailable) {
        this.rows = rows;
        this.totalCount = totalCount;
        this.columns = columns;
        this.isAvailable = isAvailable;
    }

    // Getters
    public List<Map<String, Object>> getRows() { return rows; }
    public Integer getTotalCount() { return totalCount; }
    public List<ColumnMeta> getColumns() { return columns; }
    public boolean isAvailable() { return isAvailable; }

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