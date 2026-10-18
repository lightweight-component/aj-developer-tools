/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.HashMap;
import java.util.Map;


/**
 * store realtime status
 *
 * @author xrao
 */
@Slf4j
public class InstanceStatesManager implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public static String OBJ_FILE_NAME = "instance_status.ser";
    public static final String STORAGE_DIR = "autoscan";
    @Getter
    @Setter
    private String rootPath = "myperf_reports";//data will be stored under $rootPath/autoscan

    private Map<Integer, InstanceStates> statesMap = new HashMap<Integer, InstanceStates>();

    public InstanceStatesManager() {

    }

    /**
     * Make the data persistent
     */
    public void saveStatus() {
        log.info("Store instance status snapshots");
        File root = new File(new File(this.rootPath), STORAGE_DIR);
        File objFile = new File(root, OBJ_FILE_NAME);
        storeObject(objFile);
    }

    public void storeObject(File objf) {
        ObjectOutputStream outputStream = null;

        try {

            //Construct the LineNumberReader object
            outputStream = new ObjectOutputStream(new FileOutputStream(objf));
            outputStream.writeObject(this.statesMap);

        } catch (Exception ex) {
            log.error("Exception when store instance status object", ex);
        } finally {
            //Close the ObjectOutputStream
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, InstanceStates> readObject(File objf) {
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(
                    new FileInputStream(objf));
            return (Map<Integer, InstanceStates>) input.readObject();
        } catch (Exception ex) {
            log.warn("Exception when read instance status object", ex);

        } finally {
            if (input != null) try {
                input.close();
            } catch (Exception iex) {
            }
        }
        return null;
    }

    synchronized public InstanceStates getStates(int dbid) {
        if (statesMap.containsKey(dbid))
            return statesMap.get(dbid);
        return null;
    }


    synchronized public void addInstanceStates(int dbid) {
        if (statesMap.containsKey(dbid))
            return;
        this.statesMap.put(dbid, new InstanceStates());
    }

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
        if (!root.exists()) root.mkdirs();

        File objFile = new File(root, OBJ_FILE_NAME);
        if (objFile.exists()) {
            log.info("Load saved status");
            Map<Integer, InstanceStates> savedState = readObject(objFile);
            if (savedState != null) {
                for (Map.Entry<Integer, InstanceStates> e : savedState.entrySet())
                    this.statesMap.put(e.getKey(), e.getValue());
            }
        }

        for (Map.Entry<String, DBGroupInfo> e : context.getDbInfoManager().getClusters().entrySet()) {
            for (DBInstanceInfo dbinfo : e.getValue().getInstances()) {
                if (!this.statesMap.containsKey(dbinfo.getDbid()))
                    this.statesMap.put(dbinfo.getDbid(), new InstanceStates());
            }
        }
        log.info("Initialized InstanceStatesManager");
    }

}
