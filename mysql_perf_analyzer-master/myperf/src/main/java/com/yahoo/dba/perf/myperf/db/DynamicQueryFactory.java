/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.db;

import java.util.HashMap;
import java.util.Map;

/**
 * 表示dynamic query factory相关的类型。
 */
public class DynamicQueryFactory {
    /**
     * 定义query map常量。
     */
    private static final Map<String, DynamicQuery> queryMap = new HashMap<>();

    static {
        //queryMap.put("com.yahoo.dba.perf.framework.process.SharedCursorQuery", new SharedCursorQuery());
        //queryMap.put("com.yahoo.dba.perf.framework.process.InstanceCacheTransferSummary", new InstanceCacheTransferSummary());
        //queryMap.put("com.yahoo.dba.perf.framework.process.InstanceCacheTransferSummaryAll", new InstanceCacheTransferSummaryAll());
    }

    /**
     * 获取query。
     *
     * @param queryClass query class参数。
     * @param conn       conn参数。
     * @param gc         gc参数。
     * @return 处理结果。
     */
    public static String getQuery(String queryClass, DBConnectionWrapper conn, boolean gc) {
        if (queryMap.containsKey(queryClass))
            return queryMap.get(queryClass).getQueryString(conn, gc);

        return null;
    }
}
