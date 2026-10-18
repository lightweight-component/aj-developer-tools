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

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * This will be all the connections held by a specific user.
 *
 * @author xrao
 */
@Slf4j
public class UserDBConnections {
    /**
     * 保存app user信息。
     */
    @Getter
    @Setter
    private String appUser;//application user name
    /**
     * 保存connections集合。
     */
    private List<DBConnectionWrapper> connections = new ArrayList<>();
    /**
     * 标记valid状态。
     */
    @Getter
    @Setter
    private volatile boolean valid = true;  //if this is still valid

    /**
     * 保存connection idle timeout信息。
     */
    @Getter
    @Setter
    private long connectionIdleTimeout = 600000L;

    /**
     * 保存framework context信息。
     */
    @Getter
    @Setter
    private MyPerfContext frameworkContext;

    /**
     * 创建UserDBConnections对象。
     */
    public UserDBConnections() {
    }

    /**
     * try to get a free connection not used by other session.
     *
     * @param dbGroupName
     * @param hostname
     * @return
     */
    synchronized public DBConnectionWrapper checkoutConnection(String dbGroupName, String hostname) {
        for (DBConnectionWrapper conn : this.connections)
            if (dbGroupName.equalsIgnoreCase(conn.getDb().getDbGroupName())
                    && hostname.equalsIgnoreCase(conn.getDb().getHostName()) && !conn.isInuse()) {
                conn.setInuse(true);
                return conn;
            }

        return null;
    }

    /**
     * Note: for oracle only
     *
     * @param dbname
     * @param tgtInstance
     * @return
     */
    public DBConnectionWrapper checkoutConnection(String dbname, int tgtInstance) {
        synchronized (this) {
            for (DBConnectionWrapper conn : this.connections)
                if (dbname.equalsIgnoreCase(conn.getDb().getDbGroupName()) && conn.getInstance_number() == tgtInstance) {
                    conn.setInuse(true);
                    return conn;
                }

            //if we reach here without a credential, stop here
            return null;
        }
    }

    /**
     * Because it is possible to wait for too long to create a new connection, we relax this method from
     * synchronized and move the synchronized to required blocks.
     *
     * @param dbinfo
     * @param cred
     * @return
     */
    public DBConnectionWrapper checkoutConnection(DBInstanceInfo dbinfo, DBCredential cred) {
        synchronized (this) {

            for (DBConnectionWrapper conn : this.connections)
                if (dbinfo.getDbGroupName().equalsIgnoreCase(conn.getDb().getDbGroupName())
                        && dbinfo.getHostName().equalsIgnoreCase(conn.getDb().getHostName()) && !conn.isInuse()) {
                    conn.setInuse(true);
                    return conn;
                }

            //if we reach here without credential, stop here
            if (cred == null)
                return null;
        }

        //when reach here, we have no db connection available, need create one
        //Because it could take too long to create a new connection to one db, it is better not to
        //block other connections.
        if (cred != null && cred.getPassword() == null)
            throw new RuntimeException("No credential provided fro DB " + dbinfo.getDbGroupName());

        try {
            return createConnectionInternal(dbinfo, cred);
        } catch (Exception ex) {

            if (ex.getCause() != null) {
                Throwable cause = ex.getCause();
                String msg = cause.getMessage();
                //suppress log messages for certain error
                if (cause instanceof UnknownHostException
                        || (cause instanceof ConnectException && msg != null && msg.indexOf("Connection refused") >= 0)) {
                    log.error("Exception cause when connecting to (" + dbinfo + "): msg, no retry.");
                    throw new RuntimeException(Constants.CONN_MSG_NORETRY);
                } else
                    log.info("Ex: " + ex.getCause().getClass().getName() + ", " + ex.getCause().getMessage());
            }

            log.error("Exception when connecting to (" + dbinfo + ")", ex);
            throw new RuntimeException(ex.getMessage());
        }
    }

