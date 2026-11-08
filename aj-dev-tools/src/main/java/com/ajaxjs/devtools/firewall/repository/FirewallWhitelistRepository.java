package com.ajaxjs.devtools.firewall.repository;

import com.ajaxjs.devtools.firewall.model.FirewallWhitelistRow;
import com.ajaxjs.sqlman.Action;
import com.ajaxjs.sqlman.Action;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FirewallWhitelistRow 的 SqlMan 数据访问。
 */
@Repository
public class FirewallWhitelistRepository extends FirewallRepositorySupport {

    public FirewallWhitelistRow findById(Long id) {
        return new Action("SELECT * FROM firewall_whitelist WHERE id = ?").query(id).one(FirewallWhitelistRow.class);
    }

    public FirewallWhitelistRow findByIpAddress(String ipAddress) {
        return new Action("SELECT * FROM firewall_whitelist WHERE ip_address = ?").query(ipAddress).one(FirewallWhitelistRow.class);
    }

    public boolean isWhitelisted(String ipAddress) {
        return new Action("SELECT COUNT(*) FROM firewall_whitelist WHERE ip_address = ? AND enabled = true").query(ipAddress).oneValue(Long.class) != 0;
    }

    public int deleteByIpAddress(String ipAddress) {
        return new Action(new FirewallWhitelistRow()).update().delete("firewall_whitelist", "ip_address", ipAddress).getEffectedRows();
    }

    public int updateEnabled(Long id, Boolean enabled) {
        return write("UPDATE firewall_whitelist SET enabled = ?, updated_time = CURRENT_TIMESTAMP WHERE id = ?", enabled, id);
    }

    public int batchInsert(List<FirewallWhitelistRow> whitelists) {
        return batchInsert("INSERT INTO firewall_whitelist (ip_address, description, enabled) VALUES ", "(?, ?, ?)", whitelists, item -> new Object[]{item.getIpAddress(), item.getDescription(), item.getEnabled()});
    }

    public int batchUpdateEnabled(List<Long> ids, Boolean enabled) {
        return updateIds("UPDATE firewall_whitelist SET enabled = ?, updated_time = CURRENT_TIMESTAMP WHERE id IN ", ids, enabled);
    }

    public long count() {
        return new Action("SELECT COUNT(*) FROM firewall_whitelist").query().oneValue(Long.class);
    }

    public long countValid() {
        return new Action("SELECT COUNT(*) FROM firewall_whitelist WHERE enabled = true").query().oneValue(Long.class);
    }
}
