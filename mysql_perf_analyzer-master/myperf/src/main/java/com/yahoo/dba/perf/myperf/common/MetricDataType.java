/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;

public enum MetricDataType {

    BYTE(0, 1, "BYTE"),
    SHORT(1, 2, "SHORT"),
    INT(2, 4, "INT"),
    LONG(3, 8, "LONG"),
    FLOAT(4, 4, "FLOAT"),
    DOUBLE(5, 8, "DOUBLE");

    @Getter
    private final int code;
    @Getter
    private final int length;//data length
    @Getter
    private final String description;//data description

    MetricDataType(int code, int length, String description) {
        this.code = code;
        this.length = length;
        this.description = description;
    }

}

