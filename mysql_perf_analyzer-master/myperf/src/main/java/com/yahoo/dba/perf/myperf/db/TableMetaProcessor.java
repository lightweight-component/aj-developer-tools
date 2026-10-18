/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.db;

import com.yahoo.dba.perf.myperf.common.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Map;

/**
 * 表示table meta processor相关的类型。
 */
@Slf4j
public class TableMetaProcessor implements CustomQueryProcessor {
    /**
     * 定义tbl queries常量。
     */
    private final static String[] TBL_QUERIES = new String[]{"mysql_meta_table_constraints", "mysql_meta_table_triggers",
            "mysql_meta_table_create", "mysql_meta_table_indexes",
            "mysql_meta_innodb_index_stats", "mysql_meta_innodb_table_stats",
            "mysql_meta_table_stats", "mysql_meta_table_columns"};

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
                              DBConnectionWrapper connWrapper, QueryParameters qps, Map<String, ResultList> rListMap)
            throws SQLException {

        for (String tblQuery : TBL_QUERIES) {
            QueryParameters qps2 = new QueryParameters();
            qps2.setSql(tblQuery);
            qps2.getSqlParams().put("p_1", qps.getSqlParams().get("p_1"));
            qps2.getSqlParams().put("p_2", qps.getSqlParams().get("p_2"));
            ResultList rList = null;

            try {
                rList = context.getQueryEngine().executeQueryGeneric(qps2, connWrapper, qps.getMaxRows());
            } catch (Throwable th) {
                log.warn("Error when retrieve meta data", th);

                if (th instanceof SQLException) {
                    //check if the connection is still good
                    if (!DBUtils.checkConnection(connWrapper.getConnection()))
                        throw (SQLException) th;
                }

                Sql sql = context.getSqlManager().getSql(qps2.getSql());

                if (sql != null && sql.isErrorInline()) {
                    rList = new ResultList();
                    ColumnDescriptor desc = new ColumnDescriptor();
                    desc.addColumn("Status", false, 0);
                    desc.addColumn("Message", false, 1);
                    rList.setColumnDescriptor(desc);
                    ResultRow row = new ResultRow();
                    row.setColumnDescriptor(desc);
                    row.addColumn("Error");
                    row.addColumn(th.getMessage());
                    rList.addRow(row);
                }
            }

            rListMap.put(tblQuery, rList);
        }

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
    public ResultList querySingle(MyPerfContext context, DBInstanceInfo dbinfo, String appUser, DBConnectionWrapper connWrapper, QueryParameters qps) {
        throw new RuntimeException("Not implmented");
    }

    /**
     * 判断multiple。
     *
     * @return 是否满足条件。
     */
    @Override
    public boolean isMultiple() {
        return true;
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
