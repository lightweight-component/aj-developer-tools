package com.ajaxjs.devtools.firewall.repository;

import com.ajaxjs.devtools.firewall.model.FirewallAccessLog;
import com.ajaxjs.sqlman.Action;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * FirewallAccessLog 的 SqlMan 数据访问。
 */
@Repository
public class FirewallAccessLogRepository extends FirewallRepositorySupport {
    public List<FirewallAccessLog> findRecent(int limit) {
        return new Action("SELECT * FROM firewall_access_log ORDER BY request_time DESC LIMIT ?").query(limit).list(FirewallAccessLog.class);
    }

    public List<FirewallAccessLog> findByIpAddress(String ipAddress, int limit) {
        return new Action("SELECT * FROM firewall_access_log WHERE ip_address = ? ORDER BY request_time DESC LIMIT ?").query(ipAddress, limit).list(FirewallAccessLog.class);
    }

    public List<FirewallAccessLog> findByApiPath(String apiPath, int limit) {
        return new Action("SELECT * FROM firewall_access_log WHERE api_path = ? ORDER BY request_time DESC LIMIT ?").query(apiPath, limit).list(FirewallAccessLog.class);
    }

    public List<FirewallAccessLog> findBlocked(int limit) {
        return new Action("SELECT * FROM firewall_access_log WHERE block_reason IS NOT NULL ORDER BY request_time DESC LIMIT ?").query(limit).list(FirewallAccessLog.class);
    }

    public int batchInsert(List<FirewallAccessLog> logs) {
        return batchInsert("INSERT INTO firewall_access_log (ip_address, api_path, user_agent, request_method, status_code, block_reason, request_time, response_time) VALUES ", "(?, ?, ?, ?, ?, ?, ?, ?)", logs, item -> new Object[]{item.getIpAddress(), item.getApiPath(), item.getUserAgent(), item.getRequestMethod(), item.getStatusCode(), item.getBlockReason(), item.getRequestTime(), item.getResponseTime()});
    }

    public long countBlockedByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return new Action("SELECT COUNT(*) FROM firewall_access_log WHERE request_time >= ? AND request_time <= ? AND block_reason IS NOT NULL").query(startTime, endTime).oneValue(Long.class);
    }

    public long countByIpAndTimeRange(String ipAddress, LocalDateTime startTime, LocalDateTime endTime) {
        return new Action("SELECT COUNT(*) FROM firewall_access_log WHERE ip_address = ? AND request_time >= ? AND request_time <= ?").query(ipAddress, startTime, endTime).oneValue(Long.class);
    }

    public long countByApiAndTimeRange(String apiPath, LocalDateTime startTime, LocalDateTime endTime) {
        return new Action("SELECT COUNT(*) FROM firewall_access_log WHERE api_path = ? AND request_time >= ? AND request_time <= ?").query(apiPath, startTime, endTime).oneValue(Long.class);
    }

}
