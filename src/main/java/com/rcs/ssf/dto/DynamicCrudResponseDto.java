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
        private Integer maxLength;
        private String defaultValue;
        private Integer precision;
        private Integer scale;
        private boolean unique;
        private String comment;
        private String foreignKeyTable;
        private String foreignKeyColumn;

        public ColumnMeta(String name, String type, boolean nullable) {
            this(name, type, nullable, false, null, null, null, null, false, null, null, null);
        }

        public ColumnMeta(String name, String type, boolean nullable, boolean primaryKey) {
            this(name, type, nullable, primaryKey, null, null, null, null, false, null, null, null);
        }

        public ColumnMeta(String name, String type, boolean nullable, boolean primaryKey, 
                         Integer maxLength, String defaultValue, Integer precision, Integer scale, 
                         boolean unique, String comment, String foreignKeyTable, String foreignKeyColumn) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
            this.primaryKey = primaryKey;
            this.maxLength = maxLength;
            this.defaultValue = defaultValue;
            this.precision = precision;
            this.scale = scale;
            this.unique = unique;
            this.comment = comment;
            this.foreignKeyTable = foreignKeyTable;
            this.foreignKeyColumn = foreignKeyColumn;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isNullable() { return nullable; }
        public boolean isPrimaryKey() { return primaryKey; }
        public Integer getMaxLength() { return maxLength; }
        public String getDefaultValue() { return defaultValue; }
        public Integer getPrecision() { return precision; }
        public Integer getScale() { return scale; }
        public boolean isUnique() { return unique; }
        public String getComment() { return comment; }
        public String getForeignKeyTable() { return foreignKeyTable; }
        public String getForeignKeyColumn() { return foreignKeyColumn; }
    }

    public static class SchemaMetadata {
        private String tableName;
        private List<ColumnMeta> columns;

        public SchemaMetadata(String tableName, List<ColumnMeta> columns) {
            this.tableName = tableName;
            this.columns = columns;
        }

        public String getTableName() { return tableName; }
        public List<ColumnMeta> getColumns() { return columns; }
    }
}