/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

/**
 * Store qresultset of a query
 *
 * @author xrao
 */
public class ResultList implements java.io.Serializable {
    private static final long serialVersionUID = 1126296256817182221L;
    @Getter
    @Setter
    private ColumnDescriptor columnDescriptor;//meta data
    @Getter
    private java.util.List<ResultRow> rows;//results
    @Getter
    @Setter
    private long totalResponseTime = 0L;//total time used in milliseconds
    @Getter
    @Setter
    private long totalExecutionTime = 0L;//total DB execution time used in milliseconds
    @Getter
    @Setter
    private long totalFetchTime = 0L;//total Resultset Fetch time used in milliseconds
    @Getter
    @Setter
    private java.util.Map<String, CustomResultObject> customObjects;//The key is the json key name

    public ResultList() {
        rows = new java.util.ArrayList<ResultRow>();
    }

    public ResultList(int capacity) {
        rows = new java.util.ArrayList<ResultRow>(capacity);
    }

    public int getColumnIndex(String colName) {
        return this.columnDescriptor.getColumnIndex(colName);
    }

    public void addRow(ResultRow row) {
        this.rows.add(row);
    }

    public void addCustomeObject(CustomResultObject obj) {
        if (obj == null) return;
        if (this.customObjects == null) this.customObjects = new java.util.HashMap<String, CustomResultObject>();
        this.customObjects.put(obj.getName(), obj);
    }
}
