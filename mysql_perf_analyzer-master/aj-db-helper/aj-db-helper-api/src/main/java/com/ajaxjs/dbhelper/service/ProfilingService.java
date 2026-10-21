package com.ajaxjs.dbhelper.service;

import com.ajaxjs.dbhelper.controller.ProfilingController;
import com.ajaxjs.dbhelper.model.ProfilingResult;
import com.ajaxjs.dbhelper.util.MysqlVersion;
import com.ajaxjs.sqlman.Action;
import com.ajaxjs.sqlman.JdbcConnection;
import com.ajaxjs.util.ObjectHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Service
public class ProfilingService implements ProfilingController {
    @Override
    public ProfilingResult analyze(String sql) {
        checkQuerySafety(sql);
        Connection connection = JdbcConnection.getConnection();

        try (ProfileCapture profile = new ProfileCapture(connection)) {
            profile.enable();
            ProfilingResult result = new ProfilingResult();
            result.setSessionStats(executeWithStats(connection, profile.query(sql)));
            profile.collect(result);

            return result;
        } catch (SQLException ex) {
            throw new IllegalStateException("性能剖析失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Map<String, String>> sessionStats(String sql) {
        checkQuerySafety(sql);

        return executeWithStats(JdbcConnection.getConnection(), sql);
    }

    /**
     * 单次执行目标 SQL，并在同一个连接上采样；供两种接口共享。
     */
    private List<Map<String, String>> executeWithStats(Connection connection, String sql) {
        try {
            Map<String, BigDecimal> before = readSessionStatus(connection);
            long rows = 0;
            long started;
            long executed;
            long fetched;

            // 流式读取并丢弃业务数据，避免将大结果集全部保存在内存中。
            try (Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                statement.setFetchSize(Integer.MIN_VALUE);
                statement.setQueryTimeout(60);
                started = System.nanoTime();

                try (ResultSet result = statement.executeQuery(sql)) {
                    executed = System.nanoTime();

                    while (result.next())
                        rows++;
                }

                fetched = System.nanoTime();
            }

            Map<String, BigDecimal> stats = compareSessionStatus(before, readSessionStatus(connection));
            stats.put("_RETURNED_ROWS", BigDecimal.valueOf(rows));
            stats.put("_TIME_EXEC_MS", BigDecimal.valueOf(executed - started, 6));
            stats.put("_TIME_FETCH_MS", BigDecimal.valueOf(fetched - executed, 6));
            List<Map<String, String>> result = new ArrayList<>();

            stats.forEach((name, value) -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("VARIABLE_NAME", name);
                row.put("VARIABLE_VALUE", value.stripTrailingZeros().toPlainString());
                result.add(row);
            });

            return result;
        } catch (SQLException ex) {
            throw new IllegalStateException("会话统计失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 通过 SQLMan 在当前连接上采样；SHOW STATUS 的非数值字段不参与计算。
     */
    private static Map<String, BigDecimal> readSessionStatus(Connection connection) {
        List<Map<String, Object>> rows = new Action(connection, "SHOW SESSION STATUS").query().list();
        Map<String, BigDecimal> result = new TreeMap<>();

        if (rows == null)
            throw new IllegalStateException("无法读取会话状态");

        for (Map<String, Object> row : rows) {
            String name = null;
            String value = null;

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey().replace("_", "").toUpperCase(Locale.ROOT);

                if ("VARIABLENAME".equals(key))
                    name = String.valueOf(entry.getValue()).toUpperCase(Locale.ROOT);

                if ("VALUE".equals(key))
                    value = String.valueOf(entry.getValue());
            }

            if (name != null && value != null) {
                try {
                    result.put(name, new BigDecimal(value));
                } catch (NumberFormatException ignored) {
                    // SSL 等文本状态不是数值指标。
                }
            }
        }

        return result;
    }

    /**
     * 保留旧系统的筛选规则；LAST_QUERY 指标展示末次查询值，其余展示变化量。
     */
    static Map<String, BigDecimal> compareSessionStatus(Map<String, BigDecimal> before, Map<String, BigDecimal> after) {
        Map<String, BigDecimal> result = new TreeMap<>();
        after.forEach((name, value) -> {
            if (name.startsWith("INNODB") || name.startsWith("UPTIME"))
                return;

            if (name.startsWith("LAST_QUERY")) {
                result.put(name, value);
                return;
            }

            BigDecimal delta = value.subtract(before.getOrDefault(name, BigDecimal.ZERO));

            if (delta.compareTo(BigDecimal.ZERO) != 0)
                result.put(name, delta);
        });

        return result;
    }

    @Override
    public List<Map<String, Object>> explainPlan(String sql) {
        checkQuerySafety(sql);

        return new Action("EXPLAIN " + sql).query().list();
    }

    @Override
    public Map<String, Object> explainPlanJson(String sql) {
        checkQuerySafety(sql);
        Connection connection = JdbcConnection.getConnection();

        try {
            if (!MysqlVersion.isAtLeast(connection, 5, 6, 5))
                throw new UnsupportedOperationException("当前 MySQL " + connection.getMetaData().getDatabaseProductVersion()
                        + " 不支持 JSON 执行计划，需要 MySQL 5.6.5 或更高版本，请使用普通“执行计划”。");
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取 MySQL 版本，未执行 JSON 执行计划查询", ex);
        }

        return new Action(connection, "EXPLAIN FORMAT=JSON " + sql).query().one();
    }

    /**
     * 检查query safety。
     *
     * @param sqlText sql text参数。
     */
    private static void checkQuerySafety(String sqlText) {
        if (ObjectHelper.isEmptyText(sqlText))
            throw new IllegalArgumentException("No query text provided");

        String s = sqlText.trim();

        //remove any comments
        while (s.startsWith("/*")) {
            int idx = s.indexOf("*/");

            if (idx < 0)
                break;

            s = s.substring(idx + 2);
            s = s.trim();
        }

        //remove --
        while (s.startsWith("--")) {
            int idx = s.indexOf("\n");

            if (idx < 0)
                idx = s.indexOf("\r");

            if (idx < 0)
                break;

            s = s.substring(idx + 1);
            s = s.trim();
        }

        if (s.isEmpty())
            throw new IllegalArgumentException("No valid query text provided");

        if (!s.toLowerCase().startsWith("select") && !s.toLowerCase().startsWith("(select"))
            throw new IllegalArgumentException("Only SELECT statement is supported at this moment");
    }
}
