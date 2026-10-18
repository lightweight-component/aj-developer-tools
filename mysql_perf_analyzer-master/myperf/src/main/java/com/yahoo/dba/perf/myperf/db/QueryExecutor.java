/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.db;

import com.yahoo.dba.perf.myperf.common.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 表示query executor相关的类型。
 */
@Slf4j
public class QueryExecutor {
    //Oracle specific
    /**
     * 定义awr snap id sql常量。
     */
    public static final String AWR_SNAP_ID_SQL = "select min(snap_id) min_snap_id, max(snap_id) max_snap_id from dba_hist_snapshot where begin_interval_time between TO_TIMESTAMP(?,'yyyy-mm-dd hh24:mi:ss') and TO_TIMESTAMP(?,'yyyy-mm-dd hh24:mi:ss')";
    /**
     * 定义awr db id sql常量。
     */
    public static final String AWR_DB_ID_SQL = "select dbid from dba_hist_snapshot where snap_id between ? and ?  and instance_number=? and rownum=1";
    /**
     * 定义awr db id sql 2常量。
     */
    public static final String AWR_DB_ID_SQL_2 = "select dbid from dba_hist_snapshot where instance_number=? and rownum=1";

    /**
     * 保存sql manager信息。
     */
    @Getter
    @Setter
    private SqlManager sqlManager;
    /**
     * 保存framework context信息。
     */
    @Getter
    @Setter
    private MyPerfContext frameworkContext;

    /**
     * 创建QueryExecutor对象。
     */
    public QueryExecutor() {
    }

    /**
     * No fancy replacement. Only place holder is p_nn. Used for MySQL, etc.
     *
     * @param qps      assume it has been validated
     * @param conn
     * @param maxCount
     * @return
     * @throws SQLException
     */
    public ResultList executeQueryGeneric(QueryParameters qps, DBConnectionWrapper conn, int maxCount) throws SQLException {
        String actualSql = null;
        long startTime = System.currentTimeMillis();
        long execTime = startTime;
        try {

            if (sqlManager == null)
                log.error("No sqlManager specified");//code error

            if (qps == null)
                log.error("No QPS specified");//code error

            Sql sql = sqlManager.getSql(qps.getSql());

            if (sql == null)
                log.error("No SQL found for handle " + qps.getSql());//code error

            String sqlText = null;

            if (sql.getQueryClass() != null)
                sqlText = DynamicQueryFactory.getQuery(sql.getQueryClass(), conn, false);

            if (sqlText == null)
                sqlText = sql.getSqlText(conn.getVersion());

            boolean usePreparedStatement = sql != null && sql.getParamCount() > 0 && !sql.isReplace();
            actualSql = sqlText;

            if (sql != null && sql.getParamCount() > 0)
                for (int i = 1; i <= sql.getParamCount(); i++)
                    actualSql = actualSql.replace("&p_" + i, usePreparedStatement
                            ? "?" : escapeSingleQuote(sql.getSqlParameter(i - 1), qps.getSqlParams().get("p_" + i)));


            try (Statement stmt = usePreparedStatement
                    ? conn.getConnection().prepareStatement(actualSql)
                    : conn.getConnection().createStatement()) {
                stmt.setQueryTimeout(this.frameworkContext.getQueryTimeout());
                stmt.setFetchSize(maxCount > 0 && maxCount < this.frameworkContext.getQueryFetchSize() ? maxCount : this.frameworkContext.getQueryFetchSize());
                conn.setCurrentStatement(stmt);

                if (usePreparedStatement) {
                    PreparedStatement pstmt = (PreparedStatement) stmt;

                    for (int i = 1; i <= sql.getParamCount(); i++)
                        pstmt.setString(i, qps.getSqlParams().get("p_" + i));

                }

                try (ResultSet rs = usePreparedStatement
                        ? ((PreparedStatement) stmt).executeQuery() : stmt.executeQuery(actualSql)) {
                    execTime = System.currentTimeMillis();
                    ResultList rList = ResultListUtil.flatSqlResultSet(sqlManager.getSql(qps.getSql()), rs, maxCount >= 0 ? maxCount : 1000);//TODO, the maximum count
                    long endTime = System.currentTimeMillis();

                    if (rList != null) {
                        rList.setTotalResponseTime(endTime - startTime);
                        rList.setTotalExecutionTime(execTime - startTime);
                        rList.setTotalFetchTime(endTime - execTime);
                    }

                    return rList;
                }
            }
        } catch (SQLException sqlEx) {
            log.info("Failed to execute " + actualSql);
            throw sqlEx;
        } finally {
            conn.setCurrentStatement(null);
        }
    }


