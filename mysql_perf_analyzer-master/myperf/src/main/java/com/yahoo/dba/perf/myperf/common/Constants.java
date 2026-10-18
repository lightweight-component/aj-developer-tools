/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

/**
 * 表示constants相关的类型。
 */
public class Constants {
    //Analyzer version
    /**
     * 定义version常量。
     */
    public static final String VERSION = "2.0";

    //Status
    /**
     * 定义status ok常量。
     */
    public static final int STATUS_OK = 0;
    /**
     * 定义status bad常量。
     */
    public static final int STATUS_BAD = -1;

    // DB instance management action
    /**
     * 定义dbm action add cluster常量。
     */
    public static final int DBM_ACTION_ADD_CLUSTER = 0;
    /**
     * 定义dbm action add cluster using vip常量。
     */
    public static final int DBM_ACTION_ADD_CLUSTER_USING_VIP = 1;
    /**
     * 定义dbm action add host常量。
     */
    public static final int DBM_ACTION_ADD_HOST = 2;
    /**
     * 定义dbm action update host常量。
     */
    public static final int DBM_ACTION_UPDATE_HOST = 3;
    /**
     * 定义dbm action remove host常量。
     */
    public static final int DBM_ACTION_REMOVE_HOST = 4;
    /**
     * 定义dbm action remove cluster常量。
     */
    public static final int DBM_ACTION_REMOVE_CLUSTER = 5;
    /**
     * 定义dbm action rename cluster常量。
     */
    public static final int DBM_ACTION_RENAME_CLUSTER = 6;
    /**
     * 定义dbm action acl常量。
     */
    public static final int DBM_ACTION_ACL = 7;

    /**
     * 定义url path cmd常量。
     */
    public static final String URL_PATH_CMD = "CMD";
    /**
     * 定义url path dbgroup常量。
     */
    public static final String URL_PATH_DBGROUP = "DBGROUP";
    /**
     * 定义url path dbhost常量。
     */
    public static final String URL_PATH_DBHOST = "DBHOST";
    /**
     * 定义url path metrics常量。
     */
    public static final String URL_PATH_METRICS = "METRICS";
    /**
     * 定义url path start ts常量。
     */
    public static final String URL_PATH_START_TS = "START_TS";
    /**
     * 定义url path end ts常量。
     */
    public static final String URL_PATH_END_TS = "END_TS";
    /**
     * 定义url path alert type常量。
     */
    public static final String URL_PATH_ALERT_TYPE = "ALERT_TYPE";

    /**
     * 定义session debug常量。
     */
    public static final String SESSION_DEBUG = "sess_debug";

    /**
     * 定义conn msg noretry常量。
     */
    public static final String CONN_MSG_NORETRY = "CONN_NO_RETRY";
}
