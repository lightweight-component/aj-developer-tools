/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;


/**
 * store realtime status
 *
 * @author xrao
 */
@Slf4j
public class InstanceStatesManager implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 保存obj file name信息。
     */
    public static String OBJ_FILE_NAME = "instance_status.ser";
    /**
     * 定义storage dir常量。
     */
    public static final String STORAGE_DIR = "autoscan";

    /**
     * 保存root path信息。
     */
    @Getter
    @Setter
    private String rootPath = "myperf_reports";//data will be stored under $rootPath/autoscan

    /**
     * 保存states map集合。
     */
    private Map<Integer, InstanceStates> statesMap = new HashMap<>();

    /**
     * Make the data persistent
     */
    public void saveStatus() {
        log.info("Store instance status snapshots");
        File root = new File(new File(this.rootPath), STORAGE_DIR);
        File objFile = new File(root, OBJ_FILE_NAME);

        storeObject(objFile);
    }

    /**
     * 执行store object操作。
     *
     * @param objf objf参数。
     */
    public void storeObject(File objf) {
        try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(objf.toPath()))) {
            outputStream.writeObject(this.statesMap);
        } catch (Exception ex) {
            log.error("Exception when store instance status object", ex);
        }
    }

    /**
     * 读取object。
     *
     * @param objf objf参数。
     * @return 处理结果。
     */
    @SuppressWarnings("unchecked")
    public Map<Integer, InstanceStates> readObject(File objf) {
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(objf.toPath()))) {
            return (Map<Integer, InstanceStates>) input.readObject();
        } catch (Exception ex) {
            log.warn("Exception when read instance status object", ex);
        }

        return null;
    }

    /**
     * 获取states。
     *
     * @param dbid dbid参数。
     * @return 处理结果。
     */
    synchronized public InstanceStates getStates(int dbid) {

        if (statesMap.containsKey(dbid))
            return statesMap.get(dbid);

        return null;
    }


    /**
     * 添加instance states。
     *
     * @param dbid dbid参数。
     */
    synchronized public void addInstanceStates(int dbid) {

        if (statesMap.containsKey(dbid))
            return;

        this.statesMap.put(dbid, new InstanceStates());
    }

    /**
     * 移除instance states。
     *
     * @param dbid dbid参数。
     * @return 是否满足条件。
     */
    synchronized public boolean removeInstanceStates(int dbid) {

        if (statesMap.containsKey(dbid)) {
            statesMap.remove(dbid);
            return true;
        }

        return false;
    }

    /**
     * This should be called during context initialization
     *
     * @param context
     */
    public void init(MyPerfContext context) {
        log.info("Initialize InstanceStatesManager");
        File root = new File(new File(this.rootPath), STORAGE_DIR);

        if (!root.exists())
            root.mkdirs();

        File objFile = new File(root, OBJ_FILE_NAME);

        if (objFile.exists()) {
            log.info("Load saved status");
            Map<Integer, InstanceStates> savedState = readObject(objFile);

            if (savedState != null)
                for (Map.Entry<Integer, InstanceStates> e : savedState.entrySet())
                    this.statesMap.put(e.getKey(), e.getValue());

        }

        for (Map.Entry<String, DBGroupInfo> e : context.getDbInfoManager().getClusters().entrySet())
            for (DBInstanceInfo dbinfo : e.getValue().getInstances())
                if (!this.statesMap.containsKey(dbinfo.getDbid()))
                    this.statesMap.put(dbinfo.getDbid(), new InstanceStates());


        log.info("Initialized InstanceStatesManager");
    }

}
