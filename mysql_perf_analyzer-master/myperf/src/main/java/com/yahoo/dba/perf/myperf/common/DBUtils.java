/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * JDBC utility functions
 *
 * @author xrao
 */
@Slf4j
public class DBUtils {
    //private static final String INSTANCE_INFO_SQL="select INST_NUMBER, INST_NAME from v$active_instances";

    /**
     * Check if the given jdbc connection is still good
     *
     * @param conn
     * @return
     */
    public static boolean checkConnection(Connection conn) {
        //String sql = "select sysdate from dual";
        String sql = "select now()";
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(180);
            try (ResultSet rs = stmt.executeQuery(sql)) {

                if (rs != null && rs.next())
                    return true;

                return false;
            }
        } catch (Exception ex) {
        }
        return false;
    }

    /**
     * 执行close操作。
     *
     * @param stmt stmt参数。
     */
    public static void close(Statement stmt) {

        if (stmt != null) try {
            stmt.close();
        } catch (Exception ex) {
        }
    }

    /**
     * 执行close操作。
     *
     * @param rs rs参数。
     */
    public static void close(ResultSet rs) {

        if (rs != null) try {
            rs.close();
        } catch (Exception ex) {
        }
    }

    /**
     * 执行close操作。
     *
     * @param conn conn参数。
     */
    public static void close(Connection conn) {

        if (conn != null) try {
            conn.close();
        } catch (Exception ex) {
        }
    }

    /**
     * Test is we can connect to db with given user name and password.
     * If testRWPermission is true, we will test if we can create/drop/insert/delete
     * a test table (for metrics db).
     *
     * @param dbinfo
     * @param username
     * @param password
     * @param testRWPermission
     * @return
     */
    public static String testConnection(DBInstanceInfo dbinfo,
                                        String username,
                                        String password,
                                        boolean testRWPermission) {
        String url = dbinfo.getConnectionString();
        try {
            log.info("Test connection to (" + dbinfo + "): " + url);
            DriverManager.setLoginTimeout(60);
            //if(!dbinfo.isVirtualHost())//only update for non virtual host
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                //  dbinfo.setInstance(getConnectionInstanceNumber(conn));
                if (conn != null) {
                    dbinfo.setConnectionVerified(true);
                    log.info("Connection test succeeded to (" + dbinfo + ")");

                    if (testRWPermission)
                        try (Statement stmt = conn.createStatement()) {
                            //create table
                            stmt.execute("create table mtr_test (id int auto_increment, v varchar(30), primary key(id))");
                            stmt.execute("insert into mtr_test (v) values('123')");
                            try (ResultSet rs = stmt.executeQuery("select v from mtr_test")) {
                                String testData = null;

                                if (rs != null && rs.next())
                                    testData = rs.getString(1);

                                if (!"123".equals(testData))
                                    return "Failed insert/select test. Expect 123, got " + testData + ".";
                            }
                            stmt.execute("delete from mtr_test where v='123'");
                            stmt.execute("drop table mtr_test");
                        }

                    return null;
                } else {
                    log.error("Connection test failed: reason null.");
                    return "Connection test failed: reason null.";
                }
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            return "Connection test to " + url + " failed: " + ex.getMessage();
        }
    }

    /**
     * 测试connection。
     *
     * @param dbinfo   dbinfo参数。
     * @param username username参数。
     * @param password password参数。
     * @return 处理结果。
     */
    public static String testConnection(DBInstanceInfo dbinfo,
                                        String username,
                                        String password) {
        String url = dbinfo.getConnectionString();
        try {
            log.info("Test connection to (" + dbinfo + "): " + url);
            DriverManager.setLoginTimeout(60);
            try (Connection conn = DriverManager.getConnection(url, username, password)) {

                if (conn != null) {
                    dbinfo.setConnectionVerified(true);
                    log.info("Connection test succeeded to (" + dbinfo + ")");
                    return null;
                } else {
                    log.error("Connection test failed: reason null.");
                    return "Connection test failed: reason null.";
                }
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            return "Connection test to " + url + " failed: " + ex.getMessage();
        }
    }

    /**
     * 判断是否包含table。
     *
     * @param conn       conn参数。
     * @param schemaName schema name参数。
     * @param tableName  table name参数。
     * @return 是否满足条件。
     */
    public static boolean hasTable(Connection conn, String schemaName, String tableName) {
        try (ResultSet rs = conn.getMetaData().getTables(null, schemaName, tableName.toUpperCase(), null)) {
            while (rs.next()) {

                if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME")))
                    return true;
            }
        } catch (Exception ex) {
        }
        return false;
    }

    /**
     * 查找dbcredential。
     *
     * @param ctx     ctx参数。
     * @param dbid    dbid参数。
     * @param appUser app user参数。
     * @return 处理结果。
     */
    public static DBCredential findDBCredential(MyPerfContext ctx, String dbid, AppUser appUser) {
        DBCredential cred = null;

        if (appUser == null)
            return null;

        try {
            //first, check if the user has his own credential
            cred = ctx.getMetaDb().retrieveDBCredential(appUser.getName(), dbid);

            if (cred != null && !appUser.getName().equalsIgnoreCase(cred.getAppUser()))
                log.info(appUser.getName() + ": get cred for " + cred.getUsername());

        } catch (Exception ex) {
            log.warn(appUser.getName() + ": failed to get cred for " + cred.getUsername());
        }
        return cred;
    }

}