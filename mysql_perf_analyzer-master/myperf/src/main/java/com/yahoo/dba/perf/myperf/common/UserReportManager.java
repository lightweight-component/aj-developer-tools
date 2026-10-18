/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * handle reports for a single user
 *
 * @author xrao
 */
@Slf4j
public class UserReportManager {

    /**
     * 表示user report相关的类型。
     */
    public static class UserReport {
        /**
         * 定义field separator常量。
         */
        public static final char fieldSeparator = '\u0001';
        /**
         * 定义pair separator常量。
         */
        public static final char pairSeparator = '\u0002';
        /**
         * 定义kv separator常量。
         */
        public static final char kvSeparator = '\u0003';

        /**
         * 保存id信息。
         */
        @Getter
        @Setter
        private int id;//report id, user specific
        /**
         * 保存type信息。
         */
        @Getter
        @Setter
        private String type;//report type
        /**
         * 保存dbname信息。
         */
        @Getter
        @Setter
        private String dbname;//db/cluster name
        /**
         * 保存dbhost信息。
         */
        @Getter
        @Setter
        private String dbhost;//db hostname, default to all
        /**
         * 保存created timestamp信息。
         */
        @Getter
        @Setter
        private String created_timestamp;//yyyyMMddHHmmssSSS in java
        /**
         * 保存completed timestamp信息。
         */
        @Getter
        @Setter
        private String completed_timestamp;//yyyyMMddHHmmssSSS in java
        /**
         * 保存format信息。
         */
        @Getter
        @Setter
        private String format;//html or text
        /**
         * 保存filename信息。
         */
        @Getter
        @Setter
        private String filename;//file name will be type dependent
        /**
         * 保存parameters信息。
         */
        @Getter
        private LinkedHashMap<String, String> parameters = new LinkedHashMap<>();

        /**
         * 添加parameter。
         *
         * @param name  name参数。
         * @param value value参数。
         */
        public void addParameter(String name, String value) {
            this.parameters.put(name, value);
        }

        /**
         * 执行encode line操作。
         *
         * @return 处理结果。
         */
        public String encodeLine() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.id).append(fieldSeparator);
            sb.append(this.type).append(fieldSeparator);
            sb.append(this.dbname).append(fieldSeparator);
            sb.append(this.dbhost).append(fieldSeparator);
            sb.append(this.created_timestamp).append(fieldSeparator);
            sb.append(this.format).append(fieldSeparator);
            sb.append(this.filename).append(fieldSeparator);
            boolean first = true;

            for (Map.Entry<String, String> e : this.parameters.entrySet()) {

                if (!first)
                    sb.append(pairSeparator);
                else first = false;

                sb.append(e.getKey()).append(kvSeparator).append(e.getValue());
            }

