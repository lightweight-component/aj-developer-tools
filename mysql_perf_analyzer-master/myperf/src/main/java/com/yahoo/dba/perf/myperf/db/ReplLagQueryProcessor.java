/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.db;

import com.yahoo.dba.perf.myperf.common.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Query slave status and use master info to add master status
 *
 * @author xrao
 */
@Slf4j
public class ReplLagQueryProcessor implements CustomQueryProcessor {
    /**
     * 保存slave lag entries信息。
     */
    private static String[] SLAVE_LAG_ENTRIES = new String[]{"Seconds_Behind_Master", "Slave_IO_Running",
            "Slave_SQL_Running", "Slave_IO_State"};
    /**
     * 保存slave pos entries信息。
     */
    private static String[] SLAVE_POS_ENTRIES = new String[]{"Master_Log_File", "Read_Master_Log_Pos",
            "Relay_Master_Log_File", "Exec_Master_Log_Pos", "Relay_Log_File", "Relay_Log_Pos"};

    /**
     * 查询multiple。
     *
     * @param context     context参数。
     * @param dbinfo      dbinfo参数。
     * @param appUser     app user参数。
     * @param connWrapper conn wrapper参数。
     * @param qps         qps参数。
     * @param rListMap    r list map参数。
     */
    @Override
    public void queryMultiple(MyPerfContext context, DBInstanceInfo dbinfo, String appUser,
                              DBConnectionWrapper connWrapper, QueryParameters qps,
                              Map<String, ResultList> rListMap) throws SQLException {
        throw new RuntimeException("Not implmented");

    }

    /**
     * 查询single。
     *
     * @param context     context参数。
     * @param dbinfo      dbinfo参数。
     * @param appUser     app user参数。
     * @param connWrapper conn wrapper参数。
     * @param qps         qps参数。
     * @return 处理结果。
     */
    @Override
    public ResultList querySingle(MyPerfContext context, DBInstanceInfo dbinfo, String appUser,
                                  DBConnectionWrapper connWrapper, QueryParameters qps)
            throws SQLException {
        QueryParameters qps2 = new QueryParameters();
        qps2.setSql("mysql_repl_slave");
        ResultList rList = null;
        rList = context.getQueryEngine().executeQueryGeneric(qps2, connWrapper, qps.getMaxRows());
        String master = null;
        String port = null;

        if (rList != null) {
            LinkedHashMap<String, String> kvPairs = new LinkedHashMap<>(rList.getRows().size());

            for (ResultRow row : rList.getRows()) {

                if (row.getColumns() == null || row.getColumns().size() < 2)
                    continue;

                kvPairs.put(row.getColumns().get(0), row.getColumns().get(1));
            }

            master = kvPairs.get("Master_Host");
            port = kvPairs.get("Master_Port");

            if (master != null && !master.isEmpty() && port != null && !port.isEmpty()) {
                log.info("Query master status from (" + master + ":" + port + ")");
                DBInstanceInfo dbinfo2 = new DBInstanceInfo();
                dbinfo2.setHostName(master);
                dbinfo2.setPort(port);
                dbinfo2.setDatabaseName("information_schema");
                dbinfo2.setDbType("mysql");
                String url = dbinfo2.getConnectionString();
                DBCredential cred = null;

                if (appUser != null)
                    try {
                        //first, check if the user has his own credential
                        cred = context.getMetaDb().retrieveDBCredential(appUser, dbinfo.getDbGroupName());

                        if (cred != null)
                            try {
                                DriverManager.setLoginTimeout(60);
                                try (Connection conn = DriverManager.getConnection(url, cred.getUsername(), cred.getPassword())) {

                                    if (conn != null)
                                        try (Statement stmt = conn.createStatement();
                                             ResultSet rs = stmt.executeQuery("show master status")) {
                                            //TODO rearrange order
                                            ResultList rList2 = new ResultList();
                                            rList2.setColumnDescriptor(rList.getColumnDescriptor());

                                            for (String e : SLAVE_LAG_ENTRIES) {
                                                ResultRow row = new ResultRow();
                                                row.setColumnDescriptor(rList.getColumnDescriptor());
                                                row.addColumn(e);
                                                row.addColumn(kvPairs.get(e));
                                                rList2.addRow(row);
                                                kvPairs.remove(e);
                                            }

                                            while (rs != null && rs.next()) {
                                                ResultRow row = new ResultRow();
                                                row.setColumnDescriptor(rList.getColumnDescriptor());
                                                row.addColumn("Master: File");
                                                row.addColumn(rs.getString("File"));
                                                rList2.addRow(row);
                                                row = new ResultRow();
                                                row.setColumnDescriptor(rList.getColumnDescriptor());
                                                row.addColumn("Master: Position");
                                                row.addColumn(rs.getString("Position"));
                                                rList2.addRow(row);
                                                //TODO gtid set
                                            }
                                            //now add slave position info
                                            for (String e : SLAVE_POS_ENTRIES) {
                                                ResultRow row = new ResultRow();
                                                row.setColumnDescriptor(rList.getColumnDescriptor());
                                                row.addColumn(e);
                                                row.addColumn(kvPairs.get(e));
                                                rList2.addRow(row);
                                                kvPairs.remove(e);
                                            }
                                            //now add remaining entries
                                            for (Map.Entry<String, String> e : kvPairs.entrySet()) {
                                                ResultRow row = new ResultRow();
                                                row.setColumnDescriptor(rList.getColumnDescriptor());
                                                row.addColumn(e.getKey());
                                                row.addColumn(e.getValue());
                                                rList2.addRow(row);
                                            }

                                            return rList2;
                                        }

                                }
                            } catch (Exception ex) {
                            }

                    } catch (Exception ex) {
                    }

            }
        }

        return rList;
    }

    /**
     * 判断multiple。
     *
     * @return 是否满足条件。
     */
    @Override
    public boolean isMultiple() {
        return false;
    }

    /**
     * 执行require dbconnection操作。
     *
     * @return 是否满足条件。
     */
    @Override
    public boolean requireDBConnection() {
        return true;
    }
}
