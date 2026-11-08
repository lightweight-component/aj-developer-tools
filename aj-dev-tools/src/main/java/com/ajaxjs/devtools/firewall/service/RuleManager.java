package com.ajaxjs.devtools.firewall.service;

import com.ajaxjs.devtools.firewall.repository.FirewallRepositorySupport;
import com.ajaxjs.sqlman.Action;
import com.ajaxjs.devtools.firewall.model.FirewallBlacklist;
import com.ajaxjs.devtools.firewall.model.FirewallBlacklistRow;
import com.ajaxjs.devtools.firewall.model.FirewallRule;
import com.ajaxjs.devtools.firewall.model.FirewallRuleRow;
import com.ajaxjs.devtools.firewall.model.FirewallWhitelist;
import com.ajaxjs.devtools.firewall.model.FirewallWhitelistRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防火墙规则管理器
 */
@Slf4j
@Service
public class RuleManager extends FirewallRepositorySupport {
    @Autowired
    private FirewallConnectionScope connectionScope;
    /**
     * 规则缓存
     */
    private volatile Map<String, FirewallRule> ruleCache = Map.of();

    /**
     * 黑名单缓存
     */
    private volatile Map<String, FirewallBlacklist> blacklistCache = Map.of();

    /**
     * 白名单缓存
     */
    private volatile Map<String, FirewallWhitelist> whitelistCache = Map.of();

    /**
     * 初始化加载规则
     */
    @PostConstruct
    public void init() {
        connectionScope.run(() -> {
            log.info("初始化防火墙规则管理器...");
            refreshRules();
            refreshBlacklist();
            refreshWhitelist();
            log.info("防火墙规则管理器初始化完成，加载规则: {}, 黑名单: {}, 白名单: {}", ruleCache.size(), blacklistCache.size(), whitelistCache.size());
        });
    }

    /**
     * 获取匹配指定API路径的规则
     *
     * @param apiPath API路径
     * @return 匹配的规则，如果没有匹配则返回null
     */
    public FirewallRule getMatchingRule(String apiPath) {
        if (apiPath == null)
            return null;

        Map<String, FirewallRule> snapshot = ruleCache;
        FirewallRule exactMatch = snapshot.get(apiPath);// 优先精确匹配

        if (exactMatch != null && exactMatch.isEffectiveEnabled())
            return exactMatch;

        for (FirewallRule rule : snapshot.values())// 模式匹配
            if (rule.isEffectiveEnabled() && rule.matches(apiPath))
                return rule;

        return null;
    }

    /**
     * 检查IP是否在黑名单中
     *
     * @param ipAddress IP地址
     * @return 是否在黑名单中
     */
    public boolean isBlacklisted(String ipAddress) {
        if (ipAddress == null)
            return false;

        Map<String, FirewallBlacklist> snapshot = blacklistCache;
        FirewallBlacklist exactMatch = snapshot.get(ipAddress);// 精确匹配

        if (exactMatch != null && exactMatch.isValid())
            return true;

        for (FirewallBlacklist blacklist : snapshot.values()) { // 模式匹配
            if (blacklist.isValid() && blacklist.matches(ipAddress))
                return true;
        }

        return false;
    }

    /**
     * 检查IP是否在白名单中
     *
     * @param ipAddress IP地址
     * @return 是否在白名单中
     */
    public boolean isWhitelisted(String ipAddress) {
        if (ipAddress == null)
            return false;

        Map<String, FirewallWhitelist> snapshot = whitelistCache;
        FirewallWhitelist exactMatch = snapshot.get(ipAddress); // 精确匹配

        if (exactMatch != null && exactMatch.isValid())
            return true;

        for (FirewallWhitelist whitelist : snapshot.values())// 模式匹配
            if (whitelist.isValid() && whitelist.matches(ipAddress))
                return true;

        return false;
    }

    /**
     * 获取所有规则
     *
     * @return 规则列表
     */
    public List<FirewallRuleRow> getAllRules() {
        return new Action("SELECT * FROM firewall_rule ORDER BY id").query().list(FirewallRuleRow.class);
    }

    /**
     * 获取所有黑名单
     *
     * @return 黑名单列表
     */
    public List<FirewallBlacklistRow> getAllBlacklist() {
        return new Action("SELECT * FROM firewall_blacklist ORDER BY id").query().list(FirewallBlacklistRow.class);
    }

    /**
     * 获取所有白名单
     *
     * @return 白名单列表
     */
    public List<FirewallWhitelistRow> getAllWhitelist() {
        return new Action("SELECT * FROM firewall_whitelist ORDER BY id").query().list(FirewallWhitelistRow.class);
    }

    /**
     * 添加或更新规则
     *
     * @param rule 规则
     * @return 是否成功
     */
    public boolean saveRule(FirewallRuleRow rule) {
        if (rule.getId() == null) {
            FirewallRuleRow values = writeCopy(rule);
            rule.setId(new Action(values).create().execute(true, Long.class).getNewlyId());
        } else {
            FirewallRuleRow values = writeCopy(rule);
            values.setUpdatedTime(LocalDateTime.now());
            new Action(values).update().withId().getEffectedRows();
        }

        refreshRules();
        log.info("规则保存成功: {}", rule.getRuleName());
        return true;
    }

    /**
     * 删除规则
     *
     * @param id 规则ID
     * @return 是否成功
     */
    public boolean deleteRule(Long id) {
        new Action(new FirewallRuleRow()).update().delete("firewall_rule", "id", id).getEffectedRows();
        refreshRules();
        log.info("规则删除成功: {}", id);
        return true;
    }

