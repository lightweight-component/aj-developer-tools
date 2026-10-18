/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.process;

import com.yahoo.dba.perf.myperf.common.*;
import com.yahoo.dba.perf.myperf.db.ConnectionFactory;
import com.yahoo.dba.perf.myperf.process.model.UnsafeQueryException;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * MySQL query profiler
 *
 * @author xrao
 */
public class MyProfiler implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 保存framework context信息。
     */
    @Getter
    @Setter
    private MyPerfContext frameworkContext;//access common data

    /**
     * The caller should clone the database info, plus the target database (schema) name
     */
    @Getter
    @Setter
    private DBInstanceInfo dbinfo;//target database
    /**
     * 标记gather plan状态。
     */
    @Getter
    @Setter
    private boolean gatherPlan;//if true, run explain plan first
    /**
     * 标记gather stats状态。
     */
    @Getter
    @Setter
    private boolean gatherStats;//if true, gather session stats
    /**
     * 标记use profiling状态。
     */
    @Getter
    @Setter
    private boolean useProfiling;//if true, set profiling as 1
    /**
     * 保存sql text信息。
     */
    @Getter
    private String sqlText;//query to be profiled, for now, we only support SELECT
    /**
     * 保存db user信息。
     */
    @Getter
    @Setter
    private String dbUser;
    /**
     * 保存connection time信息。
     */
    private long connectionTime = 0L; //time used for connection

    /**
     * 保存connection信息。
     */
    private transient Connection connection;

    /**
     * 设置sql text。
     *
     * @param sqlText sql text参数。
     */
    public void setSqlText(String sqlText) throws UnsafeQueryException {
        checkQuerySafety(sqlText);
        this.sqlText = sqlText;
    }

    /**
     * 检查query safety。
     *
     * @param sqlText sql text参数。
     */
    private void checkQuerySafety(String sqlText) throws UnsafeQueryException {

        if (sqlText == null || sqlText.isEmpty())
            throw new UnsafeQueryException("No query text provided");

        String s = sqlText.trim();
        //remove any comments
        while (s.startsWith("/*")) {
            int idx = s.indexOf("*/");

            if (idx < 0)
                break;

            s = s.substring(idx + 2);
            s = s.trim();
        }

        //remove --
        while (s.startsWith("--")) {
            int idx = s.indexOf("\n");

            if (idx < 0)
                idx = s.indexOf("\r");

            if (idx < 0)
                break;

            s = s.substring(idx + 1);
            s = s.trim();
        }

        if (s.isEmpty())
            throw new UnsafeQueryException("No valid query text provided");

        if (!s.toLowerCase().startsWith("select") && !s.toLowerCase().startsWith("(select"))
            throw new UnsafeQueryException("Only SELECT statement is supported at this moment");
    }

    /**
     * 执行destroy操作。
     */
    public void destroy() {

        if (this.connection != null)
            DBUtils.close(connection);

        this.connection = null;
    }

    /**
     * We don't want to store username and passwword
     *
     * @param dbuser
     * @param dbpwd
     */
    public void connect(String dbuser, String dbpwd, boolean readOnly) throws SQLException {

        if (connection != null && !this.dbUser.equalsIgnoreCase(dbuser))
            DBUtils.close(connection);

        this.dbUser = dbuser;
        //TODO test connection
        Properties prop = new Properties();
        prop.put("socketTimeout", "600000");//make it 10 minutes. 1 minute is too short for profiling
        prop.put("interactiveClient", "true");
        long connStartTime = System.currentTimeMillis();
        connection = ConnectionFactory.connect(dbinfo, dbuser, dbpwd, frameworkContext, prop);
        long connEndTime = System.currentTimeMillis();
        this.connectionTime = connEndTime - connStartTime;

        if (readOnly)
            connection.setReadOnly(true);
    }

    /**
     * 运行explain plan。
     *
     * @return 处理结果。
     */
    public ResultList runExplainPlan() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            ResultList rList;
            try (ResultSet rs = stmt.executeQuery("EXPLAIN " + this.sqlText)) {
                rList = ResultListUtil.fromSqlResultSet(rs, 1000);
            }

            try (ResultSet rs = stmt.executeQuery("show status like 'Last_query_cost'")) {

                if (rs != null && rs.next()) {
                    String cost = rs.getString("Value");

                    if (cost != null) {
                        //append as last row
                        ResultRow row = new ResultRow();
                        int cols = rList.getColumnDescriptor().getColumns().size();

                        for (int i = 0; i < cols - 1; i++)
                            row.addColumn("");

                        row.addColumn("Cost: " + cost);
                        rList.addRow(row);
                    }
                }

                return rList;
            }
        }
    }

    /**
     * TODO check version
     *
     * @return
     * @throws SQLException
     */
    public String runExplainPlanJson() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN FORMAT=JSON " + this.sqlText)) {

            if (rs != null && rs.next())
                return rs.getString(1);
        }

        return null;
    }

    /**
     * 运行profile。
     *
     * @return 处理结果。
     */
    public ResultList runProfile() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("set profiling=1");
            try (ResultSet rs = stmt.executeQuery(this.sqlText)) {
                int count = 0;
                while (rs != null && rs.next()) {
                    count++;
                }
            }

            try (ResultSet rs = stmt.executeQuery("show profile all")) {
                ResultList rList = ResultListUtil.fromSqlResultSet(rs, 1000);
                stmt.execute("set profiling=0");
                //reprocess and remove empty/all zero columns
                int[] bitmap = new int[rList.getColumnDescriptor().getColumns().size()];

                for (int i = 0; i < bitmap.length; i++)
                    bitmap[i] = 0;

                for (int i = 0; i < bitmap.length; i++)
                    for (ResultRow row : rList.getRows()) {
                        List<String> cols = row.getColumns();
                        String val = cols.get(i);

                        if (val != null && !val.isEmpty() && !"0".equals(val.trim())) {
                            bitmap[i] = 1;
                            break;
                        }
                    }


                ColumnDescriptor desc = new ColumnDescriptor();
                List<ColumnInfo> colInfos = rList.getColumnDescriptor().getColumns();
                int idx = 0;

                for (int i = 0; i < bitmap.length; i++)
                    if (bitmap[i] == 1)
                        desc.addColumn(colInfos.get(i).getName(), colInfos.get(i).isNumberType(), idx++);


                ResultList rlist2 = new ResultList();
                rlist2.setColumnDescriptor(desc);

                for (ResultRow row : rList.getRows()) {
                    ResultRow newRow = new ResultRow();
                    newRow.setColumnDescriptor(desc);
                    List<String> cols = row.getColumns();

                    for (int i = 0; i < bitmap.length; i++)
                        if (bitmap[i] == 1)
                            newRow.addColumn(cols.get(i));


                    rlist2.addRow(newRow);
                }

                return rlist2;
            }
        }
    }

    /**
     * 运行stats。
     *
     * @return 处理结果。
     */
    public ResultList runStats() throws SQLException {
        HashMap<String, BigDecimal> startStat = new HashMap<>();
        HashMap<String, BigDecimal> endStat = new HashMap<>();

        try (Statement stmt = connection.createStatement()) {
            stmt.setFetchSize(1000);
            //log query running for more than 0 millisconds
            stmt.execute("set long_query_time=0.00");

            try (ResultSet rs = stmt.executeQuery("show status")) {
                while (rs != null && rs.next()) {
                    String key = rs.getString(1);
                    String val = rs.getString(2);

                    if (val == null || val.isEmpty() || "0".equals(val))
                        continue;

                    try {
                        startStat.put(key.toUpperCase(), new BigDecimal(val));
                    } catch (Exception ex) {
                    }
                }
            }

            long execStart = System.currentTimeMillis();
            long execEnd;
            int count;
            long fetchEnd;

            try (ResultSet rs = stmt.executeQuery(this.sqlText)) {
                execEnd = System.currentTimeMillis();
                count = 0;

                while (rs != null && rs.next())
                    count++;

                fetchEnd = System.currentTimeMillis();
            }

            try (ResultSet rs = stmt.executeQuery("show status")) {
                while (rs != null && rs.next()) {
                    String key = rs.getString(1);
                    String val = rs.getString(2);

                    if (val == null || val.isEmpty() || "0".equals(val))
                        continue;

                    try {
                        endStat.put(key.toUpperCase(), new BigDecimal(val));
                    } catch (Exception ignored) {
                    }
                }
            }

            TreeMap<String, BigDecimal> res = new TreeMap<>();

            for (Map.Entry<String, BigDecimal> e : endStat.entrySet()) {
                String key = e.getKey();

                if (key.toLowerCase().startsWith("innodb") || key.toLowerCase().startsWith("uptime"))
                    //skip non session related innodb stats and uptime
                    continue;

                BigDecimal val = e.getValue();

                if (key.toLowerCase().startsWith("last_query")) {
                    //skip the negate
                } else if (startStat.containsKey(key)) {
                    //skip the one with no change
                    if (val.equals(startStat.get(key)))
                        continue;

                    val = val.add(startStat.get(key).negate());
                }

                res.put(key, val);
            }

            res.put("_RETURNED_ROWS", new BigDecimal(count));
            res.put("_TIME_EXEC_MS", new BigDecimal(execEnd - execStart));
            res.put("_TIME_FETCH_MS", new BigDecimal(fetchEnd - execEnd));
            res.put("_TIME_CONN_MS", new BigDecimal(this.connectionTime));
            ResultList rList = new ResultList();
            ColumnDescriptor desc = new ColumnDescriptor();
            desc.addColumn("VARIABLE_NAME", false, 1);
            desc.addColumn("VARIABLE_VALUE", true, 2);
            rList.setColumnDescriptor(desc);

            for (Map.Entry<String, BigDecimal> e : res.entrySet()) {
                ResultRow row = new ResultRow();
                row.setColumnDescriptor(desc);
                List<String> vals = new ArrayList<>(2);
                vals.add(e.getKey());
                vals.add(e.getValue().toPlainString());
                row.setColumns(vals);
                rList.addRow(row);
            }

            //TODO add row count and time
            return rList;
        }
    }
}