    /**
     * For Oracle
     * When a target DB is not directly accessible, try to use gv view to access through another instance. Find first one available
     *
     * @param cluster
     * @param cred
     * @return
     */
    public DBConnectionWrapper checkoutConnection(DBGroupInfo cluster, DBCredential cred) {
        synchronized (this) {

            for (DBConnectionWrapper conn : this.connections)
                if (cluster.getGroupName().equalsIgnoreCase(conn.getDb().getDbGroupName()) && !conn.isInuse()) {
                    conn.setInuse(true);
                    return conn;
                }

            //if we reach here without credential, stop here
            if (cred == null)
                return null;
        }

        //when reach here, we have no db connection available, need to create one
        //Because it could take too long to create a new connection to one db, it is better not to
        //block other connections.
        if (cred.getPassword() == null)
            throw new RuntimeException("No credential provided fro DB group " + cluster.getGroupName());

        //now let's try the cluster one by one
        for (DBInstanceInfo dbinfo : cluster.getInstances())
            try {

                if (!dbinfo.isConnectionVerified()) {
                    log.info("Skip host which is not connection verified yet: " + dbinfo.getHostName());
                    continue;
                }

                return createConnectionInternal(dbinfo, cred);
            } catch (Exception ex) {
                log.error("Exception when connecting to (" + dbinfo + ")", ex);
                throw new RuntimeException(ex.getMessage());
            }

        return null;
    }

    /**
     * 创建connection internal。
     *
     * @param dbinfo dbinfo参数。
     * @param cred   cred参数。
     * @return 处理结果。
     */
    private DBConnectionWrapper createConnectionInternal(DBInstanceInfo dbinfo, DBCredential cred)
            throws SQLException {
        log.debug("Connecting to " + dbinfo.getConnectionString() + " using user " + cred.getUsername());
        Connection conn = ConnectionFactory.connect(dbinfo, cred, frameworkContext);

        if (conn == null)
            throw new SQLException("Failed to create connection: null connection");

        conn.setReadOnly(true);//TODO right now, we only allow readonly connection
        //wrap it
        DBConnectionWrapper cw = new DBConnectionWrapper(dbinfo);
        cw.setAppUser(this.appUser);
        cw.setConnection(conn);
        cw.setInuse(true);
        //since we already have the connection, now store it back
        synchronized (this) {
            this.connections.add(cw);
        }
        return cw;
    }

    /**
     * Well, the only thing we need do is to set inuse as false
     *
     * @param conn
     */
    public void checkinConnection(DBConnectionWrapper conn) {

        if (conn == null)
            return;

        conn.setCurrentStatement(null);
        conn.setInuse(false);
    }

    /**
     * Well, we need close the connection and remove it
     *
     * @param conn
     */
    public void checkinConnectionOnError(DBConnectionWrapper conn) {
        conn.setCurrentStatement(null);
        conn.close();
        synchronized (this) {

            for (int i = 0; i < this.connections.size(); i++)
                if (conn == this.connections.get(i)) {
                    log.debug("Remove closed connection (" + conn.getAppUser() + "," + conn.getDb());
                    this.connections.remove(i);
                    break;
                }

        }
    }

    /**
     * Well, we need close the connection and remove it
     *
     * @param conn
     */
    public void checkinConnectionAndClose(DBConnectionWrapper conn) {
        conn.setCurrentStatement(null);
        conn.close();
        synchronized (this) {

            for (int i = 0; i < this.connections.size(); i++)
                if (conn == this.connections.get(i)) {
                    log.debug("Remove closed connection as requested (" + conn.getAppUser() + "," + conn.getDb());
                    this.connections.remove(i);
                    break;
                }

        }
    }

