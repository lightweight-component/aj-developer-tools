/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.sql.ResultSet;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * MySQL process list info
 *
 * @author xrao
 */
public class ProcessListEntry implements Serializable, Comparable<ProcessListEntry> {

    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    private String id;
    @Getter
    @Setter
    private String user;
    @Getter
    private String host;
    @Getter
    @Setter
    private String db;
    @Getter
    @Setter
    private String command;
    @Getter
    @Setter
    private int time;
    @Getter
    @Setter
    private String state;
    @Getter
    @Setter
    private String info;

    //note not all MySQL servers have these metrics (percona 5.5.10 or later). For 5.6, we can use performance schema
    @Getter
    @Setter
    private long time_ms = -1L;
    @Getter
    @Setter
    private long rows_read = 0;
    @Getter
    @Setter
    private long rows_examined = 0;
    @Getter
    @Setter
    private long rows_sent = 0;

    public ProcessListEntry() {

    }

    /**
     * Construct from one row
     *
     * @param rs
     */
    public ProcessListEntry(ResultSet rs)
            throws SQLException {
        this.setId(rs.getString("ID"));
        this.setUser(rs.getString("USER"));
        this.setHost(rs.getString("HOST"));
        this.setDb(rs.getString("DB"));
        this.setCommand(rs.getString("COMMAND"));
        this.setState(rs.getString("STATE"));
        this.setTime(rs.getInt("TIME"));
        this.setInfo(rs.getString("INFO"));
    }

    /**
     * @param rs
     * @param rowInfo If true, with ROWS_SENT, ROWS_EXAMINED and ROWS_READ
     * @throws java.sql.SQLException
     */
    public ProcessListEntry(ResultSet rs, boolean rowInfo, boolean hasRowsRead)
            throws SQLException {
        this.setId(rs.getString("ID"));
        this.setUser(rs.getString("USER"));
        this.setHost(rs.getString("HOST"));
        this.setDb(rs.getString("DB"));
        this.setCommand(rs.getString("COMMAND"));
        this.setState(rs.getString("STATE"));
        this.setTime(rs.getInt("TIME"));
        this.setInfo(rs.getString("INFO"));
        if (rowInfo) {
            this.setRows_examined(rs.getLong("ROWS_EXAMINED"));
            if (hasRowsRead)
                this.setRows_read(rs.getLong("ROWS_READ"));
            this.setRows_sent(rs.getLong("ROWS_SENT"));
        }
    }

    public void setHost(String host) {
        if (host == null)
            this.host = host;
        int idx = host.indexOf(":");//strip :
        if (idx < 0)
            this.host = host;
        else
            this.host = host.substring(0, idx) + "";
    }

    @Override
    public int compareTo(ProcessListEntry obj) {
        if (obj == null)
            return 1;
        String s = obj.getInfo();
        if ((s == null || s.isEmpty()) && (this.info == null || this.info.isEmpty()))
            return 0;//both are empty
        else if (s == null || s.isEmpty())
            return 1;//the other is empty
        else if (this.info == null || this.info.isEmpty())
            return -1;//we are empty

        try {
            //nobody empty
            return this.info.compareTo(s);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("info: " + this.info + ", comparedTo " + s);
        }
        return -1;
    }
}
