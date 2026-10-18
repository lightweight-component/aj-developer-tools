/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

/**
 * A config block includes the time the data was gathered
 * and the configuration data in name=value pair
 * The data will be persisted in blocks, starting with time in square bracket,
 * followed by a list of lines of name value pair.
 * The time is in yyyy-MM-dd HH:mm:ss (UTC) format.
 *
 * @author xrao
 */
public class ConfigBlock implements Serializable {

    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 保存time信息。
     */
    @Getter
    @Setter
    private String time;
    /**
     * 保存variables集合。
     */
    @Getter
    @Setter
    private Map<String, String> variables;//well, in MySQL, there are called variables

    /**
     * 创建ConfigBlock对象。
     */
    public ConfigBlock() {
        this.variables = new TreeMap<>();
    }

    /**
     * 添加variable。
     *
     * @param name  name参数。
     * @param value value参数。
     */
    public void addVariable(String name, String value) {
        this.variables.put(name, value);
    }
}
