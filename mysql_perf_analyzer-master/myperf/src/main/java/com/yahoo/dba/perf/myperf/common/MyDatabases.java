/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 表示my databases相关的类型。
 */
public class MyDatabases implements Serializable {

    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = -8586381924495834726L;

    /**
     * 保存my db set集合。
     */
    private Set<String> myDbSet = new TreeSet<>();

    /**
     * 获取my db list。
     *
     * @return 处理结果。
     */
    synchronized public Set<String> getMyDbList() {
        return Collections.unmodifiableSet(this.myDbSet);
    }

    /**
     * 添加db。
     *
     * @param name name参数。
     */
    synchronized public void addDb(String name) {

        if (!this.myDbSet.contains(name))
            this.myDbSet.add(name);
    }

    /**
     * 添加dbs。
     *
     * @param names names参数。
     */
    synchronized public void addDbs(List<String> names) {

        for (String name : names)
            if (!this.myDbSet.contains(name))
                this.myDbSet.add(name);

    }

    /**
     * 移除db。
     *
     * @param name name参数。
     */
    synchronized public void removeDb(String name) {

        if (this.myDbSet.contains(name))
            this.myDbSet.remove(name);
    }

    /**
     * 执行replace db操作。
     *
     * @param oldName old name参数。
     * @param newName new name参数。
     */
    synchronized public void replaceDb(String oldName, String newName) {

        if (!this.myDbSet.contains(oldName)) {
            this.myDbSet.remove(oldName);
            this.myDbSet.remove(newName);

        }
    }

    /**
     * 执行size操作。
     *
     * @return 处理结果。
     */
    synchronized public int size() {
        return this.myDbSet.size();
    }
}
