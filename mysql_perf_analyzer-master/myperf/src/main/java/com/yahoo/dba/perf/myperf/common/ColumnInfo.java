/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Result column descriptor
 *
 * @author xrao
 */
public class ColumnInfo implements Serializable {

    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 2467837325706019159L;
    /**
     * 保存name信息。
     */
    @Getter
    @Setter
    private String name;//name of the column
    /**
     * 保存position信息。
     */
    @Getter
    @Setter
    private int position;//position of the column
    /**
     * 标记number type状态。
     */
    @Getter
    @Setter
    private boolean numberType;//no need to quote number type
    /**
     * 保存max length信息。
     */
    @Getter
    @Setter
    private int maxLength = 0;//used for formatting

    //support repository
    /**
     * 保存db data type信息。
     */
    @Getter
    @Setter
    private String dbDataType;//data type of the database column
    /**
     * 保存java type信息。
     */
    @Getter
    @Setter
    private String javaType;//data type in java
    /**
     * 保存java name信息。
     */
    @Getter
    @Setter
    private String javaName;//java bean member name
    /**
     * 保存src name信息。
     */
    @Getter
    @Setter
    private String srcName;//column name from source Oracle database

    /**
     * 创建ColumnInfo对象。
     */
    public ColumnInfo() {

    }

    /**
     * 创建ColumnInfo对象。
     *
     * @param name name参数。
     */
    public ColumnInfo(String name) {
        this.name = name;
    }

    /**
     * 执行copy操作。
     *
     * @return 处理结果。
     */
    public ColumnInfo copy() {
        ColumnInfo colInfo = new ColumnInfo(this.name);
        colInfo.setNumberType(this.numberType);
        return colInfo;
    }

}
