package com.yahoo.dba.perf.myperf.db;

import com.yahoo.dba.perf.myperf.common.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Map;

/**
 * 表示my sqlstatus query processor相关的类型。
 */
@Slf4j
public class MySQLStatusQueryProcessor implements CustomQueryProcessor {

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
        qps2.setSql("mysql_global_status_metrics");

        for (String key : qps.getSqlParams().keySet())
            qps2.getSqlParams().put(key, qps.getSqlParams().get(key));

        if (!qps2.getSqlParams().containsKey("p_1"))
            qps2.getSqlParams().put("p_1", "");

        //mysql_global_status_metrics
        //mysql_show_global_status
        ResultList rList;
        rList = context.getQueryEngine().executeQueryGeneric(qps2, connWrapper, qps.getMaxRows());

        if (rList != null) {

            ResultList newList = new ResultList();
            ColumnDescriptor desc = rList.getColumnDescriptor();
            ColumnDescriptor newDesc = new ColumnDescriptor();
            int idx = 0;
            int nameIdx = 0;

            for (ColumnInfo c : desc.getColumns()) {

                if ("VARIABLE_NAME".equalsIgnoreCase(c.getName()))
                    nameIdx = idx;

                if ("VALUE".equalsIgnoreCase(c.getName()))
                    newDesc.addColumn("VARIABLE_VALUE", c.isNumberType(), idx++);
                else
                    newDesc.addColumn(c.getName().toUpperCase(), c.isNumberType(), idx++);
            }

            newList.setColumnDescriptor(newDesc);

            for (ResultRow row : rList.getRows()) {
                ResultRow newRow = new ResultRow();
                newRow.setColumnDescriptor(newDesc);
                int cols = row.getColumns().size();

                for (int i = 0; i < cols; i++) {
                    String v = row.getColumns().get(i);

                    if (i == nameIdx)
                        newRow.addColumn(v == null ? null : v.toUpperCase());
                    else newRow.addColumn(v);
                }

                newList.addRow(newRow);
            }

            return newList;
        }

        return null;
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
