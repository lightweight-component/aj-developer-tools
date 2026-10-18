/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import java.io.PrintWriter;

/**
 * Allow application specific information summary to take advantage of application convention
 *
 * @author xrao
 */
interface ProcessListEntryProcessor {
    /**
     * 处理entry。
     *
     * @param e e参数。
     */
    void processEntry(ProcessListEntry e);

    /**
     * 执行dump summary操作。
     *
     * @param pw pw参数。
     */
    void dumpSummary(PrintWriter pw);
}
