/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compare two ResultRows based on keyList. For simplicity, the column descriptors for both rows should be provided
 *
 * @author xrao
 */
public class ResultRowKeyComparator implements Comparator<ResultRow>, Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 2243120460008043846L;
    /**
     * 保存key list集合。
     */
    private List<String> keyList;
    /**
     * 保存index1 list集合。
     */
    private List<Integer> index1List;
    /**
     * 保存index2 list集合。
     */
    private List<Integer> index2List;


    /**
     * 创建ResultRowKeyComparator对象。
     *
     * @param keyList key list参数。
     * @param descA   desc a参数。
     * @param descB   desc b参数。
     */
    public ResultRowKeyComparator(List<String> keyList, ColumnDescriptor descA, ColumnDescriptor descB) {
        this.keyList = keyList;
        this.index1List = new ArrayList<>(this.keyList.size());
        this.index2List = new ArrayList<>(this.keyList.size());

        for (int i = 0; i < keyList.size(); i++) {
            this.index1List.add(descA.getColumnIndex(keyList.get(i)));
            this.index2List.add(descB.getColumnIndex(keyList.get(i)));

        }
    }


    /**
     * 执行compare操作。
     *
     * @param o1 o1参数。
     * @param o2 o2参数。
     * @return 处理结果。
     */
    public int compare(ResultRow o1, ResultRow o2) {

        if (o1 == null && o2 == null)
            return 0;

        if (o1 == null)
            return -1;

        if (o2 == null)
            return 1;

        for (int i = 0; i < this.keyList.size(); i++) {
            String valA = this.index1List.get(i) >= 0 ? o1.getColumns().get(this.index1List.get(i)) : null;
            String valB = this.index2List.get(i) >= 0 ? o2.getColumns().get(this.index2List.get(i)) : null;

            if (valA == null && valB == null)
                return 0;

            if (valA == null)
                return -1;

            if (valB == null)
                return 1;

            int c = valA.trim().compareTo(valB.trim());

            if (c != 0)
                return c;
        }

        return 0;
    }

}
