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
 * Information about a group of DB, such as a DB cluster
 *
 * @author xrao
 */
public class DBGroupInfo implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 保存instances集合。
     */
    @Getter
    private List<DBInstanceInfo> instances = new ArrayList<>();
    /**
     * 保存group name信息。
     */
    @Getter
    @Setter
    private String groupName;

    /**
     * 创建DBGroupInfo对象。
     */
    public DBGroupInfo() {
    }

    /**
     * 获取instance。
     *
     * @param instanceNumber instance number参数。
     * @return 处理结果。
     */
    public DBInstanceInfo getInstance(int instanceNumber) {

        for (DBInstanceInfo inst : instances)
            if (instanceNumber == inst.getInstance())
                return inst;

        return null;
    }

    /**
     * If all hosts can be accessed directly
     *
     * @return
     */
    public boolean canAccessDirectly() {

        for (DBInstanceInfo inst : instances)
            if (!inst.isConnectionVerified())
                return false;

        return true;
    }

    /**
     * 查找instance by host。
     *
     * @param hostname hostname参数。
     * @return 处理结果。
     */
    public DBInstanceInfo findInstanceByHost(String hostname) {

        for (DBInstanceInfo dbinfo : this.instances)
            if (hostname.equalsIgnoreCase(dbinfo.getHostName()))
                return dbinfo;

        return null;
    }

    /**
     * 添加or update instance。
     *
     * @param dbinfo dbinfo参数。
     */
    public void addOrUpdateInstance(DBInstanceInfo dbinfo) {
        DBInstanceInfo db = findInstanceByHost(dbinfo.getHostName());

        if (db == null)
            this.instances.add(dbinfo);
        else {
            db.setPort(dbinfo.getPort());
            db.setDatabaseName(dbinfo.getDatabaseName());
            db.setUseTunneling(dbinfo.isUseTunneling());
            db.setLocalHostName(dbinfo.getLocalHostName());
            db.setLocalPort(dbinfo.getLocalPort());
            db.setInstance(dbinfo.getInstance());
            db.setConnectionVerified(dbinfo.isConnectionVerified());
        }
    }

}
