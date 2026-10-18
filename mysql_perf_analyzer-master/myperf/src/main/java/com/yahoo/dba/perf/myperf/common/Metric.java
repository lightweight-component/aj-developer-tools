/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

/**
 * Define a single metric inside a MetricsGfroup
 *
 * @author xrao
 */
public class Metric {
    /**
     * 保存name信息。
     */
    @Setter
    @Getter
    private String name;//metric name
    /**
     * 保存source name信息。
     */
    @Getter
    private String sourceName;//data source name, either a column name from a result set or like
    //variable_name in MySQL global status.
    /**
     * 保存alt name信息。
     */
    @Getter
    @Setter
    private String altName;//maybe a different name in new version
    /**
     * 保存description信息。
     */
    @Getter
    private String description;//metric description, try not use special chars
    /**
     * 保存metric unit信息。
     */
    @Getter
    private String metricUnit = "";//metric unit, such as BYTES, etc.
    /**
     * 保存data type信息。
     */
    @Getter
    private MetricDataType dataType = MetricDataType.INT;//metric data type, support byte, short, int, long, float and double
    /**
     * 标记incremental状态。
     */
    @Getter
    @Setter
    private boolean incremental = false; //whethee this metric is accumulated
    /**
     * 保存average time unit信息。
     */
    @Getter
    @Setter
    private String averageTimeUnit = "min"; //sec, min, hour, only applied to incremental type, default to min
    /**
     * 保存adjustment信息。
     */
    @Getter
    @Setter
    private String adjustment = "1";//should be floating number if present, for example, snmp raw cpu is in tick (1/100 sec), so we need change it to sec with 0.01 multiplier
    /**
     * 保存chart display unit信息。
     */
    @Setter
    private String chartDisplayUnit = "/MIN";//Display unit after adjustment and average, for example, BYTES/SEC, CPUSEC/SEC

    /**
     * 保存short name信息。
     */
    @Getter
    @Setter
    private String shortName; //short name used when return dataset, not metrics definition. Generated at runtime.

    /**
     * 创建Metric对象。
     *
     * @param name        name参数。
     * @param sourceName  source name参数。
     * @param dataType    data type参数。
     * @param metricUnit  metric unit参数。
     * @param description description参数。
     */
    public Metric(String name, String sourceName, MetricDataType dataType, String metricUnit, String description) {
        this.name = name;
        this.sourceName = sourceName;

        if (dataType != null)
            this.dataType = dataType;

        if (metricUnit != null)
            this.metricUnit = metricUnit;

        this.description = description;
        this.incremental = true;
    }

    /**
     * 创建Metric对象。
     *
     * @param name        name参数。
     * @param sourceName  source name参数。
     * @param dataType    data type参数。
     * @param metricUnit  metric unit参数。
     * @param description description参数。
     * @param incremental incremental参数。
     */
    public Metric(String name, String sourceName, MetricDataType dataType, String metricUnit, String description, boolean incremental) {
        this.name = name;
        this.sourceName = sourceName;

        if (dataType != null)
            this.dataType = dataType;

        if (metricUnit != null)
            this.metricUnit = metricUnit;

        this.description = description;
        this.incremental = incremental;
    }

    /**
     * 获取chart display unit。
     *
     * @return 处理结果。
     */
    public String getChartDisplayUnit() {

        if (this.chartDisplayUnit == null)
            return this.metricUnit;

        return chartDisplayUnit;
    }

    /**
     * 执行copy操作。
     *
     * @return 处理结果。
     */
    public Metric copy() {
        Metric m = new Metric(this.name, this.sourceName, this.dataType, this.metricUnit, this.description, this.incremental);
        m.setAdjustment(this.adjustment);
        m.setAltName(altName);
        m.setChartDisplayUnit(chartDisplayUnit);
        m.setAverageTimeUnit(averageTimeUnit);
        m.setShortName(shortName);
        return m;
    }

    //convert string rep to enum, default to INT

    /**
     * 执行str to metric data type操作。
     *
     * @param str str参数。
     * @return 处理结果。
     */
    public static MetricDataType strToMetricDataType(String str) {

        if ("byte".equalsIgnoreCase(str))
            return MetricDataType.BYTE;

        if ("short".equalsIgnoreCase(str))
            return MetricDataType.SHORT;

        if ("int".equalsIgnoreCase(str))
            return MetricDataType.INT;

        if ("long".equalsIgnoreCase(str))
            return MetricDataType.LONG;

        if ("float".equalsIgnoreCase(str))
            return MetricDataType.FLOAT;

        if ("double".equalsIgnoreCase(str))
            return MetricDataType.DOUBLE;

        return MetricDataType.INT;
    }

    /**
     * 判断alpha numeric underscore not null。
     *
     * @param str str参数。
     * @param len len参数。
     * @return 是否满足条件。
     */
    public static boolean isAlphaNumericUnderscoreNotNull(String str, int len) {

        if (str == null || str.isEmpty())
            return false;

        if (len > 0 && str.length() > len)
            return false;

        char[] chars = str.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_')
                continue;

            return false;
        }

        return true;
    }
}
