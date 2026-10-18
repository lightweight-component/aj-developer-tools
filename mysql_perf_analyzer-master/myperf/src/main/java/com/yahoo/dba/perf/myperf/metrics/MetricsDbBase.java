/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.metrics;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import com.yahoo.dba.perf.myperf.common.*;
import com.yahoo.dba.perf.myperf.process.MetricsRetentionTask;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;


/**
 * persistence store for metrics
 *
 * @author xrao
 */
@Slf4j
public abstract class MetricsDbBase implements Runnable {

    //wrap the metrics data for future extenstion
    static class MetricsData {
        String dataKey;//can be null
        ByteBuffer data;

        MetricsData(String dataKey, ByteBuffer data) {
            this.dataKey = dataKey;
            this.data = data;
        }
    }

    /**
    * Access other system info
    *
    * @return
    */
    @Getter
    @Setter
    private MyPerfContext frameworkContext; //system context

    //database access information
    /**
    * JDBC connection string
    *
    * @return
    */
    @Getter
    @Setter
    private String connectionString = "jdbc:derby:metricsdb";//JDBC URL
    /**
    * Database user name
    *
    * @return
    */
    @Getter
    @Setter
    private String username;
    /**
    * Database password
    *
    * @return
    */
    @Getter
    @Setter
    private String password = "metricsdb";
    @Getter
    @Setter
    private String schemaName = "METRICSDB";//default to METRICSDB
    //move db info to metrics db
    protected static final String DBINFO_TABLENAME = "DBINFOS";
    protected static final String ALERTSETTING_TABLENAME = "ALERTSETTINGS";
    protected static final String ALERT_SUBSCRIPT = "ALERT_SUBSCRIPT";
    protected static final String ALERT_NOTIFICATION = "ALERT_NOTIFICATION";

    protected static final String METRICS_SUBSCRIPT = "METRICS_SUBSCRIPT";

    //data to store
    protected Map<String, ArrayBlockingQueue<MetricsData>> dataQueues = new HashMap<>();
    protected LinkedBlockingQueue<Integer> flushQueue = new LinkedBlockingQueue<>();//job controller
    @Getter
    @Setter
    protected volatile boolean stopped = false;


    protected Map<String, String> insertSQL = new HashMap<>();

    //the key will be dbtype+"_"+metric name
    //metrics definition
    @Getter
    protected Map<String, MetricsGroup> metricsGroups = new HashMap<>();
    protected Object metricsDefLock = new Object();
    //generic metric code
    private ConcurrentHashMap<String, Integer> metricCodeMap = new ConcurrentHashMap<>();


    protected DecimalFormat df = new DecimalFormat("#.###");
    protected SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    private transient Connection storeConnection = null;
    private long lastConnTime = -1L;

    private Object codeLock = new Object();//sync new code

    /**
     * To identify metric db server type, for example, javadb (derby) or mysql
     *
     * @return
     */
    abstract public String getDbType();

    /**
     * This is used during startup
     *
     * @param mgs
     */
    public void setMetricsGroups(MetricsDefManager metricsDef) {
        synchronized (metricsDefLock) {
            String[] groupNames = metricsDef.getGroupNames();
            for (String grpName : groupNames) {
                MetricsGroup group = metricsDef.getGroupByName(grpName);
                if (group == null) continue; //not supposed to be so
                List<MetricsGroup> mgs = new ArrayList<>();
                if (group.getSubGroups().size() == 0) //no sub group, add self
                {
                    mgs.add(group);
                } else {
                    for (MetricsGroup g : group.getSubGroups())
                        mgs.add(g);
                }
                for (MetricsGroup g : mgs) {
                    String sinkName = g.getSinkTableName();
                    this.metricsGroups.put(sinkName, g);
                    this.dataQueues.put(sinkName, new ArrayBlockingQueue<>(10000));//2014-02-14, change size to 10K
                    if (!g.isStoreInCommonTable())
                        this.insertSQL.put(sinkName, this.insertSQL(g));
                    else {
                        String targetTable = g.getTargetTable();
                        if (targetTable == null || targetTable.isEmpty())
                            targetTable = "METRIC_GENERIC";
                        this.insertSQL.put((g.getDbType() + "_" + g.getGroupName()), "INSERT INTO " + targetTable + " (DBID, METRIC_ID, SNAP_ID, TS, VALUE) VALUES(?,?,?,?,?)");
                    }

                }
            }

            for (Map.Entry<String, UserDefinedMetrics> entry : metricsDef.getUdmManager().getUdms().entrySet()) {
                MetricsGroup group = entry.getValue().getMetricsGroup();
                if (group == null) continue; //not supposed to be so
                String sinkName = group.getSinkTableName();
                this.metricsGroups.put(sinkName, group);
                this.dataQueues.put(sinkName, new ArrayBlockingQueue<>(10000));//2014-02-14, change size to 10K
                this.insertSQL.put(sinkName, this.insertSQL(group));
            }
        }
    }


    public boolean addNewUDM(UserDefinedMetrics udm) {
        if (udm == null) return false;
        MetricsGroup mg = udm.getMetricsGroup();
        if (!createMetricsTable(mg)) return false;
        String tblName = mg.getSinkTableName();
        synchronized (metricsDefLock) {
            if (!this.metricsGroups.containsKey(tblName)) {
                this.metricsGroups.put(tblName, mg);
                this.dataQueues.put(tblName,
                        new ArrayBlockingQueue<>(10000));
                this.insertSQL.put(tblName, this.insertSQL(mg));
            } else
                return false;
        }
        return true;
    }

    public boolean removeUDM(UserDefinedMetrics udm) {
        if (udm == null) return false;
        MetricsGroup mg = udm.getMetricsGroup();
        String tblName = mg.getSinkTableName();
        synchronized (metricsDefLock) {
            if (this.metricsGroups.containsKey(tblName)) {
                this.metricsGroups.remove(tblName);
                this.dataQueues.remove(tblName);
                this.insertSQL.remove(tblName);
            }
        }
        return true;
    }

