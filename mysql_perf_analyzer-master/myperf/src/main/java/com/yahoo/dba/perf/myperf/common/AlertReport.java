/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;

public class AlertReport implements Serializable {
    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    private String dbGroupName;
    @Getter
    @Setter
    private String dbHostName;
    @Getter
    @Setter
    private long timestamp;//in yyyymmddhhmmss
    @Getter
    @Setter
    private String alertReason;
    @Getter
    @Setter
    private String alertValue;

    @Getter
    @Setter
    private ProcessListSummary processListSummary;
    @Getter
    @Setter
    private LinkedHashMap<String, String> replicationSummary;
    //user defined alerts, the key is alert name
    @Getter
    @Setter
    private ResultList userDefinedAlertData;

    @Getter
    @Setter
    private long reportTimestamp;

    @Getter
    @Setter
    private File rootPath;

    public AlertReport() {

    }

    public void saveAsText() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
        File dest ;
        try {
            String ts = sdf.format(new Date(this.timestamp));
            File dir = new File(this.rootPath, sdf2.format(new Date(this.timestamp)));

            if (!dir.exists())
                dir.mkdirs();

            dest = new File(dir, this.alertReason + "_" + this.dbGroupName + "_" + this.dbHostName + "_" + ts + ".txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(dest))) {
                pw.println("Report Time:        " + sdf.format(new Date(this.reportTimestamp)));
                pw.println("Detect Time:        " + ts);
                pw.println("DB Group:    " + this.dbGroupName);
                pw.println("DB Host:     " + this.dbHostName);
                pw.println("Alert Type:  " + this.alertReason);

                if (alertValue != null)
                    pw.println("Alert Value:  " + this.alertValue);

                pw.println("");

                if (this.processListSummary != null)
                    this.processListSummary.saveAsText(pw);

                if (this.replicationSummary != null && this.replicationSummary.size() > 0) {
                    pw.println();
                    pw.println("------SLAVE STATUS------");

                    for (Map.Entry<String, String> e : this.replicationSummary.entrySet())
                        pw.println(String.format("%40s: %s", e.getKey(), e.getValue()));

                }

                if (this.userDefinedAlertData != null)
                    printList(pw, this.userDefinedAlertData);

                pw.flush();
            }
        } catch (Exception ex) {

        }


    }

    public static void printList(PrintWriter pw, ResultList rList) {
        int colcnt = rList.getColumnDescriptor().getColumns().size();
        int[] colsize = new int[colcnt];

        for (int i = 0; i < colcnt; i++)
            colsize[i] = rList.getColumnDescriptor().getColumns().get(i).getName().length();

        for (ResultRow row : rList.getRows()) {
            List<String> cols = row.getColumns();

            for (int i = 0; i < cols.size(); i++)
                if (cols.get(i) != null && cols.get(i).length() > colsize[i])
                    colsize[i] = cols.get(i).length();

        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < colcnt; i++)
            sb.append(String.format("%" + colsize[i] + "s ", rList.getColumnDescriptor().getColumns().get(i).getName()));

        pw.println(sb);

        for (ResultRow row : rList.getRows()) {
            sb.setLength(0);
            List<String> cols = row.getColumns();

            for (int i = 0; i < cols.size(); i++)
                sb.append(String.format("%" + colsize[i] + "s ", row.getColumns().get(i)));

            pw.println(sb);
        }
    }
}
