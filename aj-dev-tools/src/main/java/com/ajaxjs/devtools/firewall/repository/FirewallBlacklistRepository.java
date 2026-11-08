package com.ajaxjs.devtools.firewall.repository;

import com.ajaxjs.devtools.firewall.model.FirewallBlacklistRow;
import com.ajaxjs.sqlman.Action;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FirewallBlacklistRow 的 SqlMan 数据访问。
 */
@Repository
public class FirewallBlacklistRepository extends FirewallRepositorySupport {

    public FirewallBlacklistRow findById(Long id) {
        return new Action("SELECT * FROM firewall_blacklist WHERE id = ?").query(id).one(FirewallBlacklistRow.class);
    }

    public FirewallBlacklistRow findByIpAddress(String ipAddress) {
        return new Action("SELECT * FROM firewall_blacklist WHERE ip_address = ?").query(ipAddress).one(FirewallBlacklistRow.class);
    }

    public boolean isBlacklisted(String ipAddress) {
        return new Action("SELECT COUNT(*) FROM firewall_blacklist WHERE ip_address = ? AND enabled = true AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP)").query(ipAddress).oneValue(Long.class) != 0;
    }

    public int deleteByIpAddress(String ipAddress) {
        return new Action(new FirewallBlacklistRow()).update().delete("firewall_blacklist", "ip_address", ipAddress).getEffectedRows();
    }

    public int updateEnabled(Long id, Boolean enabled) {
        return write("UPDATE firewall_blacklist SET enabled = ?, updated_time = CURRENT_TIMESTAMP WHERE id = ?", enabled, id);
    }

    public int batchInsert(List<FirewallBlacklistRow> blacklists) {
        return batchInsert("INSERT INTO firewall_blacklist (ip_address, reason, expire_time, enabled) VALUES ", "(?, ?, ?, ?)", blacklists, item -> new Object[]{item.getIpAddress(), item.getReason(), item.getExpireTime(), item.getEnabled()});
    }

    public long count() {
        return new Action("SELECT COUNT(*) FROM firewall_blacklist").query().oneValue(Long.class);
    }

    public long countValid() {
        return new Action("SELECT COUNT(*) FROM firewall_blacklist WHERE enabled = true AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP)").query().oneValue(Long.class);
    }
}
