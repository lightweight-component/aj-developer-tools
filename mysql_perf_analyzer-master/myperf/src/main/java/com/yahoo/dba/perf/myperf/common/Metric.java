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
    @Setter
    @Getter
    private String name;//metric name
    @Getter
    private String sourceName;//data source name, either a column name from a result set or like
    //variable_name in MySQL global status.
    @Getter
    @Setter
    private String altName;//maybe a different name in new version
    @Getter
    private String description;//metric description, try not use special chars
    @Getter
    private String metricUnit = "";//metric unit, such as BYTES, etc.
    @Getter
    private MetricDataType dataType = MetricDataType.INT;//metric data type, support byte, short, int, long, float and double
    @Getter
    @Setter
    private boolean incremental = false; //whethee this metric is accumulated
    @Getter
    @Setter
    private String averageTimeUnit = "min"; //sec, min, hour, only applied to incremental type, default to min
    @Getter
    @Setter
    private String adjustment = "1";//should be floating number if present, for example, snmp raw cpu is in tick (1/100 sec), so we need change it to sec with 0.01 multiplier
    @Setter
    private String chartDisplayUnit = "/MIN";//Display unit after adjustment and average, for example, BYTES/SEC, CPUSEC/SEC

    @Getter
    @Setter
    private String shortName; //short name used when return dataset, not metrics definition. Generated at runtime.

    public Metric(String name, String sourceName, MetricDataType dataType, String metricUnit, String description) {
        this.name = name;
        this.sourceName = sourceName;
        if (dataType != null) this.dataType = dataType;
        if (metricUnit != null) this.metricUnit = metricUnit;
        this.description = description;
        this.incremental = true;
    }

    public Metric(String name, String sourceName, MetricDataType dataType, String metricUnit, String description, boolean incremental) {
        this.name = name;
        this.sourceName = sourceName;
        if (dataType != null) this.dataType = dataType;
        if (metricUnit != null) this.metricUnit = metricUnit;
        this.description = description;
        this.incremental = incremental;
    }

    public String getChartDisplayUnit() {
        if (this.chartDisplayUnit == null)
            return this.metricUnit;
        return chartDisplayUnit;
    }

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

    public static boolean isAlphaNumericUnderscoreNotNull(String str, int len) {
        if (str == null || str.isEmpty()) return false;
        if (len > 0 && str.length() > len) return false;
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