    /**
     * 执行query with multi row metrics collapsed。
     *
     * @param qps  qps参数。
     * @param conn conn参数。
     * @param buf  buf参数。
     * @return 处理结果。
     */
    public Map<String, String> executeQueryWithMultiRowMetricsCollapsed(QueryParameters qps, DBConnectionWrapper conn, MetricsBuffer buf) throws SQLException {
        String actualSql = null;
        long startTimestamp = System.currentTimeMillis();
        try {

            if (sqlManager == null)
                log.error("No sqlManager specified");

            if (qps == null)
                log.error("No QPS specified");

            Sql sql = sqlManager.getSql(qps.getSql());
            String sqlText = null;

            if (sql.getQueryClass() != null)
                sqlText = DynamicQueryFactory.getQuery(sql.getQueryClass(), conn, false);

            if (sqlText == null)
                sqlText = sql.getSqlText(conn.getVersion());

            boolean usePreparedStatement = sql != null && sql.getParamCount() > 0 && !sql.isReplace();
            actualSql = sqlText;

            if (sql != null && sql.getParamCount() > 0)
                for (int i = 1; i <= sql.getParamCount(); i++)
                    actualSql = actualSql.replace("&p_" + i, usePreparedStatement
                            ? "?" : escapeSingleQuote(sql.getSqlParameter(i - 1), qps.getSqlParams().get("p_" + i)));


            try (Statement stmt = usePreparedStatement
                    ? conn.getConnection().prepareStatement(actualSql)
                    : conn.getConnection().createStatement()) {
                stmt.setQueryTimeout(this.frameworkContext.getQueryTimeout());
                conn.setCurrentStatement(stmt);

                if (usePreparedStatement) {
                    PreparedStatement pstmt = (PreparedStatement) stmt;

                    for (int i = 1; i <= sql.getParamCount(); i++)
                        pstmt.setString(i, qps.getSqlParams().get("p_" + i));

                }

                try (ResultSet rs = usePreparedStatement
                        ? ((PreparedStatement) stmt).executeQuery() : stmt.executeQuery(actualSql)) {
                    //long endTimestamp = System.currentTimeMillis();
                    //return buf.recordOneRowBymetricsName2(rs, startTimestamp, (int)(endTimestamp - startTimestamp));
                    Map<String, String> metrics = new HashMap<>();
                    while (rs != null && rs.next()) {
                        metrics.put(rs.getString(buf.getMetrics().getMetricNameColumn()).toLowerCase(), rs.getString(buf.getMetrics().getMetricValueColumn()));
                    }
                    return metrics;
                }
            }
        } catch (SQLException sqlEx) {
            log.info("Failed to execute " + actualSql);
            throw sqlEx;
        } finally {
            conn.setCurrentStatement(null);
        }
    }