            sb.append(fieldSeparator);
            sb.append(this.completed_timestamp);
            return sb.toString();
        }

        /**
         * 执行decode line操作。
         *
         * @param line line参数。
         * @return 处理结果。
         */
        public static UserReport decodeLine(String line) {

            if (line == null || line.trim().length() == 0)
                return null;

            String[] tokens = line.trim().split("\\u0001");

            UserReport urp = new UserReport();
            try {
                int idx = 0;
                urp.setId(Integer.parseInt(tokens[idx++]));
                urp.setType(tokens[idx++]);
                urp.setDbname(tokens[idx++]);
                urp.setDbhost(tokens[idx++]);
                urp.setCreated_timestamp(tokens[idx++]);
                urp.setFormat(tokens[idx++]);
                urp.setFilename(tokens[idx++]);
                String p = tokens[idx++];

                if (p != null) {
                    String[] pairs = p.split("\\u0002");

                    if (pairs != null)
                        for (String pair : pairs) {
                            String[] kv = pair.split("\\u0003");

                            if (kv != null && kv.length == 2)
                                urp.addParameter(kv[0], kv[1]);

                        }

                }

                urp.setCompleted_timestamp(tokens[idx++]);
            } catch (Exception ex) {
                log.error("Exception", ex);
            }
            return urp;
        }

    }

    /**
     * 定义line separator常量。
     */
    public static final char lineSeparator = '\n';

    /**
     * 保存rootpath信息。
     */
    @Getter
    @Setter
    private String rootpath = "myperf_reports";//root path to store reports, default to reports under current directory
    /**
     * 保存idxfile信息。
     */
    @Getter
    @Setter
    private String idxfile = "rep.idx";//index file name
    /**
     * 保存username信息。
     */
    @Getter
    @Setter
    private String username;

    /**
     * 标记initiated状态。
     */
    private volatile boolean initiated = false;

    /**
     * 保存userroot信息。
     */
    private File userroot;

    /**
     * 保存max id信息。
     */
    volatile int maxId = 0;

    /**
     * 创建UserReportManager对象。
     *
     * @param username username参数。
     * @param rootpath rootpath参数。
     */
    public UserReportManager(String username, String rootpath) {
        this.username = username;

        if (rootpath != null && rootpath.trim().length() > 0)
            this.rootpath = rootpath.trim();
        //else use default
    }

    /**
     * 执行init操作。
     */
    public void init() {

        if (!initiated) {
            File reproot = new File(rootpath);

            if (!reproot.exists())
                reproot.mkdir();

            userroot = new File(reproot, username);

            if (!userroot.exists())
                userroot.mkdir();

            this.initiated = true;
        }
    }

    /**
     * 添加report entry。
     *
     * @param urp urp参数。
     */
    synchronized public void addReportEntry(UserReport urp) {
        File idxFile = new File(userroot, idxfile);

        if (maxId == 0)
            this.list();

        this.maxId++;
        urp.setId(this.maxId);
        try (FileWriter fw = new FileWriter(idxFile, true)) {
            String line = urp.encodeLine() + lineSeparator;
            fw.write(line);
        } catch (Exception ex) {

            log.error("Exception", ex);
        }
    }

    /**
     * 执行list操作。
     *
     * @return 处理结果。
     */
    synchronized public List<UserReport> list() {
        List<UserReport> repList = new ArrayList<>();
        File idxFile = new File(userroot, idxfile);

        if (idxFile.exists())
            try (BufferedReader br = new BufferedReader(new FileReader(idxFile))) {

                String line = null;

                while ((line = br.readLine()) != null) {
                    UserReport rep = UserReport.decodeLine(line);

                    if (rep != null) {
                        repList.add(rep);

                        if (rep.getId() >= this.maxId)
                            maxId = rep.getId();
                    }
                }
            } catch (Exception ex) {
                log.error("Exception", ex);
            }

        return repList;
    }

    /**
     * 执行list result list操作。
     *
     * @return 处理结果。
     */
    public ResultList listResultList() {
        List<UserReport> repList = list();

        ResultList rList = new ResultList();
        ColumnDescriptor desc = new ColumnDescriptor();
        rList.setColumnDescriptor(desc);
        int idx = 0;
        desc.addColumn("ID", false, idx++);
        desc.addColumn("TYPE", false, idx++);
        desc.addColumn("DB", false, idx++);
        desc.addColumn("HOST", false, idx++);
        desc.addColumn("CREATED", false, idx++);
        desc.addColumn("COMPLETED", false, idx++);
        desc.addColumn("FORMAT", false, idx++);
        desc.addColumn("PARAMETER", false, idx++);
        desc.addColumn("FILENAME", false, idx++);

        //list in reverse order
        for (int i = repList.size() - 1; i >= 0; i--) {
            UserReport urp = repList.get(i);
            ResultRow row = new ResultRow();
            List<String> cols = new ArrayList<>(8);
            row.setColumnDescriptor(desc);
            row.setColumns(cols);
            cols.add(String.valueOf(urp.getId()));
            cols.add(urp.getType());
            cols.add(urp.getDbname());
            cols.add(urp.getDbhost());
            cols.add(urp.getCreated_timestamp());
            cols.add(urp.getCompleted_timestamp());
            cols.add(urp.getFormat());
            cols.add(urp.getParameters().toString());
            cols.add(urp.getFilename());
            rList.addRow(row);
        }

        return rList;
    }

    /**
     * Refresh the idx file, for example, after delete an entry
     *
     * @param repList
     */
    public void update(List<UserReport> repList) {
        File idxFile = new File(userroot, idxfile);
        try (FileWriter fw = new FileWriter(idxFile)) {

            for (UserReport urp : repList) {
                String line = urp.encodeLine() + lineSeparator;
                fw.write(line);
            }

        } catch (Exception ex) {
            log.error("Exception", ex);
        }

    }

    /**
     * 获取file for download。
     *
     * @param fname fname参数。
     * @return 处理结果。
     */
    public File getFileForDownload(String fname)//retrieve file for download
    {
        File f = new File(new File(new File(getRootpath()), getUsername()), fname);

        if (!f.exists() || !f.getAbsolutePath().endsWith(fname))//check file and security
            return null;

        return f;
    }

    /**
     * 删除file。
     *
     * @param fname fname参数。
     * @param id    id参数。
     * @return 是否满足条件。
     */
    public boolean deleteFile(String fname, String id) {

        if (fname == null && id == null)
            return false;
        //first check if we have such file or not
        //update idx
        int rpid = -1;
        try {
            rpid = Integer.parseInt(id);
        } catch (Exception ex) {
        }
        List<UserReport> repList = this.list();
        boolean findOne = false;

        for (int i = repList.size() - 1; i >= 0; i--)
            if (repList.get(i).getFilename().equalsIgnoreCase(fname) || rpid == repList.get(i).getId()) {
                fname = repList.get(i).getFilename();
                repList.remove(i);
                findOne = true;
                break;
            }

        if (!findOne)
            return false;

        this.update(repList);
        File f = this.getFileForDownload(fname);

        if (f != null && !"rep.idx".equalsIgnoreCase(f.getName())) {
            f.delete();
            return true;
        }

        return false;
    }
}
