package com.ajaxjs.dbhelper.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SessionStatsTest {
    @Test
    void preservesPrecisionAndFiltersUnchangedAndGlobalCounters() {
        Map<String, BigDecimal> before = new HashMap<>();
        Map<String, BigDecimal> after = new HashMap<>();
        before.put("BYTES_SENT", new BigDecimal("9007199254740993"));
        after.put("BYTES_SENT", new BigDecimal("9007199254740995"));
        before.put("UNCHANGED", new BigDecimal("1.0"));
        after.put("UNCHANGED", new BigDecimal("1.00"));
        before.put("LAST_QUERY_COST", new BigDecimal("8"));
        after.put("LAST_QUERY_COST", new BigDecimal("1.263248"));
        after.put("INNODB_ROWS_READ", BigDecimal.ONE);
        after.put("UPTIME", BigDecimal.TEN);
        after.put("COM_SELECT", BigDecimal.ONE);
        Map<String, BigDecimal> result = ProfilingService.compareSessionStatus(before, after);
        assertEquals(new BigDecimal("2"), result.get("BYTES_SENT"));
        assertEquals(new BigDecimal("1.263248"), result.get("LAST_QUERY_COST"));
        assertEquals(BigDecimal.ONE, result.get("COM_SELECT"));
        assertEquals(3, result.size());
    }

    @Test
    void retainsCounterReturningToZeroAndZeroLastQueryValue() {
        Map<String, BigDecimal> before = new HashMap<>();
        Map<String, BigDecimal> after = new HashMap<>();
        before.put("COUNTER", BigDecimal.TEN);
        after.put("COUNTER", BigDecimal.ZERO);
        after.put("LAST_QUERY_COST", BigDecimal.ZERO);
        Map<String, BigDecimal> result = ProfilingService.compareSessionStatus(before, after);
        assertEquals(new BigDecimal("-10"), result.get("COUNTER"));
        assertEquals(BigDecimal.ZERO, result.get("LAST_QUERY_COST"));
    }

    @Test
    void rejectsNonSelectBeforeAcquiringConnection() {
        assertThrows(IllegalArgumentException.class, () -> new ProfilingService().sessionStats("delete from robot"));
        assertThrows(IllegalArgumentException.class, () -> new ProfilingService().sessionStats(" "));
    }
}
