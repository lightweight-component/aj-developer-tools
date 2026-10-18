/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.TreeMap;
import java.io.Serializable;

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

    private static final long serialVersionUID = 1L;
    @Getter
    @Setter
    private String time;
    @Getter
    @Setter
    private Map<String, String> variables;//well, in MySQL, there are called variables

    public ConfigBlock() {
        this.variables = new TreeMap<>();
    }

    public void addVariable(String name, String value) {
        this.variables.put(name, value);
    }
}
