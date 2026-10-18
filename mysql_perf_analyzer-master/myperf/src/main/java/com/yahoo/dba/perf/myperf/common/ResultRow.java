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

public class ResultRow implements Serializable {
    private static final long serialVersionUID = 2131095887594423404L;

    @Getter
    @Setter
    private ColumnDescriptor columnDescriptor;
    @Getter
    @Setter
    private List<String> columns;//we only need string value for display

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
