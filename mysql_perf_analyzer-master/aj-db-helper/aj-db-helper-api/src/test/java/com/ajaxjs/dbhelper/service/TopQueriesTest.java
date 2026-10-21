package com.ajaxjs.dbhelper.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopQueriesTest {
    @Test
    void rejectsInvalidDigestBeforeGettingConnection() {
        PrefSchemaService service = new PrefSchemaService();
        assertThrows(IllegalArgumentException.class, () -> service.topQueriesChanges("robot", null));
        assertThrows(IllegalArgumentException.class, () -> service.topQueriesChanges("robot", ""));
        assertThrows(IllegalArgumentException.class, () -> service.topQueriesChanges("robot", "abc' OR 1=1"));
        assertThrows(IllegalArgumentException.class, () -> service.topQueriesChanges("robot", "a".repeat(33)));
    }
    @Test
    void validatesSupportedSortColumnsAndBoundaries() {
        String[] fields = { "sum_timer_wait", "avg_timer_wait", "sum_lock_time", "sum_rows_examined", "sum_rows_sent",
                "sum_created_tmp_disk_tables", "sum_created_tmp_tables", "sum_select_full_join", "sum_select_scan",
                "sum_sort_merge_passes", "sum_sort_rows", "sum_no_index_used", "sum_no_good_index_used" };
        for (String field : fields)
            assertEquals(field, PrefSchemaService.validateTopQueryOptions(field, 1, 5));
        assertEquals("avg_timer_wait", PrefSchemaService.validateTopQueryOptions(" AVG_TIMER_WAIT ", 52560000, 1000));
        assertEquals("sum_timer_wait", PrefSchemaService.validateTopQueryOptions("sum_timer_wait", 0, 1));
    }

    @Test
    void rejectsSqlFragmentsAndBadLimitsBeforeGettingConnection() {
        PrefSchemaService service = new PrefSchemaService();
        assertThrows(IllegalArgumentException.class, () -> service.topQueries("sum_timer_wait DESC, SLEEP(10)", 1, 5));
        assertThrows(IllegalArgumentException.class, () -> service.topQueries(null, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> service.topQueries("sum_timer_wait", -1, 5));
        assertThrows(IllegalArgumentException.class, () -> service.topQueries("sum_timer_wait", 52560001, 5));
        assertThrows(IllegalArgumentException.class, () -> service.topQueries("sum_timer_wait", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.topQueries("sum_timer_wait", 1, 1001));
    }
}
