/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.db;

import com.yahoo.dba.perf.myperf.common.ResultList;

/**
 * Process ResultList after query returned and returns a modified ResultList
 *
 * @author xrao
 */
public interface PostQueryResultProcessor {
    /**
     * 执行process操作。
     *
     * @param rs rs参数。
     * @return 处理结果。
     */
    ResultList process(ResultList rs);
}
