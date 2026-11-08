package com.ajaxjs.devtools.firewall.service;

import com.ajaxjs.devtools.firewall.repository.FirewallRepositorySupport;
import com.ajaxjs.sqlman.Action;
import com.ajaxjs.sqlman.JdbcConnection;
import com.ajaxjs.devtools.firewall.model.FirewallAccessLog;
import com.ajaxjs.devtools.firewall.model.FirewallStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防火墙服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirewallService extends FirewallRepositorySupport {
    /**
     * MySQL/MariaDB 按日期和路径原子累加请求量及加权平均响应时间。
     */
    private static final String MYSQL_ACCUMULATE = """
            INSERT INTO firewall_statistics (stat_date, api_path, total_requests, blocked_requests, avg_response_time)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                avg_response_time = (avg_response_time * total_requests
                    + VALUES(avg_response_time) * VALUES(total_requests))
                    / NULLIF(total_requests + VALUES(total_requests), 0),
                total_requests = total_requests + VALUES(total_requests),
                blocked_requests = blocked_requests + VALUES(blocked_requests),
                updated_time = CURRENT_TIMESTAMP
            """;

    /**
     * H2 的统计累加语句，匹配时更新，否则插入。
     */
    private static final String H2_ACCUMULATE = """
            MERGE INTO firewall_statistics target
            USING (VALUES (CAST(? AS DATE), CAST(? AS VARCHAR(200)), CAST(? AS BIGINT),
                           CAST(? AS BIGINT), CAST(? AS DECIMAL(10,2))))
                incoming(stat_date, api_path, total_requests, blocked_requests, avg_response_time)
            ON target.stat_date = incoming.stat_date AND target.api_path = incoming.api_path
            WHEN MATCHED THEN UPDATE SET
                avg_response_time = (target.avg_response_time * target.total_requests
                    + incoming.avg_response_time * incoming.total_requests)
                    / NULLIF(target.total_requests + incoming.total_requests, 0),
                total_requests = target.total_requests + incoming.total_requests,
                blocked_requests = target.blocked_requests + incoming.blocked_requests,
                updated_time = CURRENT_TIMESTAMP
            WHEN NOT MATCHED THEN INSERT
                (stat_date, api_path, total_requests, blocked_requests, avg_response_time)
            VALUES (incoming.stat_date, incoming.api_path, incoming.total_requests,
                    incoming.blocked_requests, incoming.avg_response_time)
            """;

    private final FirewallConnectionScope connectionScope;

    /**
     * 异步记录访问日志
     *
     * @param accessLog 访问日志
     */
    @Async
    public void logAccessAsync(FirewallAccessLog accessLog) {
        connectionScope.run(() -> {
            FirewallAccessLog values = writeCopy(accessLog);
            accessLog.setId(new Action(values).create().execute(true, Long.class).getNewlyId());
            updateStatistics(accessLog);    // 更新统计数据
        });
    }

    /**
     * 更新统计数据
     *
     * @param accessLog 访问日志
     */
    private void updateStatistics(FirewallAccessLog accessLog) {
        FirewallStatistics statistics = FirewallStatistics.builder()
                .statDate(LocalDate.now())
                .apiPath(accessLog.getApiPath())
                .totalRequests(1L)
                .blockedRequests(accessLog.isBlocked() ? 1L : 0L)
                .avgResponseTime(BigDecimal.valueOf(accessLog.getResponseTime()))
                .build();
        Object[] args = {statistics.getStatDate(), statistics.getApiPath(), statistics.getTotalRequests(),
                statistics.getBlockedRequests(), statistics.getAvgResponseTime()};
        String database;
        try {
            database = JdbcConnection.getConnection().getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new IllegalStateException("无法读取数据库类型", e);
        }
        if ("H2".equals(database)) {
            try {
                write(H2_ACCUMULATE, args);
            } catch (RuntimeException e) {
                if (!(e.getCause() instanceof SQLException cause) || !"23505".equals(cause.getSQLState()))
                    throw e;

                // 同一日期和路径并发首次写入时，重新匹配另一事务创建的统计行。
                write(H2_ACCUMULATE, args);
            }
            return;
        }
        if ("MySQL".equals(database) || "MariaDB".equals(database)) {
            write(MYSQL_ACCUMULATE, args);
            return;
        }

        throw new UnsupportedOperationException("不支持的防火墙统计数据库: " + database);
    }

    /**
     * 获取访问日志
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param ipAddress IP地址（可选）
     * @param apiPath   API路径（可选）
     * @param limit     限制数量
     * @return 访问日志列表
     */
    public List<FirewallAccessLog> getAccessLogs(LocalDateTime startTime, LocalDateTime endTime, String ipAddress, String apiPath, Integer limit) {
        return new Action("SELECT * FROM firewall_access_log WHERE request_time >= ? AND request_time <= ? ORDER BY request_time DESC").query(startTime, endTime).list(FirewallAccessLog.class);
    }

    /**
     * 获取统计数据
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param apiPath   API路径（可选）
     * @return 统计数据列表
     */
    public List<FirewallStatistics> getStatistics(LocalDate startDate, LocalDate endDate, String apiPath) {
        if (apiPath != null && !apiPath.trim().isEmpty())
            return new Action("SELECT * FROM firewall_statistics WHERE stat_date >= ? AND stat_date <= ? AND api_path = ? ORDER BY stat_date DESC").query(startDate, endDate, apiPath).list(FirewallStatistics.class);
        else
            return new Action("SELECT * FROM firewall_statistics WHERE stat_date >= ? AND stat_date <= ? ORDER BY stat_date DESC, total_requests DESC").query(startDate, endDate).list(FirewallStatistics.class);
    }

    /**
     * 获取今日统计概览
     *
     * @return 统计概览
     */
    public Map<String, Object> getTodayOverview() {
        Map<String, Object> overview = new ConcurrentHashMap<>();
        LocalDate today = LocalDate.now();

        long totalRequests = new Action("SELECT COALESCE(SUM(total_requests), 0) FROM firewall_statistics WHERE stat_date = ?").query(today).oneValue(Long.class); // 今日总请求数
        overview.put("totalRequests", totalRequests);

        long blockedRequests = new Action("SELECT COALESCE(SUM(blocked_requests), 0) FROM firewall_statistics WHERE stat_date = ?").query(today).oneValue(Long.class);// 今日拦截数
        overview.put("blockedRequests", blockedRequests);
        overview.put("avgResponseTime", new Action("SELECT COALESCE(AVG(avg_response_time), 0) FROM firewall_statistics WHERE stat_date = ?").query(today).oneValue(Double.class));// 今日平均响应时间

        if (totalRequests > 0) {// 拦截率
            double blockRate = blockedRequests * 100.0 / totalRequests;
            overview.put("blockRate", Math.round(blockRate * 100.0) / 100.0);
        } else
            overview.put("blockRate", 0.0);

        List<Map<String, Object>> topApis = normalizeKeys(new Action("SELECT api_path, total_requests FROM firewall_statistics WHERE stat_date = ? ORDER BY total_requests DESC LIMIT ?").query(today, 5).list()); // 访问量最高的API
        overview.put("topApis", topApis);

        List<Map<String, Object>> topBlockedApis = normalizeKeys(new Action("SELECT api_path, CASE WHEN total_requests > 0 THEN (blocked_requests * 100.0 / total_requests) ELSE 0 END as block_rate FROM firewall_statistics WHERE stat_date = ? ORDER BY block_rate DESC LIMIT ?").query(today, 5).list());// 拦截率最高的API
        overview.put("topBlockedApis", topBlockedApis);

        return overview;
    }

    /**
     * 获取最近N天的趋势数据
     *
     * @param days 天数
     * @return 趋势数据
     */
    public List<Map<String, Object>> getTrendData(int days) {
        List<FirewallStatistics> trends = new Action("SELECT stat_date, SUM(total_requests) as total_requests, SUM(blocked_requests) as blocked_requests, AVG(avg_response_time) as avg_response_time FROM firewall_statistics WHERE stat_date >= ? GROUP BY stat_date ORDER BY stat_date").query(LocalDate.now().minusDays(days)).list(FirewallStatistics.class);

        if (trends == null)
            return List.of();

        return trends.stream().map(stat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("date", stat.getStatDate());
            map.put("totalRequests", stat.getTotalRequests());
            map.put("blockedRequests", stat.getBlockedRequests());
            map.put("avgResponseTime", stat.getAvgResponseTime());

            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取访问最频繁的IP地址
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     限制数量
     * @return IP地址列表
     */
    public List<Map<String, Object>> getTopIpAddresses(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return normalizeKeys(new Action("SELECT ip_address, COUNT(*) as count FROM firewall_access_log WHERE request_time >= ? AND request_time <= ? GROUP BY ip_address ORDER BY count DESC LIMIT ?").query(startTime, endTime, limit).list());
    }

    /**
     * 获取访问最频繁的API路径
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     限制数量
     * @return API路径列表
     */
    public List<Map<String, Object>> getTopApiPaths(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return normalizeKeys(new Action("SELECT api_path, COUNT(*) as count FROM firewall_access_log WHERE request_time >= ? AND request_time <= ? GROUP BY api_path ORDER BY count DESC LIMIT ?").query(startTime, endTime, limit).list());
    }

    /**
     * 统计指定时间范围内的请求数
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param ipAddress IP地址（可选）
     * @param apiPath   API路径（可选）
     * @return 请求数
     */
    public long countRequests(LocalDateTime startTime, LocalDateTime endTime, String ipAddress, String apiPath) {
        return new Action("SELECT COUNT(*) FROM firewall_access_log WHERE request_time >= ? AND request_time <= ?").query(startTime, endTime).oneValue(Long.class);
    }

    /**
     * 定时清理旧的访问日志（每天凌晨2点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanOldAccessLogs() {
        connectionScope.run(() -> {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30); // 保留30天
            log.info("清理30天前的访问日志，删除 {} 条记录", new Action(new FirewallAccessLog()).update(cutoffTime)
                    .deleteWhere("firewall_access_log", "request_time < ?").getEffectedRows());
        });
    }

    /**
     * 定时清理旧的统计数据（每天凌晨3点执行）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOldStatistics() {
        connectionScope.run(() -> {
            try {
                LocalDate cutoffDate = LocalDate.now().minusDays(90); // 保留90天
                int deleted = new Action(new FirewallStatistics()).update(cutoffDate)
                        .deleteWhere("firewall_statistics", "stat_date < ?").getEffectedRows();
                log.info("清理90天前的统计数据，删除 {} 条记录", deleted);
            } catch (Exception e) {
                log.error("清理旧统计数据失败", e);
            }
        });
    }

    /**
     * 获取系统健康状态
     *
     * @return 健康状态
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new ConcurrentHashMap<>();
        long logCount = new Action("SELECT COUNT(*) FROM firewall_access_log").query().oneValue(Long.class); // 检查数据库连接
        health.put("database", "UP");
        health.put("totalLogs", logCount);

        Map<String, Object> todayOverview = getTodayOverview(); // 检查今日数据
        health.put("todayStats", todayOverview);
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());

        return health;
    }
}