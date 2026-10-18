/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;

/**
 * 表示metric data type相关的类型。
 */
public enum MetricDataType {

    /**
     * 定义byte常量。
     */
    BYTE(0, 1, "BYTE"),
    /**
     * 定义short常量。
     */
    SHORT(1, 2, "SHORT"),
    /**
     * 定义int常量。
     */
    INT(2, 4, "INT"),
    /**
     * 定义long常量。
     */
    LONG(3, 8, "LONG"),
    /**
     * 定义float常量。
     */
    FLOAT(4, 4, "FLOAT"),
    /**
     * 定义double常量。
     */
    DOUBLE(5, 8, "DOUBLE");

    /**
     * 保存code信息。
     */
    @Getter
    private final int code;
    /**
     * 保存length信息。
     */
    @Getter
    private final int length;//data length
    /**
     * 保存description信息。
     */
    @Getter
    private final String description;//data description

    /**
     * 创建MetricDataType对象。
     *
     * @param code        code参数。
     * @param length      length参数。
     * @param description description参数。
     */
    MetricDataType(int code, int length, String description) {
        this.code = code;
        this.length = length;
        this.description = description;
    }

}

