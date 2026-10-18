/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Store qresultset of a query
 *
 * @author xrao
 */
public class ResultList implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1126296256817182221L;
    /**
     * 保存column descriptor信息。
     */
    @Getter
    @Setter
    private ColumnDescriptor columnDescriptor;//meta data
    /**
     * 保存rows集合。
     */
    @Getter
    private List<ResultRow> rows;//results
    /**
     * 保存total response time信息。
     */
    @Getter
    @Setter
    private long totalResponseTime = 0L;//total time used in milliseconds
    /**
     * 保存total execution time信息。
     */
    @Getter
    @Setter
    private long totalExecutionTime = 0L;//total DB execution time used in milliseconds
    /**
     * 保存total fetch time信息。
     */
    @Getter
    @Setter
    private long totalFetchTime = 0L;//total Resultset Fetch time used in milliseconds
    /**
     * 保存custom objects集合。
     */
    @Getter
    @Setter
    private Map<String, CustomResultObject> customObjects;//The key is the json key name

    /**
     * 创建ResultList对象。
     */
    public ResultList() {
        rows = new ArrayList<>();
    }

    /**
     * 创建ResultList对象。
     *
     * @param capacity capacity参数。
     */
    public ResultList(int capacity) {
        rows = new ArrayList<>(capacity);
    }

    /**
     * 获取column index。
     *
     * @param colName col name参数。
     * @return 处理结果。
     */
    public int getColumnIndex(String colName) {
        return this.columnDescriptor.getColumnIndex(colName);
    }

    /**
     * 添加row。
     *
     * @param row row参数。
     */
    public void addRow(ResultRow row) {
        this.rows.add(row);
    }

    /**
     * 添加custome object。
     *
     * @param obj obj参数。
     */
    public void addCustomeObject(CustomResultObject obj) {

        if (obj == null)
            return;

        if (this.customObjects == null)
            this.customObjects = new HashMap<>();

        this.customObjects.put(obj.getName(), obj);
    }
}
