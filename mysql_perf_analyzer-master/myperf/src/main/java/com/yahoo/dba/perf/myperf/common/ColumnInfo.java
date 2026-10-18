/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

/**
 * Result column descriptor
 *
 * @author xrao
 */
public class ColumnInfo implements java.io.Serializable {

    private static final long serialVersionUID = 2467837325706019159L;
    @Getter
    @Setter
    private String name;//name of the column
    @Getter
    @Setter
    private int position;//position of the column
    @Getter
    @Setter
    private boolean numberType;//no need to quote number type
    @Getter
    @Setter
    private int maxLength = 0;//used for formatting

    //support repository
    @Getter
    @Setter
    private String dbDataType;//data type of the database column
    @Getter
    @Setter
    private String javaType;//data type in java
    @Getter
    @Setter
    private String javaName;//java bean member name
    @Getter
    @Setter
    private String srcName;//column name from source Oracle database

    public ColumnInfo() {

    }

    public ColumnInfo(String name) {
        this.name = name;
    }

    public ColumnInfo copy() {
        ColumnInfo colInfo = new ColumnInfo(this.name);
        colInfo.setNumberType(this.numberType);
        return colInfo;
    }

}
