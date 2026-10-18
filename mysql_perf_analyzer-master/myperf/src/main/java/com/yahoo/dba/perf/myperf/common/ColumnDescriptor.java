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
import java.util.List;

/**
 * Information about the columns from the a query retiurn results
 *
 * @author xrao
 */
public class ColumnDescriptor implements Serializable {

    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 6923318258330634209L;

    /**
     * 保存columns集合。
     */
    @Getter
    @Setter
    private List<ColumnInfo> columns;

    /**
     * 创建ColumnDescriptor对象。
     */
    public ColumnDescriptor() {
        columns = new ArrayList<>();
    }

    /**
     * 获取column。
     *
     * @param colName col name参数。
     * @return 处理结果。
     */
    public ColumnInfo getColumn(String colName) {

        for (int i = this.columns.size() - 1; i >= 0; i--)
            if (colName.equalsIgnoreCase(this.columns.get(i).getName()))
                return this.columns.get(i);

        return null;
    }

    /**
     * 获取column index。
     *
     * @param colName col name参数。
     * @return 处理结果。
     */
    public int getColumnIndex(String colName) {

        for (int i = this.columns.size() - 1; i >= 0; i--)
            if (colName.equalsIgnoreCase(this.columns.get(i).getName()))
                return i;

        return -1;
    }

    /**
     * 添加column。
     *
     * @param colName  col name参数。
     * @param isNumber is number参数。
     * @param pos      pos参数。
     */
    public void addColumn(String colName, boolean isNumber, int pos) {

        if (this.columns == null)
            this.columns = new ArrayList<>();

        ColumnInfo col = new ColumnInfo();
        col.setName(colName);
        col.setNumberType(isNumber);
        col.setPosition(pos);
        this.columns.add(col);
    }
}