    /**
     * Retrieve key value pairs data from DB
     *
     * @param qps
     * @param conn
     * @param keyColumn
     * @param valueColumn
     * @param normalizeKey if true, key will be convert to uppercase
     * @return
     * @throws SQLException
     */
    public Map<String, String> executeQueryWithKeyValuPairs(QueryParameters qps, DBConnectionWrapper conn, String keyColumn, String valueColumn, boolean normalizeKey) throws SQLException {
        String actualSql = null;
        //long startTimestamp = System.currentTimeMillis();
        try {

            if (sqlManager == null)
                log.error("No sqlManager specified");

            if (qps == null)
                log.error("No QPS specified");

            String sqlText = qps.getSqlText(); //if not predefined
            Sql sql = null;

            if (sqlText == null || sqlText.isEmpty())
                sql = sqlManager.getSql(qps.getSql());

            if (sql != null && sql.getQueryClass() != null)
                sqlText = DynamicQueryFactory.getQuery(sql.getQueryClass(), conn, false);

            if (sqlText == null && sql != null)
                sqlText = sql.getSqlText(conn.getVersion());

            boolean usePreparedStatement = sql != null && sql.getParamCount() > 0 && !sql.isReplace();
            actualSql = sqlText;

            if (sql != null && sql.getParamCount() > 0)
                for (int i = 1; i <= sql.getParamCount(); i++)
                    actualSql = actualSql.replace("&p_" + i, usePreparedStatement
                            ? "?" : escapeSingleQuote(sql.getSqlParameter(i - 1), qps.getSqlParams().get("p_" + i)));


            try (Statement stmt = usePreparedStatement
                    ? conn.getConnection().prepareStatement(actualSql)
                    : conn.getConnection().createStatement()) {
                stmt.setQueryTimeout(this.frameworkContext.getQueryTimeout());
                conn.setCurrentStatement(stmt);

                if (usePreparedStatement) {
                    PreparedStatement pstmt = (PreparedStatement) stmt;

                    for (int i = 1; i <= sql.getParamCount(); i++)
                        pstmt.setString(i, qps.getSqlParams().get("p_" + i));

                }

                try (ResultSet rs = usePreparedStatement
                        ? ((PreparedStatement) stmt).executeQuery() : stmt.executeQuery(actualSql)) {
                    Map<String, String> metrics = new HashMap<>();

                    if (keyColumn != null && !keyColumn.isEmpty() && valueColumn != null && !valueColumn.isEmpty())
                        while (rs != null && rs.next()) {
                            String key = rs.getString(keyColumn.toLowerCase());

                            if (normalizeKey)
                                key = key.toUpperCase();

                            metrics.put(key, rs.getString(valueColumn.toLowerCase()));
                        }
                    else if (rs != null) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int cnt = meta.getColumnCount();

                        if (rs.next())
                            for (int i = 1; i <= cnt; i++)
                                metrics.put(meta.getColumnLabel(i).toLowerCase(), rs.getString(i));

                    }

                    //log.info("Retrieved: "+metrics.size()+", sql: "+actualSql);
                    return metrics;
                }
            }
        } catch (SQLException sqlEx) {
            log.info("Failed to execute: " + qps.getSql() + ": " + actualSql);
            throw sqlEx;
        } finally {
            conn.setCurrentStatement(null);
        }
    }

    /**
     * For some cases like user stats, table stats, metrics are per user or table name based.
     * The keyColumn is used to retrieve the user or table name.,
     * and other columns should be the metrics
     *
     * @param qps
     * @param conn
     * @param keyColumn
     * @return
     * @throws SQLException
     */
    public Map<String, Map<String, String>> executeQueryWithMultipleColumns(QueryParameters qps, DBConnectionWrapper conn, String keyColumn) throws SQLException {
        String actualSql = null;
        //long startTimestamp = System.currentTimeMillis();
        try {

            if (sqlManager == null)
                log.error("No sqlManager specified");

            if (qps == null)
                log.error("No QPS specified");

            String sqlText = qps.getSqlText(); //if not predefined
            Sql sql = null;

            if (sqlText == null || sqlText.isEmpty())
                sql = sqlManager.getSql(qps.getSql());

            if (sql != null && sql.getQueryClass() != null)
                sqlText = DynamicQueryFactory.getQuery(sql.getQueryClass(), conn, false);

            if (sqlText == null && sql != null)
                sqlText = sql.getSqlText(conn.getVersion());

            boolean usePreparedStatement = sql != null && sql.getParamCount() > 0 && !sql.isReplace();
            actualSql = sqlText;

            if (sql != null && sql.getParamCount() > 0)
                for (int i = 1; i <= sql.getParamCount(); i++)
                    actualSql = actualSql.replace("&p_" + i, usePreparedStatement
                            ? "?" : escapeSingleQuote(sql.getSqlParameter(i - 1), qps.getSqlParams().get("p_" + i)));


            try (Statement stmt = usePreparedStatement
                    ? conn.getConnection().prepareStatement(actualSql)
                    : conn.getConnection().createStatement()) {
                stmt.setQueryTimeout(this.frameworkContext.getQueryTimeout());
                conn.setCurrentStatement(stmt);

                if (usePreparedStatement) {
                    PreparedStatement pstmt = (PreparedStatement) stmt;

                    for (int i = 1; i <= sql.getParamCount(); i++)
                        pstmt.setString(i, qps.getSqlParams().get("p_" + i));

                }

                try (ResultSet rs = usePreparedStatement
                        ? ((PreparedStatement) stmt).executeQuery() : stmt.executeQuery(actualSql)) {
                    Map<String, Map<String, String>> metrics = new HashMap<>();
                    int colCnt = 0;

                    if (rs != null)
                        colCnt = rs.getMetaData().getColumnCount();

                    while (rs != null && rs.next()) {
                        String key = rs.getString(keyColumn);
                        Map<String, String> metricsperKey = new HashMap<>(colCnt);

                        for (int i = 1; i <= colCnt; i++)
                            metricsperKey.put(rs.getMetaData().getColumnName(i), rs.getString(i));

                        metrics.put(key, metricsperKey);
                    }
                    return metrics;
                }
            }
        } catch (SQLException sqlEx) {
            log.info("Failed to execute " + actualSql);
            throw sqlEx;
        } finally {
            conn.setCurrentStatement(null);
        }
    }


    /**
     * Not a right place, but we put the method here anyway.
     * We only gather info for seconds_behind_master, salve_sql_running, salve_io_running
     *
     * @param conn
     * @return
     */
    public HashMap<String, String> showSlaveStatusMetrics(DBConnectionWrapper conn) {
        HashMap<String, String> resMap = new HashMap<>(3);
        boolean isSlave = false;
        int secondsBehindMaster = 0;
        int slaveIoRunning = 1;
        int slaveSqlRunning = 1;
        try (Statement stmt = conn.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("show slave status")) {
            while (rs != null && rs.next()) {
                int sec = rs.getInt("Seconds_Behind_Master");

                if (sec > secondsBehindMaster)
                    secondsBehindMaster = sec;

                if (!"Yes".equalsIgnoreCase(rs.getString("Slave_IO_Running")))
                    slaveIoRunning = 0;

                if (!"Yes".equalsIgnoreCase(rs.getString("Slave_SQL_Running")))
                    slaveSqlRunning = 0;

                isSlave = true;
            }
        } catch (Exception ex) {
        }
        if (isSlave) {
            resMap.put("Seconds_Behind_Master", String.valueOf(secondsBehindMaster));
            resMap.put("Slave_IO_Running", String.valueOf(slaveIoRunning));
            resMap.put("Slave_SQL_Running", String.valueOf(slaveSqlRunning));
        }

        return resMap;
    }

    /**
     * 执行query。
     *
     * @param qps      qps参数。
     * @param conn     conn参数。
     * @param maxCount max count参数。
     * @return 处理结果。
     */
    public ResultList executeQuery(QueryParameters qps, DBConnectionWrapper conn, int maxCount) throws SQLException {
        //validate the sql first
        //QueryInputValidator.validateSql(this.sqlManager, qps);
        String actualSql = null;
        try {

            if (qps == null)
                log.error("No QPS specified");

            if (sqlManager == null)
                log.error("No sqlManager specified");

            Sql sql = sqlManager.getSql(qps.getSql());
            String sqlText = null;

            if (sql.getQueryClass() != null)
                sqlText = DynamicQueryFactory.getQuery(sql.getQueryClass(), conn, false);

            if (sqlText == null)
                sqlText = sql.getSqlText(conn.getVersion());

            boolean usePreparedStatement = sql.getParamCount() > 0 && !sql.isReplace();
            actualSql = sqlText;

            if (sql.getParamCount() > 0)
                for (int i = 1; i <= sql.getParamCount(); i++)
                    actualSql = actualSql.replace("&p_" + i, usePreparedStatement
                            ? "?" : escapeSingleQuote(sql.getSqlParameter(i - 1), qps.getSqlParams().get("p_" + i)));

            try (Statement stmt = usePreparedStatement
                    ? conn.getConnection().prepareStatement(actualSql)
                    : conn.getConnection().createStatement()) {
                stmt.setQueryTimeout(this.frameworkContext.getQueryTimeout());
                stmt.setFetchSize(maxCount > 0 && maxCount < this.frameworkContext.getQueryFetchSize() ? maxCount : this.frameworkContext.getQueryFetchSize());
                conn.setCurrentStatement(stmt);

                if (usePreparedStatement) {
                    PreparedStatement pstmt = (PreparedStatement) stmt;

                    for (int i = 1; i <= sql.getParamCount(); i++)
                        pstmt.setString(i, qps.getSqlParams().get("p_" + i));
                }

                try (ResultSet rs = usePreparedStatement
                        ? ((PreparedStatement) stmt).executeQuery() : stmt.executeQuery(actualSql)) {
                    return ResultListUtil.flatSqlResultSet(sqlManager.getSql(qps.getSql()), rs, maxCount >= 0 ? maxCount : 1000);//TODO, the maximum count
                }
            }
        } catch (SQLException sqlEx) {
            log.info("Failed to execute " + actualSql);
            throw sqlEx;
        } finally {
            conn.setCurrentStatement(null);
        }
    }

    /**
     * 执行query。
     *
     * @param conn     conn参数。
     * @param sql      sql参数。
     * @param params   params参数。
     * @param maxCount max count参数。
     * @return 处理结果。
     */
    public ResultList executeQuery(DBConnectionWrapper conn, String sql, Map<String, String> params, int maxCount) throws SQLException {
        String actualSql = null;
        try {
            String sqlText = sql;

            if (params != null && params.size() > 0) {
                actualSql = sqlText;

                for (Map.Entry<String, String> param : params.entrySet()) {
                    //note our parameters are prefixed with &
                    actualSql = actualSql.replace("&" + param.getKey(), SqlParameter.escapeSingleQuote(param.getValue()));
                }

            } else
                actualSql = sqlText;

            try (Statement stmt = conn.getConnection().createStatement()) {
                stmt.setQueryTimeout(this.frameworkContext.getQueryTimeout());
                stmt.setFetchSize(maxCount > 0 && maxCount < this.frameworkContext.getQueryFetchSize() ? maxCount : this.frameworkContext.getQueryFetchSize());
                conn.setCurrentStatement(stmt);
                try (ResultSet rs = stmt.executeQuery(actualSql)) {
                    return ResultListUtil.fromSqlResultSet(rs, maxCount >= 0 ? maxCount : 1000);
                }
            }
        } catch (SQLException sqlEx) {
            log.info("Failed to execute " + actualSql);
            throw sqlEx;
        } finally {
            conn.setCurrentStatement(null);
        }
    }

    /**
     * 执行escape single quote操作。
     *
     * @param param param参数。
     * @param str   str参数。
     * @return 处理结果。
     */
    private static String escapeSingleQuote(SqlParameter param, String str) {

        if (str == null)
            return null;

        if (param != null) {
            boolean valid = param.isValidValue(str);

            if (valid)
                return str;//nothing to espace

            if (param.needEscape())
                return SqlParameter.escapeSingleQuote(str);

        }

        return null;//otherwise count invalid
		/*
		StringBuilder sb = new StringBuilder();
		char[] chs = str.toCharArray();
		for(int i=0;i<chs.length;i++)
		{
			if(chs[i]=='\'')
				sb.append("''");
			else sb.append(chs[i]);
		}
		return sb.toString();
		*/
    }
}