    /**
     * Close all connections
     */
    synchronized public void close() {
        this.valid = false;

        for (DBConnectionWrapper conn : this.connections) {
            log.debug("Closing connection to (" + conn.getAppUser() + ", " + conn.getDb() + ")");
            try {

                if (conn.isInuse() && conn.getCurrentStatement() != null) {
                    log.debug("Cancel pending query, connection  (" + conn.getAppUser() + ", " + conn.getDb() + ")");
                    conn.getCurrentStatement().cancel();
                }

            } catch (Exception ex) {
                log.error("Exception", ex);
            }
            conn.close();
            log.debug("Closed connection to (" + conn.getAppUser() + ", " + conn.getDb() + ")");
        }

        this.connections.clear();
    }

    /**
     * Close connection idled for timeMillis. 5 minutes or more
     *
     * @param timeMillis
     */
    public void closeExpired(long timeMillis) {

        if (timeMillis <= 0)
            timeMillis = this.connectionIdleTimeout;

        List<DBConnectionWrapper> idleList = new ArrayList<>();
        synchronized (this.connections) {

            for (int i = this.connections.size() - 1; i >= 0; i--) {
                DBConnectionWrapper conn = this.connections.get(i);

                if (!conn.isInuse() && conn.isExpired(timeMillis)) {
                    log.debug("Remove idle connection from pool (" + conn.getAppUser() + "," + conn.getDb() + "), created at " + conn.getCreateTime());
                    idleList.add(this.connections.remove(i));
                }
            }
        }

        for (DBConnectionWrapper conn : idleList) {
            conn.close();
            log.debug("Closed idle connection (" + conn.getAppUser() + "," + conn.getDb() + "), created at " + conn.getCreateTime());
        }
    }

    /**
     * 关闭idle。
     *
     * @param clusterName cluster name参数。
     * @param hostName    host name参数。
     */
    public void closeIdle(String clusterName, String hostName) {
        log.debug("Close idle connections (" + clusterName + "," + hostName + ")");
        this.valid = false;
        List<DBConnectionWrapper> idleConnections = new ArrayList<>();
        //remove from reuse
        synchronized (this) {

            for (int i = this.connections.size() - 1; i >= 0; i--) {
                DBConnectionWrapper conn = this.connections.get(i);

                if (!clusterName.equalsIgnoreCase(conn.getDb().getDbGroupName())
                        || !hostName.equalsIgnoreCase(conn.getDb().getHostName()) || conn.isInuse()) continue;

                try {
                    conn.setCurrentStatement(null);
                    log.debug("Remove connection from pool (" + conn.getAppUser() + "," + conn.getDb() + "), created at " + conn.getCreateTime());
                    idleConnections.add(this.connections.remove(i));
                } catch (Exception ex) {
                    log.error("Exception", ex);
                }
            }
        }
        for (DBConnectionWrapper conn : idleConnections) {
            log.debug("Closing connection to (" + conn.getAppUser() + ", " + conn.getDb() + "), created at " + conn.getCreateTime() + ", ignore inuse " + conn.isInuse());
            conn.close();
            log.debug("Closed connection to (" + conn.getAppUser() + ", " + conn.getDb() + "), created at " + conn.getCreateTime() + ", ignore inuse " + conn.isInuse());
        }
    }

    /**
     * 获取connection count。
     *
     * @return 处理结果。
     */
    synchronized public int getConnectionCount() {
        return this.connections.size();
    }

    /**
     * 执行retrieve connection map操作。
     *
     * @return 处理结果。
     */
    synchronized public TreeMap<String, Integer> retrieveConnectionMap() {
        TreeMap<String, Integer> connMap = new TreeMap<>();

        for (int i = this.connections.size() - 1; i >= 0; i--) {
            DBConnectionWrapper conn = this.connections.get(i);
            String dbname = conn.getDb().getDbGroupName();
            String hostname = conn.getDb().getHostName();
            String key = dbname + ":" + hostname;
            int connCnt = 0;

            if (connMap.containsKey(key))
                connCnt = connMap.get(key);

            connCnt++;
            connMap.put(key, connCnt);
        }

        return connMap;
    }

}
