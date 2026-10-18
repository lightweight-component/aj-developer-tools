/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示metrics result object相关的类型。
 */
public class MetricsResultObject implements CustomResultObject {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 保存metrics集合。
     */
    @Getter
    @Setter
    private List<Metric> metrics;

    /**
     * 获取name。
     *
     * @return 处理结果。
     */
    @Override
    public String getName() {
        return "metrics";//fixed the name
    }

    /**
     * 获取value json string。
     *
     * @return 处理结果。
     */
    @Override
    public String getValueJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        if (this.metrics != null) {
            boolean first = true;

            for (Metric m : this.metrics) {

                if (!first)
                    sb.append(",");
                else
                    first = false;

                sb.append("\"").append(m.getName()).append("\"");
                sb.append(":{");
                sb.append("\"shortName\":\"").append(m.getShortName()).append("\"");
                sb.append(",\"inc\":").append(m.isIncremental() ? "1" : "0");
                sb.append(",\"unit\":\"").append(m.getMetricUnit()).append("\"");

                if (m.getAverageTimeUnit() != null)
                    sb.append(",\"avg\":\"").append(m.getAverageTimeUnit()).append("\"");

                if (m.getAdjustment() != null)
                    sb.append(",\"adj\":").append(m.getAdjustment());

                if (m.getChartDisplayUnit() != null)
                    sb.append(",\"display\":\"").append(m.getChartDisplayUnit()).append("\"");

                if (m.getDescription() != null)
                    sb.append(",\"description\":\"").append(m.getDescription()).append("\"");

                sb.append("}\r\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    //will prefix metricsGroupname

    /**
     * 设置metrics。
     *
     * @param metrics          metrics参数。
     * @param metricsGroupName metrics group name参数。
     */
    public void setMetrics(List<Metric> metrics, String metricsGroupName) {

        if (metrics != null && metricsGroupName != null && !metricsGroupName.isEmpty() && !"globalstatus".equals(metricsGroupName))
            for (Metric m : metrics)
                m.setName(metricsGroupName + "." + m.getName());


        this.metrics = metrics;
    }

    /**
     * 添加metric。
     *
     * @param m m参数。
     */
    public void addMetric(Metric m) {

        if (this.metrics == null)
            this.metrics = new ArrayList<>();

        this.metrics.add(m);
    }
}
