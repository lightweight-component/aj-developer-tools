/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import java.io.Serializable;

/**
 * An app specific object to be inserted into ResultList as part of result header
 *
 * @author xrao
 */
public interface CustomResultObject extends Serializable {
    /**
     * 获取name。
     *
     * @return 处理结果。
     */
    String getName();//the name/key in json ResultList

    /**
     * 获取value json string。
     *
     * @return 处理结果。
     */
    String getValueJsonString();//the value in json ResultList
}
