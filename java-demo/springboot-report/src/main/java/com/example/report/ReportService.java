package com.example.report;

import com.example.report.ReportController;
import com.example.report.entity.ColumnInfo;
import com.example.report.entity.FilterCondition;
import com.example.report.entity.QueryRequest;
import com.example.report.entity.TableInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService implements ReportController {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<TableInfo> getTables(String databaseName) {
        String sql = "SELECT TABLE_NAME, TABLE_COMMENT, " +
                "(SELECT COUNT(*) FROM information_schema.COLUMNS C WHERE C.TABLE_NAME = T.TABLE_NAME AND C.TABLE_SCHEMA = T.TABLE_SCHEMA) as COLUMN_COUNT " +
                "FROM information_schema.TABLES T " +
                "WHERE T.TABLE_SCHEMA = ? AND T.TABLE_TYPE = 'BASE TABLE' " +
                "ORDER BY TABLE_NAME";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, databaseName);
        List<TableInfo> tables = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            TableInfo table = new TableInfo((String) row.get("TABLE_NAME"), (String) row.get("TABLE_COMMENT"), ((Number) row.get("COLUMN_COUNT")).intValue());
            tables.add(table);
        }

        return tables;
    }

    @Override
    public List<ColumnInfo> getColumns(String databaseName, String tableName) {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_TYPE " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, databaseName, tableName);
        List<ColumnInfo> columns = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            ColumnInfo column = new ColumnInfo((String) row.get("COLUMN_NAME"),
                    (String) row.get("DATA_TYPE"),
                    (String) row.get("COLUMN_COMMENT"),
                    "YES".equals(row.get("IS_NULLABLE")),
                    (String) row.get("COLUMN_TYPE")
            );
            columns.add(column);
        }

        return columns;
    }

    @Override
    public List<String> getDatabases() {
        String sql = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA " +
                "WHERE SCHEMA_NAME NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys') " +
                "ORDER BY SCHEMA_NAME";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<String> databases = new ArrayList<>();

        for (Map<String, Object> row : rows)
            databases.add((String) row.get("SCHEMA_NAME"));

        return databases;
    }

    @Override
    public List<Map<String, Object>> executeQuery(String databaseName, QueryRequest request) {
        String sql = buildSQL(request, databaseName);
        System.out.println("Generated SQL: " + sql);

        if (request.getLimit() > 0)
            sql += " LIMIT " + request.getLimit();

        return jdbcTemplate.queryForList(sql);
    }

    private String buildSQL(QueryRequest request, String databaseName) {
        StringBuilder sql = new StringBuilder();

        // SELECT clause
        sql.append("SELECT ");

        List<String> selectFields = new ArrayList<>();
        boolean hasDimensions = request.getDimensions() != null && !request.getDimensions().isEmpty();
        boolean hasMetrics = request.getMetrics() != null && !request.getMetrics().isEmpty();

        // Add dimensions
        if (hasDimensions)
            selectFields.addAll(request.getDimensions());

        // Add metrics with aggregation
        if (hasMetrics) {
            List<ColumnInfo> columns = getColumns(databaseName, request.getTableName());

            for (String metric : request.getMetrics()) {
                String dataType = getDataTypeForColumn(columns, metric);
                String aggFunction = getAggregationFunction(dataType);
                selectFields.add(aggFunction + "(" + metric + ") as " + metric);
            }
        } else if (hasDimensions)
            // 智能优化：如果只有维度没有指标，自动添加 COUNT(*) 作为默认指标
            // 这样前端就能看到每个维度的记录数，而不是重复数据
            selectFields.add("COUNT(*) as record_count");

        // If no dimensions and no metrics, select all
        if (selectFields.isEmpty())
            sql.append("*");
        else
            sql.append(String.join(", ", selectFields));

        // FROM clause
        sql.append(" FROM ").append(databaseName).append(".").append(request.getTableName());

        // WHERE clause
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            sql.append(" WHERE ");
            List<String> conditions = new ArrayList<>();

            for (FilterCondition filter : request.getFilters()) {
                String condition = buildCondition(filter);

                if (condition != null)
                    conditions.add(condition);
            }

            if (!conditions.isEmpty())
                sql.append(String.join(" AND ", conditions));
        }

        // GROUP BY clause - 修复：只要有多个维度就应该分组
        // 多个维度时，必须分组以避免数据重复
        if (hasDimensions)
            // 重要：只要有多个维度就生成 GROUP BY
            sql.append(" GROUP BY ").append(String.join(", ", request.getDimensions()));

        return sql.toString();
    }

    private String buildCondition(FilterCondition filter) {
        if (filter.getField() == null || filter.getOperator() == null || filter.getValue() == null)
            return null;

        String field = filter.getField();
        String operator = filter.getOperator();
        String value = filter.getValue();

        switch (operator.toLowerCase()) {
            case "=":
            case "eq":
                return field + " = '" + escapeSql(value) + "'";
            case "!=":
            case "ne":
                return field + " != '" + escapeSql(value) + "'";
            case ">":
            case "gt":
                return field + " > " + value;
            case ">=":
            case "gte":
                return field + " >= " + value;
            case "<":
            case "lt":
                return field + " < " + value;
            case "<=":
            case "lte":
                return field + " <= " + value;
            case "like":
                return field + " LIKE '%" + escapeSql(value) + "%'";
            case "in":
                return field + " IN (" + value + ")";
            default:
                return null;
        }
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private String getDataTypeForColumn(List<ColumnInfo> columns, String columnName) {
        for (ColumnInfo column : columns)
            if (column.getColumnName().equals(columnName))
                return column.getDataType();

        return "varchar";
    }

    private String getAggregationFunction(String dataType) {
        if (dataType != null && (dataType.toLowerCase().contains("int") ||
                dataType.toLowerCase().contains("decimal") ||
                dataType.toLowerCase().contains("float") ||
                dataType.toLowerCase().contains("double")))
            return "SUM";

        return "COUNT";
    }
}
