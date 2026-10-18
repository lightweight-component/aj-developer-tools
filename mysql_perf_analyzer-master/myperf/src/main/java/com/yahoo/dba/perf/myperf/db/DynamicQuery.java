/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.db;

/**
 * 表示dynamic query相关的类型。
 */
public interface DynamicQuery {
    /**
     * 获取query string。
     *
     * @param conn conn参数。
     * @param gc   gc参数。
     * @return 处理结果。
     */
    String getQueryString(DBConnectionWrapper conn, boolean gc);
}
