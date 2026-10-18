/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.db;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import com.yahoo.dba.perf.myperf.common.DBInstanceInfo;
import com.yahoo.dba.perf.myperf.common.DBUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Wrap a database connection with additional information
 *
 * @author xrao
 */
@Slf4j
public class DBConnectionWrapper implements Comparable<DBConnectionWrapper> {
    @Getter
    @Setter
    private DBInstanceInfo db;
    @Getter
    @Setter
    private String appUser;//connection owner
    private Connection connection;//actual jdbc database connection
    @Getter
    private long createTime = System.currentTimeMillis();//time this object is created
    private boolean inuse;//some session has checked out this connection if true
    @Getter
    private boolean closed = false;
    @Getter
    @Setter
    private int instance_number;//instance_number retrieved from DB when connected, for Oracle
    @Getter
    @Setter
    private String version;//oracle version
    @Getter
    @Setter
    private Statement currentStatement;//current executing statement, for cancel during forceful close
    @Getter
    private long lastAccessTime = System.currentTimeMillis();

    public DBConnectionWrapper() {
    }

    public DBConnectionWrapper(DBInstanceInfo db) {
        this.db = db;
    }

    public Connection getConnection() {
        this.lastAccessTime = System.currentTimeMillis();
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
        retrieveInfoUponOpen();
    }

    /**
     * If connection last used time is 30 minutes away, treat it as expired
     *
     * @return
     */

    public boolean isExpired() {
        return System.currentTimeMillis() - this.lastAccessTime >= 1800000L;
    }

    public boolean isExpired(long timeMillis) {
        return System.currentTimeMillis() - this.lastAccessTime >= timeMillis;
    }

    //make it package level only
    boolean isInuse() {
        return inuse;
    }

    //make it package level only
    void setInuse(boolean inuse) {
        this.inuse = inuse;
    }

    public void close() {
        try {
            this.connection.close();
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        this.closed = true;
    }

    public int compareTo(DBConnectionWrapper conn) {

        if (this.db.getDbGroupName() != null && !this.db.getDbGroupName().equalsIgnoreCase(conn.getDb().getDbGroupName()))
            return this.getDb().getDbGroupName().compareTo(conn.getDb().getDbGroupName());
        else if (this.db.getDbGroupName() == null && conn.getDb().getDbGroupName() != null)
            return -1;
        else if (this.db.getHostName() != null && !this.db.getHostName().equalsIgnoreCase(conn.getDb().getHostName()))
            return this.db.getHostName().compareTo(conn.getDb().getHostName());

        return 0;
    }

    /**
     * Version and oracle instance info. Called by setConnection
     */
    private void retrieveInfoUponOpen() {

        if (this.connection == null)
            return;

        String sql = null;

        if ("oracle".equalsIgnoreCase(this.db.getDbType()))
            sql = "select instance_number, version from v$instance";
        else if ("mysql".equalsIgnoreCase(this.db.getDbType()))
            sql = "select @@global.version as version";
        else
            return;

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);

            if (rs != null && rs.next())
                if ("oracle".equalsIgnoreCase(this.db.getDbType())) {
                    this.instance_number = rs.getInt(1);
                    this.version = rs.getString(2);
                } else if ("mysql".equalsIgnoreCase(this.db.getDbType()))
                    this.version = rs.getString(1);


        } catch (Exception ex) {
            DBUtils.close(rs);
            DBUtils.close(stmt);
        }
    }
}
