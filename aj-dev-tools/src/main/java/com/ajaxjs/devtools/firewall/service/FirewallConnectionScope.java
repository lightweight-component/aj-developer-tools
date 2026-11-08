package com.ajaxjs.devtools.firewall.service;

import com.ajaxjs.sqlman.JdbcConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 为启动加载、定时任务和异步任务提供当前线程的 SqlMan 连接。
 */
@Component
@RequiredArgsConstructor
class FirewallConnectionScope {
    private final DataSource dataSource;

    void run(Runnable task) {
        if (hasConnection()) {
            task.run();
            return;
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);

        try {
            JdbcConnection.setConnection(connection);
            try {
                task.run();
            } finally {
                JdbcConnection.setConnection(null);
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private boolean hasConnection() {
        try {
            return JdbcConnection.getConnection() != null;
        } catch (IllegalStateException e) {
            // SqlMan 对未绑定或已关闭的连接抛出 IllegalStateException。
            return false;
        }
    }
}
