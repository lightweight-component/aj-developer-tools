package com.ajaxjs.dbhelper.service;

import com.ajaxjs.dbhelper.controller.PrefSchemaController;
import com.ajaxjs.dbhelper.util.MysqlVersion;
import com.ajaxjs.sqlman.Action;
import com.ajaxjs.sqlman.JdbcConnection;
import com.ajaxjs.util.ObjectHelper;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Locale;

@Service
public class PrefSchemaService implements PrefSchemaController {
    /** ORDER BY 只能来自白名单，不能将请求中的 SQL 片段直接拼入查询。 */
    private static final Set<String> TOP_QUERY_SORT_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "sum_timer_wait", "avg_timer_wait", "sum_lock_time", "sum_rows_examined", "sum_rows_sent",
            "sum_created_tmp_disk_tables", "sum_created_tmp_tables", "sum_select_full_join", "sum_select_scan",
            "sum_sort_merge_passes", "sum_sort_rows", "sum_no_index_used", "sum_no_good_index_used")));

    /** 校验排序列、时间范围及返回数量，校验失败不执行任何 SQL。 */
    static String validateTopQueryOptions(String by, int backToMins, int count) {
        String field = by == null ? "" : by.trim().toLowerCase(Locale.ROOT);
        if (!TOP_QUERY_SORT_FIELDS.contains(field))
            throw new IllegalArgumentException("不支持的热点查询排序字段");
        if (backToMins < 0 || backToMins > 52560000)
            throw new IllegalArgumentException("热点查询时间范围无效");
        if (count < 1 || count > 1000)
            throw new IllegalArgumentException("热点查询返回条数必须在 1 到 1000 之间");
        return field;
    }
    @Override
    public List<Map<String, Object>> metaDataLock() {
        checkVersion();

        String sql = "SELECT th.PROCESSLIST_ID, th.PROCESSLIST_USER, th.PROCESSLIST_HOST, th.PROCESSLIST_COMMAND, th.PROCESSLIST_STATE\n" +
                "\t, th.PROCESSLIST_TIME, ml.object_type, ml.object_schema, ml.object_name, ml.LOCK_TYPE\n" +
                "\t, ml.LOCK_STATUS, ml.LOCK_DURATION, th.PROCESSLIST_INFO\n" +
                "FROM performance_schema.threads th, performance_schema.metadata_locks ml\n" +
                "WHERE ml.OWNER_THREAD_ID = th.thread_id";

        return new Action(sql).query().list();
    }

    @Override
    public List<Map<String, Object>> memoryGlobal() {
        checkVersion();

        String sql = "SELECT event_name, count_alloc, count_free, sum_number_of_bytes_alloc, sum_number_of_bytes_free\n" +
                "\t, low_count_used, current_count_used, high_count_used, low_number_of_bytes_used, current_number_of_bytes_used\n" +
                "\t, high_number_of_bytes_used\n" +
                "FROM performance_schema.memory_summary_global_by_event_name\n" +
                "WHERE count_alloc > 0";

        return new Action(sql).query().list();
    }

    void checkVersion() {
        Connection connection = JdbcConnection.getConnection();

        try {
            if (!MysqlVersion.isAtLeast(connection, 5, 7))
                throw new UnsupportedOperationException("当前 MySQL " + connection.getMetaData().getDatabaseProductVersion()
                        + " 不支持全局内存统计，需要 MySQL 5.7 或更高版本。");
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取 MySQL 版本，未执行全局内存统计查询", ex);
        }
    }

    @Override
    public List<Map<String, Object>> memoryThread(String threadId) {
        checkVersion();

        String sql = "SELECT thread_id, event_name, count_alloc, count_free, sum_number_of_bytes_alloc\n" +
                "\t, sum_number_of_bytes_free, low_count_used, current_count_used, high_count_used, low_number_of_bytes_used\n" +
                "\t, current_number_of_bytes_used, high_number_of_bytes_used\n" +
                "FROM performance_schema.memory_summary_by_thread_by_event_name\n" +
                "WHERE count_alloc > 0\n";

        if (ObjectHelper.isEmptyText(threadId))
            return new Action(sql).query().list();
        else {
            sql += "AND thread_id LIKE ?";

            return new Action(sql).query(threadId).list();
        }
    }

    @Override
    public List<Map<String, Object>> topQueries(String by, int backToMins, int count) {
        String field = validateTopQueryOptions(by, backToMins, count);
        String sql = "SELECT * FROM performance_schema.events_statements_summary_by_digest\n";
        Connection connection = JdbcConnection.getConnection();
        // 兼容旧页面的 All=52560000；0 也表示不限制 LAST_SEEN。
        if (backToMins == 0 || backToMins == 52560000)
            return new Action(connection, sql + "ORDER BY " + field + " DESC LIMIT ?").query(count).list();

        sql += "WHERE timestampadd(minute, ?, last_seen) >= LOCALTIMESTAMP\nORDER BY " + field + " DESC LIMIT ?";
        return new Action(connection, sql).query(backToMins, count).list();
    }

    @Override
    public Map<String, Object> topQueriesChanges(String schemaName, String digest) {
        if (digest == null || !digest.trim().matches("(?i)([0-9a-f]{32}|[0-9a-f]{64})"))
            throw new IllegalArgumentException("Digest 必须是 32 或 64 位十六进制摘要标识");

        String sql = "SELECT COUNT_STAR, SUM_TIMER_WAIT, SUM_LOCK_TIME, SUM_ERRORS, SUM_WARNINGS\n" +
                "\t, SUM_ROWS_AFFECTED, SUM_ROWS_SENT, SUM_ROWS_EXAMINED, SUM_CREATED_TMP_DISK_TABLES, SUM_CREATED_TMP_TABLES\n" +
                "\t, SUM_SELECT_FULL_JOIN, SUM_SELECT_FULL_RANGE_JOIN, SUM_SELECT_RANGE, SUM_SELECT_RANGE_CHECK, SUM_SELECT_SCAN\n" +
                "\t, SUM_SORT_MERGE_PASSES, SUM_SORT_RANGE, SUM_SORT_ROWS, SUM_SORT_SCAN, SUM_NO_INDEX_USED\n" +
                "\t, SUM_NO_GOOD_INDEX_USED\n" +
                "FROM performance_schema.events_statements_summary_by_digest\n" +
                "WHERE SCHEMA_NAME <=> ? AND DIGEST = ?";

        // 空 Schema 对应未指定默认数据库的摘要，不是通配查询。
        return new Action(JdbcConnection.getConnection(), sql).query(
                schemaName == null || schemaName.isEmpty() ? null : schemaName,
                digest.trim().toLowerCase(Locale.ROOT)).one();
    }
}
