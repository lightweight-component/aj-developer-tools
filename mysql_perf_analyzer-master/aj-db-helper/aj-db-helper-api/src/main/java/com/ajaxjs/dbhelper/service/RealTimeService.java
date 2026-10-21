package com.ajaxjs.dbhelper.service;

import com.ajaxjs.dbhelper.controller.RealTimeController;
import com.ajaxjs.dbhelper.model.GlobalStatusSnapshot;
import com.ajaxjs.sqlman.Action;
import com.ajaxjs.sqlman.JdbcConnection;
import com.ajaxjs.dbhelper.util.MysqlVersion;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class RealTimeService implements RealTimeController {
    /**
     * 按 JDBC 提供的主、次版本选择查询，统一新旧版本返回的列名。
     */
    @Override
    public GlobalStatusSnapshot globalStatus(String keyword) throws SQLException {
        // 连接由请求拦截器管理；这里只关闭本方法创建的 Statement 与 ResultSet。
        Connection connection = JdbcConnection.getConnection();
        String sql = MysqlVersion.isAtLeast(connection, 5, 7)
                ? "SHOW GLOBAL STATUS"
                : "SELECT VARIABLE_NAME, VARIABLE_VALUE FROM information_schema.GLOBAL_STATUS";
        String filter = keyword == null ? "" : keyword.trim().toUpperCase(Locale.ROOT);
        List<Map<String, String>> variables = new ArrayList<>();
        GlobalStatusSnapshot snapshot = new GlobalStatusSnapshot();

        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(15);

            try (ResultSet result = statement.executeQuery(sql)) {
                while (result.next()) {
                    String name = result.getString(1).toUpperCase(Locale.ROOT);
                    String value = result.getString(2);

                    if ("UPTIME".equals(name) && value != null)
                        snapshot.setUptime(Long.valueOf(value));

                    // 在内存中筛选少量状态项，避免 SHOW 与 SELECT 的参数语法差异。
                    if (!name.contains(filter))
                        continue;

                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("VARIABLE_NAME", name);
                    row.put("VARIABLE_VALUE", value);
                    variables.add(row);
                }
            }
        }

        variables.sort(Comparator.comparing(left -> left.get("VARIABLE_NAME")));
        snapshot.setVariables(variables);
        snapshot.setSampledAt(System.currentTimeMillis());

        return snapshot;
    }

    /** 获取全局变量，保留变量值的字符串形式，避免数值精度丢失。 */
    @Override
    public List<Map<String, String>> globalVariables() throws SQLException {
        Connection connection = JdbcConnection.getConnection();
        String sql = MysqlVersion.isAtLeast(connection, 5, 7)
                ? "SHOW GLOBAL VARIABLES"
                : "SELECT VARIABLE_NAME, VARIABLE_VALUE FROM information_schema.GLOBAL_VARIABLES";
        List<Map<String, String>> variables = new ArrayList<>();

        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(15);

            try (ResultSet result = statement.executeQuery(sql)) {
                while (result.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("VARIABLE_NAME", result.getString(1).toUpperCase(Locale.ROOT));
                    row.put("VARIABLE_VALUE", result.getString(2));
                    variables.add(row);
                }
            }
        }

        variables.sort(Comparator.comparing(row -> row.get("VARIABLE_NAME")));
        return variables;
    }

    @Override
    public List<Map<String, Object>> processlist(boolean isAll) {
        String sql = isAll
                ? "SELECT * FROM information_schema.processlist LIMIT 5000"
                : "SELECT * FROM information_schema.processlist WHERE command!='Sleep' LIMIT 5000";

        return new Action(sql).query().list();
    }
}
