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
 * 表示result row相关的类型。
 */
public class ResultRow implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 2131095887594423404L;

    /**
     * 保存column descriptor信息。
     */
    @Getter
    @Setter
    private ColumnDescriptor columnDescriptor;
    /**
     * 保存columns集合。
     */
    @Getter
    @Setter
    private List<String> columns;//we only need string value for display

    /**
     * 创建ResultRow对象。
     */
    public ResultRow() {

    }


    /**
     * The user can build columns by adding column one by one. The user should not use setColumns when this is used.
     * This is convenient when there are only a few columns to build
     *
     * @param s
     */
    public void addColumn(String s) {

        if (this.columns == null)
            this.columns = new ArrayList<>();

        this.columns.add(s);
    }
}
