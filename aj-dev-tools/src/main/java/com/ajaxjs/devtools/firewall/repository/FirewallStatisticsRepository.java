package com.ajaxjs.devtools.firewall.repository;

import com.ajaxjs.devtools.firewall.model.FirewallStatistics;
import com.ajaxjs.sqlman.Action;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * FirewallStatistics 的 SqlMan 数据访问。
 */
@Repository
public class FirewallStatisticsRepository extends FirewallRepositorySupport {

    public List<FirewallStatistics> findByDate(LocalDate statDate) {
        return new Action("SELECT * FROM firewall_statistics WHERE stat_date = ? ORDER BY total_requests DESC").query(statDate).list(FirewallStatistics.class);
    }

    public List<FirewallStatistics> findByApiPath(String apiPath, int limit) {
        return new Action("SELECT * FROM firewall_statistics WHERE api_path = ? ORDER BY stat_date DESC LIMIT ?").query(apiPath, limit).list(FirewallStatistics.class);
    }

    public FirewallStatistics findByDateAndApiPath(LocalDate statDate, String apiPath) {
        return new Action("SELECT * FROM firewall_statistics WHERE stat_date = ? AND api_path = ?").query(statDate, apiPath).one(FirewallStatistics.class);
    }

    public int insert(FirewallStatistics statistics) {
        FirewallStatistics values = writeCopy(statistics);
        statistics.setId(new Action(values).create().execute(true, Long.class).getNewlyId());
        return 1;
    }

    public int update(FirewallStatistics statistics) {
        FirewallStatistics values = writeCopy(statistics);
        values.setUpdatedTime(java.time.LocalDateTime.now());
        return new Action(values).update().withId().getEffectedRows();
    }

    public long getTotalRequestsInDays(int days) {
        return new Action("SELECT COALESCE(SUM(total_requests), 0) FROM firewall_statistics WHERE stat_date >= ?").query(LocalDate.now().minusDays(days)).oneValue(Long.class);
    }

    public long getTotalBlockedInDays(int days) {
        return new Action("SELECT COALESCE(SUM(blocked_requests), 0) FROM firewall_statistics WHERE stat_date >= ?").query(LocalDate.now().minusDays(days)).oneValue(Long.class);
    }

    public double getAvgResponseTimeInDays(int days) {
        return new Action("SELECT COALESCE(AVG(avg_response_time), 0) FROM firewall_statistics WHERE stat_date >= ?").query(LocalDate.now().minusDays(days)).oneValue(Double.class);
    }

    public List<Map<String, Object>> getTopApiPaths(int days, int limit) {
        return normalizeKeys(new Action("SELECT api_path, SUM(total_requests) as total FROM firewall_statistics WHERE stat_date >= ? GROUP BY api_path ORDER BY total DESC LIMIT ?").query(LocalDate.now().minusDays(days), limit).list());
    }

    public List<Map<String, Object>> getTopBlockedApiPaths(int days, int limit) {
        return normalizeKeys(new Action("SELECT api_path, SUM(blocked_requests) * 100.0 / SUM(total_requests) as block_rate FROM firewall_statistics WHERE stat_date >= ? AND total_requests > 0 GROUP BY api_path HAVING SUM(total_requests) >= 10 ORDER BY block_rate DESC LIMIT ?").query(LocalDate.now().minusDays(days), limit).list());
    }

    public int batchInsert(List<FirewallStatistics> statisticsList) {
        return batchInsert("INSERT INTO firewall_statistics (stat_date, api_path, total_requests, blocked_requests, avg_response_time) VALUES ", "(?, ?, ?, ?, ?)", statisticsList, item -> new Object[]{item.getStatDate(), item.getApiPath(), item.getTotalRequests(), item.getBlockedRequests(), item.getAvgResponseTime()});
    }
}
