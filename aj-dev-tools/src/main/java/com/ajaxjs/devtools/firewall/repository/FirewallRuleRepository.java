package com.ajaxjs.devtools.firewall.repository;

import com.ajaxjs.devtools.firewall.model.FirewallRuleRow;
import com.ajaxjs.sqlman.Action;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FirewallRuleRow 的 SqlMan 数据访问。
 */
@Repository
public class FirewallRuleRepository extends FirewallRepositorySupport {

    public FirewallRuleRow findById(Long id) {
        return new Action("SELECT * FROM firewall_rule WHERE id = ?").query(id).one(FirewallRuleRow.class);
    }

    public FirewallRuleRow findByApiPattern(String apiPattern) {
        return new Action("SELECT * FROM firewall_rule WHERE api_pattern = ? AND enabled = true").query(apiPattern).one(FirewallRuleRow.class);
    }

    public List<FirewallRuleRow> findMatchingRules(String apiPath) {
        return new Action("SELECT * FROM firewall_rule WHERE enabled = true ORDER BY LENGTH(api_pattern) DESC").query().list(FirewallRuleRow.class);
    }

    public int updateEnabled(Long id, Boolean enabled) {
        return write("UPDATE firewall_rule SET enabled = ?, updated_time = CURRENT_TIMESTAMP WHERE id = ?", enabled, id);
    }

    public int batchUpdateEnabled(List<Long> ids, Boolean enabled) {
        return updateIds("UPDATE firewall_rule SET enabled = ?, updated_time = CURRENT_TIMESTAMP WHERE id IN ", ids, enabled);
    }

    public long count() {
        return new Action("SELECT COUNT(*) FROM firewall_rule").query().oneValue(Long.class);
    }

    public long countEnabled() {
        return new Action("SELECT COUNT(*) FROM firewall_rule WHERE enabled = true").query().oneValue(Long.class);
    }
}
