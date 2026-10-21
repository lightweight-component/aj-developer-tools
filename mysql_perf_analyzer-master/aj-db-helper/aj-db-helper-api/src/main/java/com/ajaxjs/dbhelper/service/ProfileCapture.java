package com.ajaxjs.dbhelper.service;

import com.ajaxjs.dbhelper.model.ProfilingResult;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 当前连接的 profiling 生命周期；不关闭由请求管理的连接。 */
final class ProfileCapture implements AutoCloseable {
    private final Connection connection;
    /** 放在查询开头，即使 SHOW PROFILES 截断长 SQL 也能识别目标。 */
    private final String marker = "/*aj_profile_" + UUID.randomUUID().toString().replace("-", "") + "*/ ";
    private int originalProfiling;
    private int originalHistorySize;
    private boolean restoreProfiling;
    private boolean restoreHistory;
    private boolean enabled;
    private String message;

    ProfileCapture(Connection connection) {
        this.connection = connection;
    }

    /** 按实际服务器能力检测，兼容支持 profiling 的旧版及不同发行版。 */
    void enable() throws SQLException {
        Map<String, String> variables = new LinkedHashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SHOW SESSION VARIABLES LIKE 'profiling%'")) {
            while (result.next())
                variables.put(result.getString(1).toLowerCase(Locale.ROOT), result.getString(2));
        }

        if (!variables.containsKey("profiling") || !variables.containsKey("profiling_history_size")) {
            message = "当前数据库不支持 SHOW PROFILE，仍已执行查询并返回会话统计。";
            return;
        }

        originalProfiling = "ON".equalsIgnoreCase(variables.get("profiling")) || "1".equals(variables.get("profiling")) ? 1 : 0;
        originalHistorySize = Integer.parseInt(variables.get("profiling_history_size"));

        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION profiling_history_size = 100");
            restoreHistory = true;
            statement.execute("SET SESSION profiling = 1");
            restoreProfiling = true;
            enabled = true;
        } catch (SQLException ex) {
            // 不因阶段分析不可用而重复执行或阻断会话统计；close 仍恢复原值。
            message = "当前数据库无法开启阶段分析：" + ex.getMessage();
        }
    }

    String query(String sql) {
        return enabled ? marker + sql : sql;
    }

    /** 明确选择带唯一标记的 Query_ID，不依赖容易被驱动和采样语句改变的“末次查询”。 */
    void collect(ProfilingResult target) {
        if (!enabled) {
            target.setProfileMessage(message);
            return;
        }

        try {
            Long queryId = null;

            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SHOW PROFILES")) {
                while (result.next()) {
                    String query = result.getString("Query");

                    if (query != null && query.startsWith(marker))
                        queryId = result.getLong("Query_ID");
                }
            }

            if (queryId == null) {
                target.setProfileMessage("未找到本次查询的阶段记录，会话统计仍然有效。未重新执行 SQL。");
                return;
            }

            List<Map<String, String>> rows = new ArrayList<>();

            // Query_ID 来自服务器的整数结果，不拼接用户输入。
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SHOW PROFILE ALL FOR QUERY " + queryId)) {
                ResultSetMetaData metadata = result.getMetaData();

                while (result.next()) {
                    Map<String, String> row = new LinkedHashMap<>();

                    for (int i = 1; i <= metadata.getColumnCount(); i++)
                        row.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT), result.getString(i));

                    rows.add(row);
                }
            }

            target.setProfileInfo(rows);
            target.setProfileAvailable(!rows.isEmpty());

            if (rows.isEmpty())
                target.setProfileMessage("数据库未返回本次查询的阶段资料，会话统计仍然有效。");
        } catch (SQLException ex) {
            target.setProfileMessage("读取阶段分析失败：" + ex.getMessage() + "。会话统计仍然有效，未重新执行 SQL。");
        }
    }

    /** 成功及异常路径均恢复借用连接原有的会话设置，保留原始异常。 */
    @Override
    public void close() throws SQLException {
        if (!restoreProfiling && !restoreHistory)
            return;

        SQLException failure = null;

        if (restoreProfiling)
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION profiling = " + originalProfiling);
            } catch (SQLException ex) {
                failure = ex;
            }

        if (restoreHistory)
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION profiling_history_size = " + originalHistorySize);
            } catch (SQLException ex) {
                if (failure == null)
                    failure = ex;
                else
                    failure.addSuppressed(ex);
            }

        if (failure != null)
            throw failure;
    }
}