    /**
     * Build insert statement.
     * TODO db specific optimization
     *
     * @param mg
     * @return
     */
    private String insertSQL(MetricsGroup mg) {
        StringBuilder sb = new StringBuilder();
        sb.append("insert ignore into ");
        if (mg.isUdmFlagged())
            sb.append("UDM");
        else
            sb.append(mg.getDbType().toUpperCase());
        sb.append("_")
                .append(mg.getGroupName().toUpperCase()).append(" (")
                .append("DBID, SNAP_ID");
        if (mg.getKeyColumn() != null && !mg.getKeyColumn().isEmpty()) {
            //add additional column to store key
            sb.append(", KEY_COLUMN");
        }
        sb.append(", TS, SQL_TIME");
        for (Metric m : mg.getMetrics()) {
            sb.append(",");
            sb.append(m.getName().toUpperCase());
        }
        sb.append(") values (?,?,?,?");
        if (mg.getKeyColumn() != null && !mg.getKeyColumn().isEmpty()) {
            //add additional column to store key
            sb.append(",?");
        }
        for (Metric m : mg.getMetrics()) {
            sb.append(",?");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Initial method. It will create required table, if not exists, and load metric code and start stop script.
     * TODO use multiple threads in the future
     */
    public void init() {
        //TODO read configuration
        log.info("Init metrics db");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        createTables();
        loadMetricCode();
        new Thread(this).start();
        log.info("Init metrics done");
    }

    /**
     * Just set thread to stop
     */
    public void destroy() {
        log.info("Shutting donw metrics DB");
        this.stopped = true;
    }

    //derbydb does not support limit
    abstract protected boolean isLimitSupport();

    abstract protected String[] buildHostDDL();

    abstract protected String[] buildAlertSettingDDL();

    abstract protected String buildMetricCodeDDL();

    abstract protected String buildMetricCodeIndexDDL();

    abstract protected String[] buildGenericMetricDDL();

    abstract protected String buildAlertDDL();

    abstract protected String buildAlertIndexDDL();

    abstract protected String[] buildSnapshotDDL();//create metrics snapshot table

    abstract protected String[] buildAlertSubScriptionDDL();//create alert subscription table

    abstract protected String[] buildMetricsSubscrptionDDL();//create metrics subscription table for UDM and on demand metrics

    abstract protected String[] buildAlertNotificationDDL();//create alert notification table

    protected String buildDDL(MetricsGroup mg) {
        StringBuilder sb = new StringBuilder();
        String tblName = mg.getSinkTableName();
        sb.append("CREATE TABLE ");
        sb.append(tblName);
        sb.append(" (")
                .append("DBID INT, SNAP_ID INT");
        if (mg.getKeyColumn() != null && !mg.getKeyColumn().isEmpty()) {
            //add additional column to store key
            sb.append(", KEY_COLUMN VARCHAR(255)");
        }
        sb.append(", TS BIGINT, SQL_TIME INT");
        for (Metric m : mg.getMetrics()) {
            sb.append(",");
            sb.append(m.getName().toUpperCase()).append(" ");
            if (m.getDataType() == MetricDataType.BYTE)
                sb.append("TINYINT");
            else if (m.getDataType() == MetricDataType.SHORT)
                sb.append("SMALLINT");
            else if (m.getDataType() == MetricDataType.INT)
                sb.append("INT");
            else if (m.getDataType() == MetricDataType.LONG)
                sb.append("BIGINT");
            else if (m.getDataType() == MetricDataType.FLOAT)
                sb.append("DECIMAL(22,7)");
            else if (m.getDataType() == MetricDataType.DOUBLE)
                sb.append("DECIMAL(22,7)");
        }
        if (mg.getKeyColumn() != null && !mg.getKeyColumn().isEmpty())
            sb.append(", PRIMARY KEY(DBID, SNAP_ID, KEY_COLUMN))");
        else
            sb.append(", PRIMARY KEY(DBID, SNAP_ID))");
        return sb.toString();
    }

    private boolean createMetricsTable(MetricsGroup mg) {
        try (Connection conn = this.createConnection(false)) {
            createMetricsTable(mg, conn);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to create table for metrics " + mg.getGroupName(), ex);
        }
        return false;
    }

    private void createMetricsTable(MetricsGroup mg, Connection conn) throws SQLException {
        String tblName = mg.getSinkTableName();
        if (!DBUtils.hasTable(conn, schemaName, tblName)) {
            try (Statement stmt = conn.createStatement()) {
                String ddl = this.buildDDL(mg);
                log.info("Create metric table " + tblName + ": " + ddl);
                stmt.execute(ddl);
            }
            log.info("Created metric table " + tblName);
        }
    }

    private void createTables() {
        try (Connection conn = this.createConnection(false)) {
            for (Map.Entry<String, MetricsGroup> e : this.metricsGroups.entrySet()) {
                if (e.getValue().isStoreInCommonTable())
                    //skip it. It will use generic table
                    continue;
                createMetricsTable(e.getValue(), conn);
            }
            if (!DBUtils.hasTable(conn, schemaName, "METRIC_CODE")) {
                try (Statement stmt = conn.createStatement()) {
                    String ddl = this.buildMetricCodeDDL();
                    log.info("Create metric table METRIC_CODE: " + ddl);
                    stmt.execute(ddl);
                    stmt.execute(this.buildMetricCodeIndexDDL());
                }
                log.info("Created metric table METRIC_CODE");
            }
            if (!DBUtils.hasTable(conn, schemaName, "METRIC_GENERIC")) {
                try (Statement stmt = conn.createStatement()) {
                    String[] ddls = this.buildGenericMetricDDL();
                    for (String ddl : ddls) {
                        log.info("Create metric table METRIC_GENERIC: " + ddl);
                        stmt.execute(ddl);
                    }
                }
                log.info("Created metric table METRIC_GENERIC");
            }
            if (!DBUtils.hasTable(conn, schemaName, "SNAPSHOTS")) {
                try (Statement stmt = conn.createStatement()) {
                    String[] ddls = this.buildSnapshotDDL();
                    for (String ddl : ddls) {
                        log.info("Create metric table SNAPSHOTS: " + ddl);
                        stmt.execute(ddl);
                    }
                }
                log.info("Created metric table METRIC_GENERIC");
            }
            if (!DBUtils.hasTable(conn, schemaName, "ALERT")) {
                try (Statement stmt = conn.createStatement()) {
                    String ddl = this.buildAlertDDL();
                    log.info("Create alert table ALERT: " + ddl);
                    stmt.execute(ddl);
                    stmt.execute(this.buildAlertIndexDDL());
                }
                log.info("Created alert table ALERT");
            }
            if (!DBUtils.hasTable(conn, schemaName, "ALERT_SUBSCRIPT")) {
                try (Statement stmt = conn.createStatement()) {
                    String[] ddls = this.buildAlertSubScriptionDDL();
                    for (String ddl : ddls) {
                        log.info("Create metric table ALERT_SUBSCRIPT: " + ddl);
                        stmt.execute(ddl);
                    }
                }
                log.info("Created metric table ALERT_SUBSCRIPT");
            }
            if (!DBUtils.hasTable(conn, schemaName, "METRICS_SUBSCRIPT")) {
                try (Statement stmt = conn.createStatement()) {
                    String[] ddls = this.buildMetricsSubscrptionDDL();
                    for (String ddl : ddls) {
                        log.info("Create metric table METRICS_SUBSCRIPT: " + ddl);
                        stmt.execute(ddl);
                    }
                }
                log.info("Created metric table METRICS_SUBSCRIPT");
            }
            //buildAlertNotificationDDL
            if (!DBUtils.hasTable(conn, schemaName, "ALERT_NOTIFICATION")) {
                try (Statement stmt = conn.createStatement()) {
                    String[] ddls = this.buildAlertNotificationDDL();
                    for (String ddl : ddls) {
                        log.info("Create metric table ALERT_NOTIFICATION: " + ddl);
                        stmt.execute(ddl);
                    }
                }
                log.info("Created metric table ALERT_NOTIFICATION");
            }
            if (!DBUtils.hasTable(conn, schemaName, DBINFO_TABLENAME)) {
                try (Statement stmt = conn.createStatement()) {
                    for (String ddl : this.buildHostDDL()) {
                        if (ddl != null && !ddl.isEmpty()) {
                            log.info("Create dbhost table:  " + ddl);
                            stmt.execute(ddl);
                            log.info("Created dbhost table " + DBINFO_TABLENAME);
                        }
                    }
                }
            }
            if (!DBUtils.hasTable(conn, schemaName, ALERTSETTING_TABLENAME)) {
                try (Statement stmt = conn.createStatement()) {
                    for (String ddl : this.buildAlertSettingDDL()) {
                        if (ddl != null && !ddl.isEmpty()) {
                            log.info("Create table:  " + ddl);
                            stmt.execute(ddl);
                            log.info("Created table " + ALERTSETTING_TABLENAME);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to check or create metric db", ex);
        }
    }

    /**
     * This can be used at server start time to sync with meta db
     *
     * @param dbInfoManager
     */
    public void syncHosts(List<DBInstanceInfo> dbInfos) {
        if (dbInfos == null || dbInfos.size() == 0) return;//nothing to sync
        MetaHostSyncer syncer = new MetaHostSyncer();
        syncer.setDbList(dbInfos);
        new Thread(syncer).start();//let it run on itself
    }

    /**
     * This can be used to sync with actions on individual db
     *
     * @param dbInfo
     * @param action: new, delete, update
     * @return
     */
    public boolean syncHosts(DBInstanceInfo dbInfo, String action) {
        if (dbInfo == null)
            return false;
        try (Connection conn = this.createConnection(true)) {
            if ("delete".equalsIgnoreCase(action)) {
                if (dbInfo.getHostName() == null || "all".equalsIgnoreCase(dbInfo.getHostName()))
                    //remove whole group
                    return this.removeDbGroup(conn, dbInfo.getDbGroupName());
                else
                    return this.removeDBInfo(conn, dbInfo.getDbGroupName(), dbInfo.getHostName());
            } else
                return this.upsertDBInfo(conn, dbInfo, "new".equals(action));
        } catch (Exception ex) {
            log.info("Failed to remove host from metrics db (" + dbInfo.getDbGroupName() + ", " + dbInfo.getHostName());
        }
        return false;
    }

    /**
     * DB specific to create connection
     *
     * @param autocommit If true, the connection use autocommit, otherwise manual.
     * @return
     */
    abstract protected Connection createConnection(boolean autocommit);


    public void flush() {
        this.flushQueue.add(new Integer(1));
    }

    public void putData(MetricsGroup mg, String key, ByteBuffer buf) {
        String tblName = mg.getSinkTableName();
        ArrayBlockingQueue<MetricsData> q = this.dataQueues.get(tblName);
        if (q != null) {
            try {
                q.put(new MetricsData(key, buf));
            } catch (Exception ex) {
            }
        } else {
            log.info("Warning: cannot find sink queue " + mg.getGroupName() + ", table " + tblName);
        }
    }

    public void run() {
        Thread.currentThread().setName("MetricsDB");
        while (!stopped) {
            try {
                //start store either by trigger or timeout
                //2013-12-02: change from 60 seconds to 1sec
                //2014-02-13: change from 1 seconds to 100 msec
                Integer i = this.flushQueue.poll(100, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {

            }
            store();
        }
        //final store after shutdown
        store();
        DBUtils.close(storeConnection);
        storeConnection = null;
        log.info("metrics db stopped");
    }

    protected void store() {
        try {
            for (String s : this.dataQueues.keySet()) {
                ArrayBlockingQueue<MetricsData> q = this.dataQueues.get(s);
                List<MetricsData> q2 = new ArrayList<>(50);
                while (true) {
                    MetricsData bufData = q.poll();
                    if (//if nothing there, go to next queue.
                    bufData == null)
                        break;
                    //accumulate all data from the same queue
                    q2.add(bufData);
                }
                if (q2.size() > 0) {
                    log.debug("Store " + q2.size() + " " + s + " metric records.");
                    long currTime = System.currentTimeMillis();
                    if (currTime - this.lastConnTime > 30000) {
                        DBUtils.close(storeConnection);
                        storeConnection = null;
                    }
                    if (storeConnection == null) {
                        storeConnection = this.createConnection(true);
                        this.lastConnTime = currTime;
                    }
                    //generic metrics
                    MetricsGroup mg = this.metricsGroups.get(s);
                    if (mg.isStoreInCommonTable()) {
                        storeGenericMetric(s, q2, storeConnection);
                        this.lastConnTime = System.currentTimeMillis();
                        log.debug("Stored " + q2.size() + " " + s + " metric records.");
                        //go to next type
                        continue;
                    }
                    //builtin metrics
                    try {
                        String sql = this.insertSQL.get(s);
                        //for(MetricsData mdata:q2)
                        try (PreparedStatement stmt = storeConnection.prepareStatement(sql)) {
                            for (MetricsData mdata : q2) {
                                ByteBuffer buf = mdata.data;
                                int idx = 1;
                                int pos = 0;
                                stmt.setInt(idx++, buf.getInt(pos));
                                //dbid
                                pos += 4;
                                stmt.setInt(idx++, buf.getInt(pos));
                                //snap_id
                                pos += 4;
                                if (mg.getKeyColumn() != null && !mg.getKeyColumn().isEmpty()) {
                                    if (mdata.dataKey == null)
                                        stmt.setNull(idx++, Types.VARCHAR);
                                    else
                                        stmt.setString(idx++, mdata.dataKey);
                                }
                                stmt.setString(idx++, sdf.format(new Date(buf.getLong(pos))));
                                //timestamp
                                pos += 8;
                                stmt.setInt(idx++, buf.getInt(pos));
                                //sql time
                                pos += 4;
                                List<Metric> ms = mg.getMetrics();
                                int len = ms.size();
                                for (int i = 0; i < len; i++) {
                                    Metric m = ms.get(i);
                                    if (m.getDataType() == MetricDataType.BYTE) {
                                        stmt.setInt(idx++, buf.get(pos));
                                        pos++;
                                    } else if (m.getDataType() == MetricDataType.SHORT) {
                                        stmt.setInt(idx++, buf.getShort(pos));
                                        pos += 2;
                                    } else if (m.getDataType() == MetricDataType.INT) {
                                        stmt.setInt(idx++, buf.getInt(pos));
                                        pos += 4;
                                    } else if (m.getDataType() == MetricDataType.LONG) {
                                        stmt.setLong(idx++, buf.getLong(pos));
                                        pos += 8;
                                    } else if (m.getDataType() == MetricDataType.FLOAT) {
                                        stmt.setString(idx++, df.format(buf.getFloat(pos)));
                                        pos += 4;
                                    } else if (m.getDataType() == MetricDataType.DOUBLE) {
                                        stmt.setString(idx++, df.format(buf.getDouble(pos)));
                                        pos += 8;
                                    }
                                }
                                //one row
                                stmt.execute();
                                stmt.clearBatch();
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("Exception when store metrics: " + s, ex);
                    }
                    //try{
                    //  stmt.executeBatch();
                    //}catch(Exception iex)
                    //{
                    // log.warn("Failed: "+sql);
                    //  throw iex;
                    //}
                    //storeConnection.commit();
                    this.lastConnTime = System.currentTimeMillis();
                    log.debug("Stored " + q2.size() + " " + s + " metric records.");
                }
            }
        } catch (Exception ex) {
            log.warn("Exception when store metrics", ex);
            if (storeConnection != null) {
                try {
                    storeConnection.rollback();
                } catch (Exception iex) {
                }
            }
        }
    }

    /**
     * Store generic metrics
     *
     * @param q2
     * @throws SQLException
     */
    protected void storeGenericMetric(String mg, List<MetricsData> q2, Connection conn) throws SQLException {
        String sql = this.insertSQL.get(mg);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int cnt = 0;
            for (MetricsData mdata : q2) {
                ByteBuffer buf = mdata.data;
                int idx = 1;
                int pos = 0;
                stmt.setInt(idx++, buf.getInt(pos));
                //DB ID
                pos += 4;
                stmt.setInt(idx++, buf.getInt(pos));
                //METRICS ID
                pos += 4;
                stmt.setInt(idx++, buf.getInt(pos));
                //SNAP_ID
                pos += 4;
                stmt.setString(idx++, sdf.format(new Date(buf.getLong(pos))));
                //TODO sdf thread safety
                pos += 8;
                stmt.setString(idx++, df.format(buf.getDouble(pos)));
                stmt.addBatch();
                cnt++;
                if (cnt == 20) {
                    stmt.executeBatch();
                    cnt = 0;
                    //conn.commit();
                    stmt.clearBatch();
                }
            }
            //for loop
            if (cnt > 0) {
                stmt.executeBatch();
                //conn.commit();
            }
        }
    }

    /**
     * remove all old alerts exceeded retention threshold
     */
    public void purgeAlerts(long endDate) {
        String sql = "delete from ALERT where ts<=?";
        log.info("To purge alerts up to " + endDate);
        try (Connection conn = createConnection(true)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, endDate);
                stmt.execute();
            } catch (Exception transactionException) {
                try {
                    conn.rollback();
                } catch (Exception iex) {
                }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Failed to purge alerts up to " + endDate, ex);
        }
    }

    /**
     * Purge metric data up to endDate
     *
     * @param metricGroupName
     * @param dbid
     * @param endDate
     */
    public void purge(String metricGroupName, int dbid, long endDate) {
        int[] snaps = this.getSnapshostRange(-1, endDate);
        //no data
        if (snaps == null)
            return;
        String sql = "delete from " + metricGroupName + " where dbid=? and snap_id<=?";
        //log.info("To purge metrics "+metricGroupName+" for db "+dbid+" up to " +endDate);
        try (Connection conn = createConnection(true)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, dbid);
                stmt.setInt(2, snaps[1]);
                stmt.execute();
            } catch (Exception transactionException) {
                try {
                    conn.rollback();
                } catch (Exception iex) {
                }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Failed to purge metrics " + metricGroupName + " for db " + dbid + " up to " + endDate, ex);
        }
    }

    /**
     * Purge all metrics data for a given db when it is removed from management
     *
     * @param metricGroupName
     * @param dbid
     */
    public void purgeAll(String metricGroupName, int dbid) {
        //TODO make it configurable
        int batchSize = 10000;
        String sql = "delete from " + metricGroupName + " where dbid=? limit " + batchSize;
        if (!this.isLimitSupport())
            sql = "delete from " + metricGroupName + " where dbid=?";
        if (this.isLimitSupport())
            log.info("To purge metrics " + metricGroupName + " for db " + dbid + " using batch of " + batchSize);
        else
            log.info("To purge metrics " + metricGroupName + " for db " + dbid);
        try (Connection conn = createConnection(true)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                while (true) {
                    stmt.setInt(1, dbid);
                    stmt.execute();
                    int total = stmt.getUpdateCount();
                    if (total <= 0 || !this.isLimitSupport())
                        break;
                    else
                        log.info("To purge metrics " + metricGroupName + " for db " + dbid + ": " + total);
                }
            } catch (Exception transactionException) {
                try {
                    conn.rollback();
                } catch (Exception iex) {
                }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Failed to purge metrics " + metricGroupName + " for db " + dbid + " using batch size  " + batchSize, ex);
        }
    }

    /**
     * Remove metrics data from metrics storage for a given db.
     * It will start a thread to do it.
     *
     * @param dbid a group of ids to purge
     */
    public void purgeMetricsForDbInstance(int[] dbid) {
        MetricsRetentionTask purgeTask = new MetricsRetentionTask(this.frameworkContext, 0, dbid);
        new Thread(purgeTask).start();
    }

    public ResultList retrieveMetrics(String metricGroupName, int dbid, long startDate, long endDate) {
        int[] snaps = this.getSnapshostRange(startDate, endDate);
        //no data
        if (snaps == null)
            return null;
        //later, connection pooling
        ResultList rList = null;
        String sql = "select * from " + metricGroupName + " where dbid=? and snap_id between ? and ? order by dbid, snap_id";
        //String sql = "select * from "+metricGroupName+" where dbid=?";
        log.debug("To retrieve metrics " + metricGroupName + " for db " + dbid + " with time range (" + startDate + ", " + endDate + "), snap (" + snaps[0] + ", " + snaps[1] + ")");
        try (Connection conn = createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setFetchSize(1000);
            //stmt.setMaxRows(5000);
            stmt.setInt(1, dbid);
            stmt.setInt(2, snaps[0]);
            stmt.setInt(3, snaps[1]);
            try (ResultSet rs = stmt.executeQuery()) {
                rList = ResultListUtil.fromSqlResultSet(rs, 5000);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve metrics " + metricGroupName + " for db " + dbid + " with time range (" + startDate + ", " + endDate + ")", ex);
        }
        return rList;
    }

    public ResultList retrieveMetrics(String metricGroupName, String[] metrics, boolean hasKeyColumn, int dbid, long startDate, long endDate) {
        int[] snaps = this.getSnapshostRange(startDate, endDate);
        //no data
        if (snaps == null)
            return null;
        if (//not specify the metrics? Get all
        metrics == null || metrics.length == 0)
            return retrieveMetrics(metricGroupName, dbid, startDate, endDate);
        //later, connection pooling
        ResultList rList = null;
        //build select list
        StringBuilder sb = new StringBuilder();
        sb.append("SNAP_ID");
        if (hasKeyColumn)
            sb.append(", KEY_COLUMN");
        sb.append(", TS");
        for (String me : metrics) {
            sb.append(", ");
            sb.append(me);
        }
        String sql = "select " + sb.toString() + " from " + metricGroupName + " where dbid=? and snap_id between ? and ? order by dbid, snap_id";
        //String sql = "select * from "+metricGroupName+" where dbid=?";
        log.info("To retrieve metrics " + metricGroupName + ", metrics (" + sb.toString() + ") for db " + dbid + " with time range (" + startDate + ", " + endDate + "), snap (" + snaps[0] + ", " + snaps[1] + ")");
        try (Connection conn = createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setFetchSize(1000);
            //stmt.setMaxRows(5000);
            stmt.setInt(1, dbid);
            stmt.setInt(2, snaps[0]);
            stmt.setInt(3, snaps[1]);
            try (ResultSet rs = stmt.executeQuery()) {
                rList = ResultListUtil.fromSqlResultSet(rs, 5000);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve metrics " + metricGroupName + " for db " + dbid + " with time range (" + startDate + ", " + endDate + ")", ex);
        }
        return rList;
    }

    public ResultList retrieveMetrics(String metricGroupName, Metric[] metrics, boolean hasKeyColumn, int dbid, long startDate, long endDate, boolean agg) {
        String[] ms = new String[metrics.length];
        for (int i = 0; i < metrics.length; i++) ms[i] = metrics[i].getName();
        if (!agg)
            return retrieveMetrics(metricGroupName, ms, hasKeyColumn, dbid, startDate, endDate);
        int[] snaps = this.getSnapshostRange(startDate, endDate);
        //no data
        if (snaps == null)
            return null;
        if (//not specify the metrics? Get all
        metrics == null || metrics.length == 0)
            return retrieveMetrics(metricGroupName, dbid, startDate, endDate);
        //later, connection pooling
        ResultList rList = null;
        //build select list
        StringBuilder sb = new StringBuilder();
        //build select list
        StringBuilder grpBy = new StringBuilder();
        sb.append("SNAP_ID");
        sb.append(", TS");
        for (Metric me : metrics) {
            if (me.isIncremental())
                sb.append(", sum(");
            else
                sb.append(", avg(");
            sb.append(me.getName()).append(") ").append(me.getName());
        }
        String sql = "select " + sb.toString() + " from " + metricGroupName + " where dbid=? and snap_id between ? and ? group by snap_id, ts order by snap_id";
        //String sql = "select * from "+metricGroupName+" where dbid=?";
        log.info("To retrieve metrics " + metricGroupName + ", metrics (" + sb.toString() + ") for db " + dbid + " with time range (" + startDate + ", " + endDate + "), snap (" + snaps[0] + ", " + snaps[1] + ")");
        try (Connection conn = createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setFetchSize(1000);
            //stmt.setMaxRows(5000);
            stmt.setInt(1, dbid);
            stmt.setInt(2, snaps[0]);
            stmt.setInt(3, snaps[1]);
            try (ResultSet rs = stmt.executeQuery()) {
                rList = ResultListUtil.fromSqlResultSet(rs, 5000);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve metrics " + metricGroupName + " for db " + dbid + " with time range (" + startDate + ", " + endDate + ")", ex);
        }
        return rList;
    }

    /**
     * Retrieve user defined merics
     *
     * @param metrics
     * @param dbid
     * @param startDate
     * @param endDate
     * @return
     */
    public ResultList retrieveUDMMetrics(String metric, int dbid, long startDate, long endDate) {
        int[] snaps = this.getSnapshostRange(startDate, endDate);
        //no data
        if (snaps == null)
            return null;
        //later, connection pooling
        ResultList rList = null;
        int code = 0;
        if (this.metricCodeMap.containsKey(metric)) {
            code = this.metricCodeMap.get(metric);
        } else {
            log.warn("Failed to find metrics code for " + metric + ", " + this.metricCodeMap);
            return null;
        }
        String sql = "select SNAP_ID, TS, METRIC_ID, VALUE from METRIC_GENERIC where dbid=? and snap_id between ? and ? and METRIC_ID=? order by dbid, METRIC_ID, snap_id";
        //String sql = "select * from "+metricGroupName+" where dbid=?";
        log.info("To retrieve " + metric + ", " + code + " on db " + dbid + " with time range (" + startDate + ", " + endDate + "), using " + sql);
        try (Connection conn = createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setFetchSize(1000);
            //stmt.setMaxRows(5000);
            stmt.setInt(1, dbid);
            stmt.setInt(2, snaps[0]);
            stmt.setInt(3, snaps[1]);
            stmt.setLong(4, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs == null)
                    return rList;
                rList = new ResultList();
                //java.sql.ResultSetMetaData meta =  rs.getMetaData();
                ColumnDescriptor desc = new ColumnDescriptor();
                desc.addColumn("SNAP_ID", true, 1);
                desc.addColumn("TS", true, 2);
                desc.addColumn(metric, true, 3);
                rList.setColumnDescriptor(desc);
                int rowCnt = 0;
                //List<ColumnInfo> cols = desc.getColumns();
                while (rs.next()) {
                    //log.info(new java.util.Date()+": process "+rowCnt+" rows");
                    ResultRow row = new ResultRow();
                    row.setColumnDescriptor(desc);
                    ArrayList<String> cols2 = new ArrayList<>(3);
                    cols2.add(rs.getString(1));
                    cols2.add(rs.getString(2));
                    cols2.add(rs.getString(4));
                    row.setColumns(cols2);
                    rList.addRow(row);
                    rowCnt++;
                    if (rowCnt >= 5000)
                        break;
                }
                log.info(new Date() + ": Process results done: " + rList.getRows().size());
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve UDM " + metric + " for db " + dbid + " with time range (" + startDate + ", " + endDate + ")", ex);
        }
        return rList;
    }

    public ResultList retrieveMetricsStatus(String metricGroupName, int dbid, long startDate, long endDate) {
        int[] snaps = this.getSnapshostRange(startDate, endDate);
        //no data
        if (snaps == null)
            return null;
        //later, connection pooling
        ResultList rList = null;
        String sql = "select DBID, SNAP_ID, TS, SQL_TIME, SLOW_QUERIES from " + metricGroupName + " where dbid=? and snap_id between ? and ? order by dbid, snap_id";
        //String sql = "select * from "+metricGroupName+" where dbid=?";
        log.debug("Retrieve metrics status from " + metricGroupName + " for db " + dbid + " with time range (" + startDate + ", " + endDate + ")");
        try (Connection conn = createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, dbid);
            stmt.setInt(2, snaps[0]);
            stmt.setInt(3, snaps[1]);
            try (ResultSet rs = stmt.executeQuery()) {
                rList = ResultListUtil.fromSqlResultSet(rs, 5000);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve metrics " + metricGroupName + " for db " + dbid + " with time range (" + startDate + ", " + endDate + ")", ex);
        }
        return rList;
    }


    private void loadMetricCode() {
        String sql = "select CODE_ID, NAME from METRIC_CODE";
        try (Connection conn = createConnection(true);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs != null && rs.next()) {
                this.metricCodeMap.put(rs.getString("NAME"), rs.getInt("CODE_ID"));
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve metrics METRIC_CODE)", ex);
        }
    }

    /**
     * Check if metric existed
     *
     * @param name
     * @return
     */
    public int checkAndAddMetricCode(String name) {
        //if(this.metricCodeMap.containsKey(name.toLowerCase()))
        //	  return this.metricCodeMap.get(name.toLowerCase());
        if (//make it case sensitive, as the user input
        this.metricCodeMap.containsKey(name))
            return this.metricCodeMap.get(name);
        else //add one
        {
            synchronized (this.codeLock) {
                if (this.metricCodeMap.containsKey(name))
                    return this.metricCodeMap.get(name);
                String sql = "insert into METRIC_CODE (NAME) values (?)";
                try (Connection conn = createConnection(true)) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, name);
                        stmt.execute();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("select NAME, CODE_ID from METRIC_CODE where NAME=?")) {
                        stmt.setString(1, name);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs != null && rs.next()) {
                                this.metricCodeMap.put(rs.getString("NAME"), rs.getInt("CODE_ID"));
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.error("Failed to store/retrieve metrics METRIC_CODE)", ex);
                }
            }
            if (this.metricCodeMap.containsKey(name))
                return this.metricCodeMap.get(name);
        }
        return -1;
    }

    public boolean addNewAlert(long ts, int dbid, String alert_type, String alert_reason) {
        log.info("ALERT INSERT: (" + dbid + ", " + ts + ", " + alert_type + ", " + alert_reason + ")");
        String sql = "insert into ALERT (DBID, TS, ALERT_TYPE, ALERT_REASON) values (?,?,?,?)";
        try (Connection conn = createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, dbid);
            stmt.setLong(2, ts);
            stmt.setString(3, alert_type);
            stmt.setString(4, alert_reason);
            stmt.execute();
            return true;
        } catch (Exception ex) {
            log.error("Failed to store alert data", ex);
        }
        return false;
    }

    public boolean markAlertEnd(long ts, int dbid, long end_ts) {
        log.info("ALERT UPDATE: (" + dbid + ", " + ts + ", " + end_ts + ")");
        //mark all alerts for the same db within the same day end
        String sql = "update ALERT set end_ts=? where dbid=? and ts<=? and ts >= ? - 240000";
        try (Connection conn = createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, end_ts);
            stmt.setInt(2, dbid);
            stmt.setLong(3, ts);
            stmt.setLong(4, ts);
            stmt.execute();
            return true;
        } catch (Exception ex) {
            log.error("Failed to update alert data", ex);
        }
        return false;
    }

    public boolean markAlertEnd(long ts, int dbid, long end_ts, String alertName) {
        log.info("ALERT UPDATE: (" + dbid + ", " + ts + ", " + end_ts + ")");
        //mark all alerts for the same db within the same day end
        String sql = "update ALERT set end_ts=? where dbid=? and ts<=? and ts >= ? - 240000 and ALERT_TYPE=?";
        try (Connection conn = createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, end_ts);
            stmt.setInt(2, dbid);
            stmt.setLong(3, ts);
            stmt.setLong(4, ts);
            stmt.setString(5, alertName);
            stmt.execute();
            return true;
        } catch (Exception ex) {
            log.error("Failed to update alert data", ex);
        }
        return false;
    }

    /**
     * @param startTs
     * @param endTs
     * @param dbs         a map for look up db info by dbis
     * @param filteredDbs actual dbs to retrieve alerts
     * @return
     */
    public ResultList retrieveAlerts(String startTs, String endTs, Map<Integer, DBInstanceInfo> dbs, List<Integer> filteredDbs) {
        ResultList rList = null;
        rList = new ResultList();
        ColumnDescriptor desc = new ColumnDescriptor();
        desc.addColumn("DBGROUP", false, 1);
        desc.addColumn("HOST", false, 2);
        desc.addColumn("TS", false, 3);
        desc.addColumn("END_TS", false, 4);
        desc.addColumn("ALERT_TYPE", false, 5);
        desc.addColumn("ALERT_REASON", false, 6);
        desc.addColumn("BY CPU", true, 7);
        desc.addColumn("BY IO", true, 8);
        desc.addColumn("BY THREAD", true, 9);
        desc.addColumn("BY LOADAVG", true, 10);
        desc.addColumn("BY REPL LAG", true, 11);
        desc.addColumn("BY SLOW QUERY", true, 12);
        desc.addColumn("BY REPL DOWN", true, 13);
        desc.addColumn("BY CONN FAILURE", true, 14);
        desc.addColumn("BY DEADLOCKS", true, 15);
        rList.setColumnDescriptor(desc);
        StringBuilder sb = new StringBuilder();
        sb.append("select * from ALERT where ts between ").append(startTs).append(" and ").append(endTs);
        if (filteredDbs != null && filteredDbs.size() >= 1) {
            sb.append(" and dbid in (");
            boolean isFirst = true;
            for (Integer id : filteredDbs) {
                if (!isFirst)
                    sb.append(",");
                sb.append(id);
                isFirst = false;
            }
            sb.append(")");
        }
        sb.append(" order by ts desc");
        String sql = sb.toString();
        log.info("Retrieve alerts: " + sql);
        Map<Integer, AlertSummary> sumMap = null;
        try (Connection conn = createConnection(true)) {
            sumMap = this.retrieve7DaysAlertsSummary(conn, filteredDbs);
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
                //DBID, TS, END_TS, ALERT_TYPE, ALERT_REASON
                while (rs != null && rs.next()) {
                    int dbid = rs.getInt("DBID");
                    if (!dbs.containsKey(dbid))
                        //no db records, ignore
                        continue;
                    DBInstanceInfo dbinfo = dbs.get(dbid);
                    ResultRow row = new ResultRow();
                    row.setColumnDescriptor(desc);
                    row.addColumn(dbinfo.getDbGroupName());
                    row.addColumn(dbinfo.getHostName());
                    row.addColumn(formatDatetime(rs.getString("TS")));
                    row.addColumn(formatDatetime(rs.getString("END_TS")));
                    row.addColumn(rs.getString("ALERT_TYPE"));
                    row.addColumn(rs.getString("ALERT_REASON"));
                    //don't expect missing
                    AlertSummary sum = sumMap.get(dbid);
                    row.addColumn(sum != null ? String.valueOf(sum.cpuAlerts) : "0");
                    row.addColumn(sum != null ? String.valueOf(sum.ioAlerts) : "0");
                    row.addColumn(sum != null ? String.valueOf(sum.threadAlerts) : "0");
                    row.addColumn(sum != null ? String.valueOf(sum.LoadAvgAlerts) : "0");
                    row.addColumn(sum != null ? String.valueOf(sum.replAlerts) : "0");
                    row.addColumn(sum != null ? String.valueOf(sum.slowAlerts) : "0");
                    row.addColumn(sum != null ? String.valueOf(sum.replDown) : "0");
                    row.addColumn(sum != null ? String.valueOf(sum.connectFailuerAlerts) : "0");
                    row.addColumn(sum != null ? String.valueOf(sum.deadlocks) : "0");
                    rList.addRow(row);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve alerts " + sql, ex);
        }
        return rList;
    }

    public static class AlertSummary {
        int dbid;
        int cpuAlerts = 0;
        int LoadAvgAlerts = 0;
        int ioAlerts = 0;
        int threadAlerts = 0;
        int replAlerts = 0;
        int slowAlerts = 0;
        int connectFailuerAlerts = 0;
        int replDown = 0;
        int deadlocks = 0;

        AlertSummary() {

        }
    }

    private Map<Integer, AlertSummary> retrieve7DaysAlertsSummary(Connection conn, List<Integer> filteredDbs) {
        Map<Integer, AlertSummary> resMap = new HashMap<>();
        //metricsDB TS column format
        SimpleDateFormat alertsdf = new SimpleDateFormat("yyyyMMddHHmmss");
        alertsdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String startTs = null;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, -7);
        startTs = sdf.format(c.getTime());
        StringBuilder sb = new StringBuilder();
        sb.append("select DBID, ALERT_TYPE, COUNT(*) AS TOTAL from ALERT where ts >= ").append(startTs);
        if (filteredDbs != null && filteredDbs.size() >= 1) {
            sb.append(" and dbid in (");
            boolean isFirst = true;
            for (Integer id : filteredDbs) {
                if (!isFirst)
                    sb.append(",");
                sb.append(id);
                isFirst = false;
            }
            sb.append(")");
        }
        sb.append(" GROUP By DBID, ALERT_TYPE");
        String sql = sb.toString();
        log.info("Retrieve alerts summary: " + sql);
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            //DBID, TS, END_TS, ALERT_TYPE, ALERT_REASON
            while (rs != null && rs.next()) {
                int dbid = rs.getInt("DBID");
                String alertType = rs.getString("ALERT_TYPE");
                int acount = rs.getInt(3);
                if (!resMap.containsKey(dbid)) {
                    AlertSummary sum = new AlertSummary();
                    sum.dbid = dbid;
                    resMap.put(dbid, sum);
                }
                {
                    AlertSummary sum = resMap.get(dbid);
                    if ("CPU".equalsIgnoreCase(alertType))
                        sum.cpuAlerts = acount;
                    else if ("LOADAVG".equalsIgnoreCase(alertType))
                        sum.LoadAvgAlerts = acount;
                    else if ("IO".equalsIgnoreCase(alertType))
                        sum.ioAlerts = acount;
                    else if ("THREAD".equalsIgnoreCase(alertType))
                        sum.threadAlerts = acount;
                    else if ("REPLLAG".equalsIgnoreCase(alertType))
                        sum.replAlerts = acount;
                    else if ("SLOW".equalsIgnoreCase(alertType))
                        sum.slowAlerts = acount;
                    else if ("REPLDOWN".equalsIgnoreCase(alertType))
                        sum.replDown = acount;
                    else if ("CONNECT_FAILURE".equalsIgnoreCase(alertType))
                        sum.connectFailuerAlerts = acount;
                    else if ("DEADLOCKS".equalsIgnoreCase(alertType))
                        sum.deadlocks = acount;
                }
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve alerts " + sql, ex);
        }
        return resMap;
    }

    private String formatDatetime(String str) {
        try {
            if (str != null && str.length() == 14) {
                StringBuilder sb = new StringBuilder();
                sb.append(str.substring(0, 4))
                        .append('-')
                        .append(str.substring(4, 6))
                        .append('-')
                        .append(str.substring(6, 8))
                        .append(' ')
                        .append(str.substring(8, 10))
                        .append(':')
                        .append(str.substring(10, 12))
                        .append(':')
                        .append(str.substring(12, 14));
                return sb.toString();
            }
        } catch (Exception ex) {
        }

        return "";
    }

    private boolean removeDBInfoById(Connection conn, int dbid) {
        String sql = "delete from " + DBINFO_TABLENAME + " where dbid=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, dbid);
            pstmt.execute();
            log.info("Removed the database host : (" + dbid + ")");
            return pstmt.getUpdateCount() > 0;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    private boolean removeDBInfo(Connection conn, String dbGroupName, String hostName) {
        String sql = "delete from " + DBINFO_TABLENAME + " where dbgroupname=? and hostname=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dbGroupName.toLowerCase());
            pstmt.setString(2, hostName.toLowerCase());
            pstmt.execute();
            log.info("Removed the database host : (" + dbGroupName + ", " + hostName + ")");
            return pstmt.getUpdateCount() > 0;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    public boolean removeDbGroup(Connection conn, String dbGroupName) {
        String sql = "delete from " + DBINFO_TABLENAME + " where dbgroupname=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dbGroupName.toLowerCase());
            pstmt.execute();
            log.info("Removed the database host : (" + dbGroupName + ")");
            return pstmt.getUpdateCount() > 0;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    /**
     * Store db info into db. If exists, update the info. If not, insert new one.
     *
     * @param dbinfo
     */
    public boolean upsertDBInfo(Connection conn, DBInstanceInfo dbinfo, boolean insert) {
        if (dbinfo == null)
            return false;
        String sql2 = "update " + DBINFO_TABLENAME + " set dbgroupname=?, hostname=?, dbtype=?,instance=?, port=?, database_name=?,USE_SSHTUNNEL=?, LOCAL_HOSTNAME=?, LOCAL_PORT=?, CONNECTION_VERIFIED=?, VIRTUAL_HOST=?, OWNER=? where DBID=?";
        String sql3 = "insert into " + DBINFO_TABLENAME + " (dbgroupname,hostname,dbtype,instance, port, database_name,USE_SSHTUNNEL, LOCAL_HOSTNAME, LOCAL_PORT,CONNECTION_VERIFIED,VIRTUAL_HOST, OWNER, DBID) values(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        log.info("Store or update db " + dbinfo.toString());
        boolean findOne = false;
        try {
            //first, check if we have record
            findOne = !insert;
            try (PreparedStatement pstmt = conn.prepareStatement(findOne ? sql2 : sql3)) {
                int idx = 1;
                pstmt.setString(idx++, dbinfo.getDbGroupName().toLowerCase());
                pstmt.setString(idx++, dbinfo.getHostName().toLowerCase());
                pstmt.setString(idx++, dbinfo.getDbType());
                pstmt.setString(idx++, String.valueOf(dbinfo.getInstance()));
                if (dbinfo.getPortShort() != 0)
                    pstmt.setShort(idx++, dbinfo.getPortShort());
                else
                    pstmt.setNull(idx++, Types.SMALLINT);
                pstmt.setString(idx++, dbinfo.getDatabaseName());
                pstmt.setString(idx++, dbinfo.isUseTunneling() ? "1" : "0");
                pstmt.setString(idx++, dbinfo.getLocalHostName());
                if (dbinfo.getLocalPortShort() != 0)
                    pstmt.setShort(idx++, dbinfo.getLocalPortShort());
                else
                    pstmt.setNull(idx++, Types.SMALLINT);
                pstmt.setString(idx++, dbinfo.isConnectionVerified() ? "1" : "0");
                pstmt.setString(idx++, dbinfo.isVirtualHost() ? "1" : "0");
                pstmt.setString(idx++, dbinfo.getOwner());
                pstmt.setInt(idx++, dbinfo.getDbid());
                pstmt.execute();
                return true;
            }
        } catch (Exception ex) {
            log.info("Failed to save " + dbinfo.toString() + ", " + ex.getMessage());
            if (conn != null)
                try {
                    conn.rollback();
                } catch (Exception iex) {
                }
            return false;
        }
    }

    public void renameDbGroup(String oldName, String newName) {
        String[] renameDbinfoSql = new String[] { "update " + DBINFO_TABLENAME + " set dbgroupname=? where dbgroupname=?", "update " + ALERTSETTING_TABLENAME + " set dbgroupname=? where dbgroupname=?", "update ALERT_SUBSCRIPT set DBGROUP=? where DBGROUP=?", "update METRICS_SUBSCRIPT set DBGROUP=? where DBGROUP=?" };
        try (Connection conn = this.createConnection(true)) {
            for (int i = 0; i < renameDbinfoSql.length; i++) {
                log.info("Rename dbgroup: " + renameDbinfoSql[i]);
                try (PreparedStatement stmt = conn.prepareStatement(renameDbinfoSql[i])) {
                    stmt.setString(1, newName.toLowerCase());
                    stmt.setString(2, oldName.toLowerCase());
                    stmt.execute();
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to rename dbgroup " + oldName + " to " + newName, ex);
            throw new RuntimeException(ex);
        }
    }

    public Map<Integer, DBInstanceInfo> loadDbInfo(Connection conn) {
        String sql = "select * from " + DBINFO_TABLENAME;
        Map<Integer, DBInstanceInfo> dbMap = new HashMap<>();
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs != null && rs.next()) {
                DBInstanceInfo dbinfo = new DBInstanceInfo();
                dbinfo.setDbid(rs.getInt("DBID"));
                dbinfo.setDbType(rs.getString("DBTYPE"));
                dbinfo.setDbGroupName(rs.getString("DBGROUPNAME"));
                dbinfo.setInstance(rs.getShort("INSTANCE"));
                dbinfo.setHostName(rs.getString("HOSTNAME"));
                dbinfo.setPort(rs.getString("PORT"));
                dbinfo.setDatabaseName(rs.getString("DATABASE_NAME"));
                dbinfo.setUseTunneling(rs.getShort("USE_SSHTUNNEL") == 1 ? true : false);
                dbinfo.setLocalHostName(rs.getString("LOCAL_HOSTNAME"));
                dbinfo.setLocalPort(rs.getString("LOCAL_PORT"));
                dbinfo.setConnectionVerified(rs.getInt("CONNECTION_VERIFIED") == 1);
                dbinfo.setVirtualHost(rs.getInt("VIRTUAL_HOST") == 1);
                dbinfo.setSnmpEnabled(rs.getShort("SNMP_ENABLED") == 1 ? true : false);
                dbinfo.setMetricsEnabled(rs.getShort("METRICS_ENABLED") == 1 ? true : false);
                dbinfo.setAlertEnabled(rs.getShort("ALERT_ENABLED") == 1 ? true : false);
                dbinfo.setOwner(rs.getString("OWNER"));
                dbMap.put(dbinfo.getDbid(), dbinfo);
            }
        } catch (Exception ex) {
        }
        return dbMap;
    }

    class MetaHostSyncer implements Runnable {
        @Setter
        private List<DBInstanceInfo> dbList;

        MetaHostSyncer() {

        }

        @Override
        public void run() {
        Map<Integer, DBInstanceInfo> metaHosts = new HashMap<>(dbList.size());
        for (DBInstanceInfo info : dbList) metaHosts.put(info.getDbid(), info);
        try {
            log.info("Start to sync meta DB host info with metrics DB host info");
            try (Connection conn = createConnection(true)) {
                Map<Integer, DBInstanceInfo> metricsHosts = loadDbInfo(conn);
                for (DBInstanceInfo info : dbList) {
                    int dbid = info.getDbid();
                    if (!metricsHosts.containsKey(dbid)) {
                        upsertDBInfo(conn, info, true);
                    } else {
                        DBInstanceInfo info2 = metricsHosts.get(dbid);
                        try {
                            if (!info2.getDbGroupName().equals(info.getDbGroupName()) || !info2.getHostName().equals(info.getHostName()))
                                upsertDBInfo(conn, info, false);
                        } catch (Exception ex) {
                        }
                    }
                }
            }
        } catch (Exception ex) {
        }
        log.info("Ended: sync meta DB host info with metrics DB host info");
    }

    }

    /**
     * remove one entry from meta db
     *
     * @param dbGroupName
     * @param hostName
     * @param owner
     * @param force       If false, only delete the entry owned by owner
     * @return
     */
    public boolean removeDBInfo(String dbGroupName, String hostName, String owner, boolean force) {
        try (Connection conn = this.createConnection(true)) {
            if (removeDBInfo(conn, dbGroupName, hostName, owner, force))
                return this.removeAlertSetting(conn, dbGroupName, hostName);
            return false;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    private boolean removeDBInfo(Connection conn, String dbGroupName, String hostName, String owner, boolean force) {
        String sql = "delete from " + DBINFO_TABLENAME + " where dbgroupname=? and hostname=?";
        if (!force)
            sql = "delete from " + DBINFO_TABLENAME + " where dbgroupname=? and hostname=? and owner=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dbGroupName.toLowerCase());
            pstmt.setString(2, hostName.toLowerCase());
            if (!force)
                pstmt.setString(3, owner);
            pstmt.execute();
            log.info("Removed the database host : (" + dbGroupName + ", " + hostName + "), owenr " + owner + ", force " + force);
            return pstmt.getUpdateCount() > 0;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    public boolean removeAlertSetting(String dbGroupName, String hostName) {
        try (Connection conn = this.createConnection(true)) {
            return removeAlertSetting(conn, dbGroupName, hostName);
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    private boolean removeAlertSetting(Connection conn, String dbGroupName, String hostName) {
        String sql = "delete from " + ALERTSETTING_TABLENAME + " where dbgroupname=? and hostname=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dbGroupName.toLowerCase());
            pstmt.setString(2, hostName.toLowerCase());
            pstmt.execute();
            log.info("Removed alert setting for the database host : (" + dbGroupName + ", " + hostName + ")");
            return true;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    /**
     * @param dbGroupName
     * @param owner
     * @param force       if false, only remove entries with the same owner as owner
     * @return
     */
    public boolean removeDbGroup(String dbGroupName, String owner, boolean force) {
        try (Connection conn = this.createConnection(true)) {
            return removeDbGroup(conn, dbGroupName, owner, force);
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    public boolean removeDbGroup(Connection conn, String dbGroupName, String owner, boolean force) {
        String sql = "delete from " + DBINFO_TABLENAME + " where dbgroupname=?";
        if (!force)
            sql = "delete from " + DBINFO_TABLENAME + " where dbgroupname=? and owner=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dbGroupName.toLowerCase());
            if (!force)
                pstmt.setString(2, owner);
            pstmt.execute();
            log.info("Removed the database host : (" + dbGroupName + ")");
            return pstmt.getUpdateCount() > 0;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return false;
    }

    /**
     * Retrieve a database entry identified by a dbGroupName and hostname
     *
     * @param dbGroupName
     * @param hostName
     * @return
     */
    public DBInstanceInfo retrieveDBInfo(String dbGroupName, String hostName) {
        try (Connection conn = createConnection(true)) {
            return retrieveDBInfo(conn, dbGroupName, hostName);
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return null;
    }

    private DBInstanceInfo retrieveDBInfo(Connection conn, String dbGroupName, String hostName) {
        String sql = "select * from " + DBINFO_TABLENAME + " where dbgroupname=? and hostname=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dbGroupName.toLowerCase());
            pstmt.setString(2, hostName.toLowerCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs != null && rs.next()) {
                    DBInstanceInfo dbinfo = new DBInstanceInfo();
                    dbinfo.setDbid(rs.getInt("DBID"));
                    dbinfo.setDbType(rs.getString("DBTYPE"));
                    dbinfo.setDbGroupName(rs.getString("DBGROUPNAME"));
                    dbinfo.setInstance(rs.getShort("INSTANCE"));
                    dbinfo.setHostName(rs.getString("HOSTNAME"));
                    dbinfo.setPort(rs.getString("PORT"));
                    dbinfo.setDatabaseName(rs.getString("DATABASE_NAME"));
                    dbinfo.setUseTunneling(rs.getShort("USE_SSHTUNNEL") == 1 ? true : false);
                    dbinfo.setLocalHostName(rs.getString("LOCAL_HOSTNAME"));
                    dbinfo.setLocalPort(rs.getString("LOCAL_PORT"));
                    dbinfo.setConnectionVerified(1 == rs.getInt("CONNECTION_VERIFIED"));
                    dbinfo.setVirtualHost(rs.getShort("VIRTUAL_HOST") == 1 ? true : false);
                    dbinfo.setSnmpEnabled(rs.getShort("SNMP_ENABLED") == 1 ? true : false);
                    dbinfo.setMetricsEnabled(rs.getShort("METRICS_ENABLED") == 1 ? true : false);
                    dbinfo.setAlertEnabled(rs.getShort("ALERT_ENABLED") == 1 ? true : false);
                    dbinfo.setOwner(rs.getString("OWNER"));
                    return dbinfo;
                }
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return null;
    }

    /**
     * Store db info into db. If exists, update the info. If not, insert new one.
     *
     * @param dbinfo
     */
    public void upsertDBInfo(DBInstanceInfo dbinfo) {
        if (dbinfo == null)
            return;
        String sql2 = "update " + DBINFO_TABLENAME + " set dbtype=?,instance=?, port=?, database_name=?,USE_SSHTUNNEL=?, LOCAL_HOSTNAME=?, LOCAL_PORT=?, CONNECTION_VERIFIED=?, VIRTUAL_HOST=? where DBGROUPNAME=? and HOSTNAME=?";
        String sql3 = "insert into " + DBINFO_TABLENAME + " (dbgroupname,hostname,dbtype,instance, port, database_name,USE_SSHTUNNEL, LOCAL_HOSTNAME, LOCAL_PORT,CONNECTION_VERIFIED,VIRTUAL_HOST, OWNER) values(?,?,?,?,?,?,?,?,?,?,?,?)";
        log.info("Store or update db " + dbinfo.toString());
        boolean findOne = false;
        try (Connection conn = createConnection(true)) {
            try {
                //first, check if we have record
                findOne = this.retrieveDBInfo(conn, dbinfo.getDbGroupName().toLowerCase(), dbinfo.getHostName().toLowerCase()) != null;
                if (findOne) {
                    //conn.commit();
                    try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                        int idx = 1;
                        pstmt.setString(idx++, dbinfo.getDbType());
                        pstmt.setString(idx++, String.valueOf(dbinfo.getInstance()));
                        if (dbinfo.getPortShort() != 0)
                            pstmt.setShort(idx++, dbinfo.getPortShort());
                        else
                            pstmt.setNull(idx++, Types.SMALLINT);
                        pstmt.setString(idx++, dbinfo.getDatabaseName());
                        pstmt.setString(idx++, dbinfo.isUseTunneling() ? "1" : "0");
                        if (dbinfo.getLocalHostName() != null)
                            pstmt.setString(idx++, dbinfo.getLocalHostName().toLowerCase());
                        else
                            pstmt.setNull(idx++, Types.VARCHAR);
                        if (dbinfo.getLocalPortShort() != 0)
                            pstmt.setShort(idx++, dbinfo.getLocalPortShort());
                        else
                            pstmt.setNull(idx++, Types.SMALLINT);
                        pstmt.setString(idx++, dbinfo.isConnectionVerified() ? "1" : "0");
                        pstmt.setString(idx++, dbinfo.isVirtualHost() ? "1" : "0");
                        pstmt.setString(idx++, dbinfo.getDbGroupName().toLowerCase());
                        pstmt.setString(idx++, dbinfo.getHostName().toLowerCase());
                        pstmt.execute();
                    }
                } else {
                    int idx = 1;
                    //conn.commit();
                    try (PreparedStatement pstmt = conn.prepareStatement(sql3)) {
                        pstmt.setString(idx++, dbinfo.getDbGroupName().toLowerCase());
                        pstmt.setString(idx++, dbinfo.getHostName().toLowerCase());
                        pstmt.setString(idx++, dbinfo.getDbType());
                        pstmt.setString(idx++, String.valueOf(dbinfo.getInstance()));
                        if (dbinfo.getPortShort() != 0)
                            pstmt.setShort(idx++, dbinfo.getPortShort());
                        else
                            pstmt.setNull(idx++, Types.SMALLINT);
                        pstmt.setString(idx++, dbinfo.getDatabaseName());
                        pstmt.setString(idx++, dbinfo.isUseTunneling() ? "1" : "0");
                        pstmt.setString(idx++, dbinfo.getLocalHostName());
                        if (dbinfo.getLocalPortShort() != 0)
                            pstmt.setShort(idx++, dbinfo.getLocalPortShort());
                        else
                            pstmt.setNull(idx++, Types.SMALLINT);
                        pstmt.setString(idx++, dbinfo.isConnectionVerified() ? "1" : "0");
                        pstmt.setString(idx++, dbinfo.isVirtualHost() ? "1" : "0");
                        pstmt.setString(idx++, dbinfo.getOwner());
                        pstmt.execute();
                    }
                }
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to save " + dbinfo.toString());
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    public void upsertAlertSetting(String dbgroup, String dbhost, String alertType, float threshold, String comments) {
        String sql2 = "update " + ALERTSETTING_TABLENAME + " set ALERT_TYPE=?,THRESHOLD=?, RESERVED=? where DBGROUPNAME=? and HOSTNAME=?";
        String sql3 = "insert into " + ALERTSETTING_TABLENAME + " (DBGROUPNAME,HOSTNAME,ALERT_TYPE,THRESHOLD, RESERVED) values(?,?,?,?,?)";
        log.info("Store or update alert settings for db " + dbgroup + ", " + dbhost);
        boolean findOne = false;
        try (Connection conn = this.createConnection(true)) {
            try {
                try (PreparedStatement pstmt = conn.prepareStatement("select ALERT_TYPE, THRESHOLD from " + ALERTSETTING_TABLENAME + " where DBGROUPNAME=? and HOSTNAME=?")) {
                    pstmt.setString(1, dbgroup);
                    pstmt.setString(2, dbhost);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs != null && rs.next()) {
                            findOne = true;
                        }
                    }
                }
                if (findOne) {
                    //conn.commit();
                    try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                        int idx = 1;
                        pstmt.setString(idx++, alertType);
                        pstmt.setFloat(idx++, threshold);
                        if (comments != null && !comments.isEmpty())
                            pstmt.setString(idx++, comments);
                        else
                            pstmt.setNull(idx++, Types.VARCHAR);
                        pstmt.setString(idx++, dbgroup);
                        pstmt.setString(idx++, dbhost);
                        pstmt.execute();
                    }
                } else {
                    int idx = 1;
                    //conn.commit();
                    try (PreparedStatement pstmt = conn.prepareStatement(sql3)) {
                        pstmt.setString(idx++, dbgroup);
                        pstmt.setString(idx++, dbhost);
                        pstmt.setString(idx++, alertType);
                        pstmt.setFloat(idx++, threshold);
                        if (comments != null && !comments.isEmpty())
                            pstmt.setString(idx++, comments);
                        else
                            pstmt.setNull(idx++, Types.VARCHAR);
                        pstmt.execute();
                    }
                }
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to save alerts for " + dbgroup + ", " + dbhost);
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    public void upsertAlertNotification(String dbgroup, String dbhost, String emails) {
        String sql2 = "update " + ALERT_NOTIFICATION + " set EMAILS=? where DBGROUP=? and HOSTNAME=?";
        String sql3 = "insert into " + ALERT_NOTIFICATION + " (DBGROUP,HOSTNAME,EMAILS) values(?,?,?)";
        log.info("Store or update alert notifications for db " + dbgroup + ", " + dbhost);
        boolean findOne = false;
        try (Connection conn = this.createConnection(true)) {
            try {
                try (PreparedStatement pstmt = conn.prepareStatement("select EMAILS from " + ALERT_NOTIFICATION + " where DBGROUP=? and HOSTNAME=?")) {
                    pstmt.setString(1, dbgroup);
                    pstmt.setString(2, dbhost);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs != null && rs.next()) {
                            findOne = true;
                        }
                    }
                }
                if (findOne) {
                    //conn.commit();
                    try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                        int idx = 1;
                        pstmt.setString(idx++, emails);
                        pstmt.setString(idx++, dbgroup);
                        pstmt.setString(idx++, dbhost);
                        pstmt.execute();
                    }
                } else {
                    int idx = 1;
                    //conn.commit();
                    try (PreparedStatement pstmt = conn.prepareStatement(sql3)) {
                        pstmt.setString(idx++, dbgroup);
                        pstmt.setString(idx++, dbhost);
                        pstmt.setString(idx++, emails);
                        pstmt.execute();
                    }
                }
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to save alert notification for " + dbgroup + ", " + dbhost);
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }


    public void loadAlertSetting(AlertSettings alertSettings) {
        log.info("Loadalert settings from db");
        int count = 0;
        try (Connection conn = this.createConnection(true);
            Statement pstmt = conn.createStatement()) {
            try (ResultSet rs = pstmt.executeQuery("select DBGROUPNAME, HOSTNAME, ALERT_TYPE, THRESHOLD from " + ALERTSETTING_TABLENAME)) {
                while (rs != null && rs.next()) {
                    String dbgroup = rs.getString("DBGROUPNAME");
                    String dbhost = rs.getString("HOSTNAME");
                    String alertType = rs.getString("ALERT_TYPE");
                    Float threshold = rs.getFloat("THRESHOLD");
                    //we don't want to store it back, so last arg is false
                    alertSettings.updateAlertThreshold(dbgroup, dbhost, alertType, threshold, false);
                    count++;
                }
            }
            try (ResultSet rs = pstmt.executeQuery("select DBGROUP, HOSTNAME, EMAILS from " + ALERT_NOTIFICATION)) {
                while (rs != null && rs.next()) {
                    String dbgroup = rs.getString("DBGROUP");
                    String dbhost = rs.getString("HOSTNAME");
                    String emails = rs.getString("EMAILS");
                    //we don't want to store it back, so last arg is false
                    alertSettings.updateAlertNotification(dbgroup, dbhost, emails, false);
                    count++;
                }
            }
        } catch (Exception ex) {
            log.info("Failed to load alert settings ");
            log.error("Exception", ex);
        }
        log.info("Load " + count + " customized alert settings.");
    }

    /**
     * Enable or disable snmp query
     *
     * @param dbinfo
     * @param enabled
     */
    public boolean enableSnmp(String dbGroup, String hostname, boolean enabled, String owner, boolean force) {
        String sql2 = "update " + DBINFO_TABLENAME + " set SNMP_ENABLED=? where DBGROUPNAME=? and HOSTNAME=?";
        if (!force)
            sql2 = "update " + DBINFO_TABLENAME + " set SNMP_ENABLED=? where DBGROUPNAME=? and HOSTNAME=? AND OWNER=?";
        log.info("Update snmp metrics gathering for " + dbGroup + ", " + hostname);
        try (Connection conn = this.createConnection(true)) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                pstmt.setInt(1, enabled ? 1 : 0);
                pstmt.setString(2, dbGroup);
                pstmt.setString(3, hostname);
                if (!force)
                    pstmt.setString(4, owner);
                pstmt.execute();
                //conn.commit();
                return pstmt.getUpdateCount() > 0;
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to update snmp_enabled " + dbGroup + ", " + hostname);
            log.error("Exception", ex);
            return false;
        }
    }

    public boolean enableMetrics(String dbGroup, String hostname, boolean enabled, String owner, boolean force) {
        String sql2 = "update " + DBINFO_TABLENAME + " set METRICS_ENABLED=? where DBGROUPNAME=? and HOSTNAME=?";
        if (!force)
            sql2 = "update " + DBINFO_TABLENAME + " set METRICS_ENABLED=? where DBGROUPNAME=? and HOSTNAME=? AND OWNER=?";
        log.info("Update metrics gathering for " + dbGroup + ", " + hostname);
        try (Connection conn = this.createConnection(true)) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                pstmt.setInt(1, enabled ? 1 : 0);
                pstmt.setString(2, dbGroup);
                pstmt.setString(3, hostname);
                if (!force)
                    pstmt.setString(4, owner);
                pstmt.execute();
                //conn.commit();
                return pstmt.getUpdateCount() > 0;
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to update metrics_enabled " + dbGroup + ", " + hostname);
            log.error("Exception", ex);
            return false;
        }
    }

    public boolean enableAlerts(String dbGroup, String hostname, boolean enabled, String owner, boolean force) {
        String sql2 = "update " + DBINFO_TABLENAME + " set ALERT_ENABLED=? where DBGROUPNAME=? and HOSTNAME=?";
        if (!force)
            sql2 = "update " + DBINFO_TABLENAME + " set ALERT_ENABLED=? where DBGROUPNAME=? and HOSTNAME=? AND OWNER=?";
        log.info("Update alert gathering for " + dbGroup + ", " + hostname);
        try (Connection conn = this.createConnection(true)) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                pstmt.setInt(1, enabled ? 1 : 0);
                pstmt.setString(2, dbGroup);
                pstmt.setString(3, hostname);
                if (!force)
                    pstmt.setString(4, owner);
                pstmt.execute();
                //conn.commit();
                return pstmt.getUpdateCount() > 0;
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to update alert_enabled " + dbGroup + ", " + hostname);
            log.error("Exception", ex);
            return false;
        }
    }

    /**
     * Retrieve all DB entries based on keyword.
     * To retrieve all entries, use blank keyword (null string,
     * empty string, or %)
     *
     * @param keyword
     * @return
     */
    public List<DBInstanceInfo> SearchDbInfo(String keyword) {
        String sql = null;
        if (keyword == null || keyword.isEmpty() || "%".equals(keyword.trim()))
            sql = "select * from " + DBINFO_TABLENAME + " order by dbtype, dbgroupname, hostname";
        else {
            keyword = keyword.trim();
            sql = "select * from " + DBINFO_TABLENAME + " where hostname like '%" + keyword.toLowerCase() + "%' or lower(database_name) like '%" + keyword.toLowerCase() + "%' or lower(dbgroupname) like '%" + keyword.toLowerCase() + "%' order by dbtype, dbgroupname, hostname";
        }
        log.info("Excute: " + sql);
        ArrayList<DBInstanceInfo> dbList = new ArrayList<>();
        try (Connection conn = this.createConnection(true);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs != null && rs.next()) {
                DBInstanceInfo dbinfo = new DBInstanceInfo();
                dbinfo.setDbid(rs.getInt("DBID"));
                dbinfo.setDbType(rs.getString("DBTYPE"));
                dbinfo.setDbGroupName(rs.getString("DBGROUPNAME"));
                dbinfo.setInstance(rs.getShort("INSTANCE"));
                dbinfo.setHostName(rs.getString("HOSTNAME"));
                dbinfo.setPort(rs.getString("PORT"));
                dbinfo.setDatabaseName(rs.getString("DATABASE_NAME"));
                dbinfo.setUseTunneling(rs.getShort("USE_SSHTUNNEL") == 1 ? true : false);
                dbinfo.setLocalHostName(rs.getString("LOCAL_HOSTNAME"));
                dbinfo.setLocalPort(rs.getString("LOCAL_PORT"));
                dbinfo.setConnectionVerified(rs.getInt("CONNECTION_VERIFIED") == 1);
                dbinfo.setVirtualHost(rs.getInt("VIRTUAL_HOST") == 1);
                dbinfo.setSnmpEnabled(rs.getShort("SNMP_ENABLED") == 1 ? true : false);
                dbinfo.setMetricsEnabled(rs.getShort("METRICS_ENABLED") == 1 ? true : false);
                dbinfo.setAlertEnabled(rs.getShort("ALERT_ENABLED") == 1 ? true : false);
                dbList.add(dbinfo);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve db info", ex);
        }
        return dbList;
    }

    /**
     * add a record in snapshots table for a given timestamp and retrieve the snapid
     *
     * @param ts
     * @return
     */
    public int getNextSnapshotId(long ts) {
        String insertSQL = "insert into SNAPSHOTS (START_TS) values(?)";
        String retrieveSQL = "select SNAP_ID from SNAPSHOTS where START_TS=?";
        try (Connection conn = this.createConnection(true)) {
            try (PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                stmt.setLong(1, ts);
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement(retrieveSQL)) {
                stmt.setLong(1, ts);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs != null && rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (Exception ex) {
        }
        //error
        return -1;
    }

    /**
     * Retrieve snapshot range based on given timestamp range
     *
     * @param startTs
     * @param endTs
     * @return
     */
    public int[] getSnapshostRange(long startTs, long endTs) {
        String retrieveSQL = "select min(SNAP_ID) min_snap_id, max(snap_id) max_snap_id from SNAPSHOTS where START_TS between ? and ?";
        try (Connection conn = this.createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(retrieveSQL)) {
            stmt.setLong(1, startTs);
            stmt.setLong(2, endTs);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs != null && rs.next()) {
                    return new int[] { rs.getInt(1), rs.getInt(2) };
                }
            }
        } catch (Exception ex) {
        }
        //failed
        return null;
    }

    /**
     * Update snapshot end timestamp
     *
     * @param snap_id
     * @param ts
     */
    public void updateSnapCompleteTime(int snap_id, long ts) {
        String sql = "update SNAPSHOTS set END_TS=? where SNAP_ID=?";
        try (Connection conn = this.createConnection(true);
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, ts);
            stmt.setInt(1, snap_id);
            stmt.execute();
        } catch (Exception ex) {
        }
    }

    public List<AlertSubscribers.Subscription> loadAlertSubscriptions() throws SQLException {
        List<AlertSubscribers.Subscription> subscribers = new ArrayList<>();
        String sql = "select * from ALERT_SUBSCRIPT";
        try (Connection conn = this.createConnection(true);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs != null && rs.next()) {
                AlertSubscribers.Subscription sub = new AlertSubscribers.Subscription();
                sub.group = rs.getString("DBGROUP");
                sub.host = rs.getString("HOSTNAME");
                sub.alertName = rs.getString("ALERT_NAME");
                sub.setParams(rs.getString("PARAMS"));
                subscribers.add(sub);
            }
        }
        return subscribers;
    }

    public boolean upsertAlertSubscription(AlertSubscribers.Subscription sub) {
        String sql2 = "update " + ALERT_SUBSCRIPT + " set PARAMS=? where DBGROUP=? and HOSTNAME=? AND ALERT_NAME=?";
        String sql3 = "insert into " + ALERT_SUBSCRIPT + " (DBGROUP,HOSTNAME,ALERT_NAME,PARAMS) values(?,?,?,?)";
        log.info("Store or update alert sub " + sub.alertName + "for db " + sub.group + ", " + sub.host);
        boolean findOne = false;
        try (Connection conn = this.createConnection(true)) {
            try {
                try (PreparedStatement pstmt = conn.prepareStatement("select ID from " + ALERT_SUBSCRIPT + " where DBGROUP=? and HOSTNAME=? and ALERT_NAME=?")) {
                    pstmt.setString(1, sub.group);
                    if (sub.host != null && !sub.host.isEmpty())
                        pstmt.setString(2, sub.host);
                    else
                        pstmt.setNull(2, Types.VARCHAR);
                    pstmt.setString(3, sub.alertName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs != null && rs.next()) {
                            findOne = true;
                        }
                    }
                }
                if (findOne) {
                    try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                        String s = sub.paramToJSON();
                        if (s != null)
                            pstmt.setString(1, sub.paramToJSON());
                        else
                            pstmt.setNull(1, Types.VARCHAR);
                        pstmt.setString(2, sub.group);
                        if (sub.host != null && !sub.host.isEmpty())
                            pstmt.setString(3, sub.host);
                        else
                            pstmt.setNull(3, Types.VARCHAR);
                        pstmt.setString(4, sub.alertName);
                        pstmt.execute();
                    }
                } else {
                    try (PreparedStatement pstmt = conn.prepareStatement(sql3)) {
                        pstmt.setString(1, sub.group);
                        if (sub.host != null && !sub.host.isEmpty())
                            pstmt.setString(2, sub.host);
                        else
                            pstmt.setNull(2, Types.VARCHAR);
                        pstmt.setString(3, sub.alertName);
                        String s = sub.paramToJSON();
                        if (s != null)
                            pstmt.setString(4, sub.paramToJSON());
                        else
                            pstmt.setNull(4, Types.VARCHAR);
                        pstmt.execute();
                    }
                }
                return true;
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to save alert sub " + sub.alertName + " for " + sub.group + ", " + sub.host);
            log.error("Exception", ex);
        }
        return false;
    }

    public boolean deleteAlertSubscription(AlertSubscribers.Subscription sub) {
        String sql2 = "delete from " + ALERT_SUBSCRIPT + " where DBGROUP=? and HOSTNAME=? AND ALERT_NAME=?";
        log.info("Delete alert sub " + sub.alertName + "for db " + sub.group + ", " + sub.host);
        try (Connection conn = this.createConnection(true)) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                pstmt.setString(1, sub.group);
                if (sub.host != null && !sub.host.isEmpty())
                    pstmt.setString(2, sub.host);
                else
                    pstmt.setNull(2, Types.VARCHAR);
                pstmt.setString(3, sub.alertName);
                pstmt.execute();
                return true;
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to delete alert sub " + sub.alertName + " for " + sub.group + ", " + sub.host);
            log.error("Exception", ex);
        }
        return false;
    }

    public List<MetricsSubscribers.Subscription> loadMetricsSubscriptions() throws SQLException {
        List<MetricsSubscribers.Subscription> subscribers = new ArrayList<>();
        String sql = "select * from " + METRICS_SUBSCRIPT;
        try (Connection conn = this.createConnection(true);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs != null && rs.next()) {
                MetricsSubscribers.Subscription sub = new MetricsSubscribers.Subscription();
                sub.group = rs.getString("DBGROUP");
                sub.host = rs.getString("HOSTNAME");
                sub.mGroup = rs.getString("MGROUP");
                sub.mSubGroup = rs.getString("MSUBGROUP");
                subscribers.add(sub);
            }
        }
        return subscribers;
    }

    public boolean addMetricsSubscription(MetricsSubscribers.Subscription sub) {
        String sql3 = "insert into " + METRICS_SUBSCRIPT + " (DBGROUP,HOSTNAME,MGROUP,MSUBGROUP) values(?,?,?,?)";
        log.info("Store metricst sub " + sub.mGroup + ", " + sub.mSubGroup + " for db " + sub.group + ", " + sub.host);
        boolean findOne = false;
        try (Connection conn = this.createConnection(true)) {
            try {
                try (PreparedStatement pstmt = conn.prepareStatement("select ID from " + METRICS_SUBSCRIPT + " where DBGROUP=? and HOSTNAME=? and MGROUP=? and MSUBGROUP=?")) {
                    pstmt.setString(1, sub.group);
                    if (sub.host != null && !sub.host.isEmpty())
                        pstmt.setString(2, sub.host);
                    else
                        pstmt.setNull(2, Types.VARCHAR);
                    pstmt.setString(3, sub.mGroup);
                    if (sub.mSubGroup != null && !sub.mSubGroup.isEmpty())
                        pstmt.setString(4, sub.mSubGroup);
                    else
                        pstmt.setNull(4, Types.VARCHAR);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs != null && rs.next()) {
                            findOne = true;
                        }
                    }
                }
                if (!findOne) {
                    try (PreparedStatement pstmt = conn.prepareStatement(sql3)) {
                        pstmt.setString(1, sub.group);
                        if (sub.host != null && !sub.host.isEmpty())
                            pstmt.setString(2, sub.host);
                        else
                            pstmt.setNull(2, Types.VARCHAR);
                        pstmt.setString(3, sub.mGroup);
                        if (sub.mSubGroup != null)
                            pstmt.setString(4, sub.mSubGroup);
                        else
                            pstmt.setNull(4, Types.VARCHAR);
                        pstmt.execute();
                    }
                }
                return true;
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to save metrics sub " + sub.mGroup + ", " + sub.mSubGroup + " for " + sub.group + ", " + sub.host);
            log.error("Exception", ex);
        }
        return false;
    }

    public boolean deleteMetricsSubscription(MetricsSubscribers.Subscription sub) {
        String sql2 = "delete from " + METRICS_SUBSCRIPT + " where DBGROUP=? and HOSTNAME=? AND MGROUP=? AND MSUBGROUP=?";
        log.info("Delete metrics sub " + sub.mGroup + ", " + sub.mSubGroup + " for db " + sub.group + ", " + sub.host);
        try (Connection conn = this.createConnection(true)) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                pstmt.setString(1, sub.group);
                if (sub.host != null && !sub.host.isEmpty())
                    pstmt.setString(2, sub.host);
                else
                    pstmt.setNull(2, Types.VARCHAR);
                pstmt.setString(3, sub.mGroup);
                if (sub.mSubGroup != null)
                    pstmt.setString(4, sub.mSubGroup);
                else
                    pstmt.setNull(4, Types.VARCHAR);
                pstmt.execute();
                return true;
            } catch (Exception transactionException) {
                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }
                throw transactionException;
            }
        } catch (Exception ex) {
            log.info("Failed to delete alert sub " + sub.mGroup + ", " + sub.mSubGroup + " for " + sub.group + ", " + sub.host);
            log.error("Exception", ex);
        }
        return false;
    }
}