    /**
     * 添加黑名单
     *
     * @param blacklist 黑名单
     * @return 是否成功
     */
    public boolean addBlacklist(FirewallBlacklistRow blacklist) {
        FirewallBlacklistRow values = writeCopy(blacklist);
        blacklist.setId(new Action(values).create().execute(true, Long.class).getNewlyId());
        refreshBlacklist();
        log.info("黑名单添加成功: {}", blacklist.getIpAddress());

        return true;
    }

    /**
     * 删除黑名单
     *
     * @param id 黑名单ID
     * @return 是否成功
     */
    public boolean deleteBlacklist(Long id) {
        new Action(new FirewallBlacklistRow()).update().delete("firewall_blacklist", "id", id).getEffectedRows();
        refreshBlacklist();
        log.info("黑名单删除成功: {}", id);

        return true;
    }

    /**
     * 添加白名单
     *
     * @param whitelist 白名单
     * @return 是否成功
     */
    public boolean addWhitelist(FirewallWhitelistRow whitelist) {
        FirewallWhitelistRow values = writeCopy(whitelist);
        whitelist.setId(new Action(values).create().execute(true, Long.class).getNewlyId());
        refreshWhitelist();
        log.info("白名单添加成功: {}", whitelist.getIpAddress());

        return true;
    }

    /**
     * 删除白名单
     *
     * @param id 白名单ID
     * @return 是否成功
     */
    public boolean deleteWhitelist(Long id) {
        new Action(new FirewallWhitelistRow()).update().delete("firewall_whitelist", "id", id).getEffectedRows();
        refreshWhitelist();
        log.info("白名单删除成功: {}", id);

        return true;
    }

    /**
     * 更新黑名单
     *
     * @param blacklist 黑名单
     * @return 是否成功
     */
    public boolean updateBlacklist(FirewallBlacklistRow blacklist) {
        FirewallBlacklistRow values = writeCopy(blacklist);
        values.setUpdatedTime(LocalDateTime.now());

        new Action(values).update().withId().getEffectedRows();
        refreshBlacklist();
        log.info("黑名单更新成功: {}", blacklist.getIpAddress());

        return true;
    }

    /**
     * 更新白名单
     *
     * @param whitelist 白名单
     * @return 是否成功
     */
    public boolean updateWhitelist(FirewallWhitelistRow whitelist) {
        FirewallWhitelistRow values = writeCopy(whitelist);
        values.setUpdatedTime(LocalDateTime.now());
        new Action(values).update().withId().getEffectedRows();
        refreshWhitelist();
        log.info("白名单更新成功: {}", whitelist.getIpAddress());

        return true;
    }

    /**
     * 刷新规则缓存
     */
    public void refreshRules() {
        Map<String, FirewallRule> refreshed = new ConcurrentHashMap<>();
        for (FirewallRuleRow row : Optional.ofNullable(new Action("SELECT * FROM firewall_rule WHERE enabled = true ORDER BY id").query().list(FirewallRuleRow.class)).orElseGet(List::of)) {
            FirewallRule rule = new FirewallRule(row.getId(), row.getRuleName(), row.getApiPattern(),
                    row.getQpsLimit(), row.getUserLimit(), row.getTimeWindow(), row.getEnabled());
            refreshed.put(rule.getApiPattern(), rule);
        }
        ruleCache = refreshed;
        log.debug("规则缓存刷新完成，共加载 {} 条规则", ruleCache.size());
    }

    /**
     * 刷新黑名单缓存
     */
    public void refreshBlacklist() {
        Map<String, FirewallBlacklist> refreshed = new ConcurrentHashMap<>();
        for (FirewallBlacklistRow row : Optional.ofNullable(new Action("SELECT * FROM firewall_blacklist WHERE enabled = true AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP) ORDER BY id").query().list(FirewallBlacklistRow.class)).orElseGet(List::of)) {
            FirewallBlacklist entry = new FirewallBlacklist(row.getId(), row.getIpAddress(), row.getReason(), row.getExpireTime(), row.getEnabled());
            refreshed.put(entry.getIpAddress(), entry);
        }
        blacklistCache = refreshed;
        log.debug("黑名单缓存刷新完成，共加载 {} 条记录", blacklistCache.size());
    }

    /**
     * 刷新白名单缓存
     */
    public void refreshWhitelist() {
        Map<String, FirewallWhitelist> refreshed = new ConcurrentHashMap<>();
        for (FirewallWhitelistRow row : Optional.ofNullable(new Action("SELECT * FROM firewall_whitelist WHERE enabled = true ORDER BY id").query().list(FirewallWhitelistRow.class)).orElseGet(List::of)) {
            FirewallWhitelist entry = new FirewallWhitelist(row.getId(), row.getIpAddress(), row.getDescription(), row.getEnabled());
            refreshed.put(entry.getIpAddress(), entry);
        }
        whitelistCache = refreshed;
        log.debug("白名单缓存刷新完成，共加载 {} 条记录", whitelistCache.size());
    }

    /**
     * 定时刷新缓存（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledRefresh() {
        connectionScope.run(() -> {
            log.debug("定时刷新防火墙规则缓存...");
            refreshRules();
            refreshBlacklist();
            refreshWhitelist();

            int cleaned = new Action(new FirewallBlacklistRow()).update()
                    .deleteWhere("firewall_blacklist", "expire_time IS NOT NULL AND expire_time <= CURRENT_TIMESTAMP").getEffectedRows();// 清理过期的黑名单

            if (cleaned > 0) {
                log.info("清理过期黑名单 {} 条", cleaned);
                refreshBlacklist();
            }
        });
    }

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("ruleCount", ruleCache.size());
        stats.put("blacklistCount", blacklistCache.size());
        stats.put("whitelistCount", whitelistCache.size());

        return stats;
    }
}