/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.db;

import com.yahoo.dba.perf.myperf.common.ColumnDescriptor;
import com.yahoo.dba.perf.myperf.common.ColumnInfo;
import com.yahoo.dba.perf.myperf.common.ResultList;
import com.yahoo.dba.perf.myperf.common.ResultRow;

import java.util.List;
import java.util.TreeMap;

/**
 * 表示inno db mutex post proccessor相关的类型。
 */
public class InnoDbMutexPostProccessor implements PostQueryResultProcessor {
    /**
     * 表示mutex name相关的类型。
     */
    static class MutexName {
        /**
         * 保存type信息。
         */
        String type;
        /**
         * 保存name信息。
         */
        String name;
        /**
         * 保存wait type信息。
         */
        String waitType;
        /**
         * 保存count信息。
         */
        int count = 0;

        /**
         * 保存val信息。
         */
        int val = 0;

        /**
         * 创建MutexName对象。
         *
         * @param type     type参数。
         * @param name     name参数。
         * @param waitType wait type参数。
         */
        MutexName(String type, String name, String waitType) {
            this.type = type;
            this.name = name;
            this.waitType = waitType;
        }
    }

    /**
     * 执行process操作。
     *
     * @param rs rs参数。
     * @return 处理结果。
     */
    @Override
    public ResultList process(ResultList rs) {
        TreeMap<String, MutexName> mutexMetrics = new TreeMap<>();

        if (rs != null && rs.getRows().size() > 0) {
            int typeIdx = 0;
            int nameIdx = 1;
            int statusIdx = 2;
            List<ColumnInfo> colList = rs.getColumnDescriptor().getColumns();

            for (int i = 0; i < colList.size(); i++)
                if ("Type".equalsIgnoreCase(colList.get(i).getName()))
                    typeIdx = i;
                else if ("Name".equalsIgnoreCase(colList.get(i).getName()))
                    nameIdx = i;
                else if ("Status".equalsIgnoreCase(colList.get(i).getName()))
                    statusIdx = i;

            for (ResultRow row : rs.getRows())
                try {
                    List<String> sList = row.getColumns();
                    String type = sList.get(typeIdx);
                    String name = sList.get(nameIdx);
                    String status = sList.get(statusIdx);
                    //split status to name value pair
                    String[] nv = status.split("=");
                    String key = type + "|" + name + "|" + nv[0];
                    int val = Integer.parseInt(nv[1]);

                    if (!mutexMetrics.containsKey(key))
                        mutexMetrics.put(key, new MutexName(type, name, nv[0]));

                    mutexMetrics.get(key).val += val;
                    mutexMetrics.get(key).count++;
                } catch (Exception ignored) {
                }

        }
        //now build new List
        ResultList rlist = new ResultList();
        ColumnDescriptor desc = new ColumnDescriptor();
        desc.addColumn("NAME", false, 0);
        desc.addColumn("VALUE", true, 1);
        desc.addColumn("COUNT", true, 2);
        rlist.setColumnDescriptor(desc);

        for (MutexName m : mutexMetrics.values()) {
            ResultRow row = new ResultRow();
            row.setColumnDescriptor(desc);
            row.addColumn(m.type + "/" + m.name + "/" + m.waitType);
            row.addColumn(String.valueOf(m.val));
            row.addColumn(String.valueOf(m.count));
            rlist.addRow(row);
        }

        return rlist;
    }
}
