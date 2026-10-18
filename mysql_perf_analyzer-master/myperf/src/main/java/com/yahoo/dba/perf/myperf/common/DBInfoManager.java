/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import com.yahoo.dba.perf.myperf.meta.MetaDB;
import com.yahoo.dba.perf.myperf.metrics.MetricsDbBase;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;


/**
 * Manage database information
 *
 * @author xrao
 */
@Slf4j
public class DBInfoManager implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 保存project db信息。
     */
    private MetaDB projectDb;//note we store user cred here
    /**
     * 保存metrics db信息。
     */
    @Setter
    @Getter
    private MetricsDbBase metricsDb;//db def

    /**
     * 保存groups集合。
     */
    private Map<String, DBGroupInfo> groups = new TreeMap<>();
    /**
     * 保存mydbs集合。
     */
    private Map<String, MyDatabases> mydbs = new HashMap<>();
    /**
     * 保存last scan time信息。
     */
    private HashMap<String, Date> lastScanTime = new HashMap<>();

    /**
     * 获取clusters。
     *
     * @return 处理结果。
     */
    synchronized public Map<String, DBGroupInfo> getClusters() {
        return groups;
    }

    /**
     * 查找group。
     *
     * @param groupName group name参数。
     * @return 处理结果。
     */
    synchronized public DBGroupInfo findGroup(String groupName) {

        if (groups != null && groups.containsKey(groupName))
            return groups.get(groupName);

        return null;
    }

    /**
     * 添加or update instance。
     *
     * @param dbinfo dbinfo参数。
     */
    synchronized public void addOrUpdateInstance(DBInstanceInfo dbinfo) {
        DBGroupInfo cls = this.findGroup(dbinfo.getDbGroupName());

        if (cls == null) {
            cls = new DBGroupInfo();
            cls.setGroupName(dbinfo.getDbGroupName());
            this.groups.put(cls.getGroupName(), cls);
        }

        cls.addOrUpdateInstance(dbinfo);
    }

    /**
     * 查找db。
     *
     * @param groupName group name参数。
     * @param hostname  hostname参数。
     * @return 处理结果。
     */
    synchronized public DBInstanceInfo findDB(String groupName, String hostname) {
        DBGroupInfo cls = this.findGroup(groupName);

        if (cls == null)
            return null;

        for (DBInstanceInfo inst : cls.getInstances())
            if (hostname.equalsIgnoreCase(inst.getHostName()))
                return inst;


        return null;
    }

    /**
     * 移除dbhost。
     *
     * @param groupName group name参数。
     * @param hostname  hostname参数。
     * @return 是否满足条件。
     */
    synchronized public boolean removeDBHost(String groupName, String hostname) {
        DBGroupInfo cls = this.findGroup(groupName);

        if (cls == null)
            return false;

        for (int i = cls.getInstances().size() - 1; i >= 0; i--) {
            DBInstanceInfo inst = cls.getInstances().get(i);

            if (hostname.equalsIgnoreCase(inst.getHostName())) {
                cls.getInstances().remove(i);
                return true;
            }
        }

        return false;
    }

    /**
     * 移除group。
     *
     * @param groupName group name参数。
     */
    synchronized public void removeGroup(String groupName) {
        DBGroupInfo cls = this.findGroup(groupName);

        if (cls == null)
            return;

        this.groups.remove(groupName);
        this.projectDb.removeDbGroup(groupName);
    }

    /**
     * 执行list group names操作。
     *
     * @return 处理结果。
     */
    synchronized public List<String> listGroupNames() {
        ArrayList<String> clist = new ArrayList<>(this.groups.size());

        for (DBGroupInfo c : this.groups.values())
            clist.add(c.getGroupName());

        return clist;
    }

    /**
     * 执行init操作。
     *
     * @param metricsDb metrics db参数。
     * @return 是否满足条件。
     */
    synchronized public boolean init(MetricsDbBase metricsDb) {
        this.groups.clear();
        this.mydbs.clear();
        this.metricsDb = metricsDb;
        return init();
    }

    /**
     * 执行init操作。
     *
     * @return 是否满足条件。
     */
    synchronized public boolean init() {
        log.info("Loading stored db info");
        mydbs.clear();//refill
        List<DBInstanceInfo> dbList = null;

        if (this.metricsDb != null) {
            dbList = this.metricsDb.SearchDbInfo(null);
            log.info("Find " + dbList.size() + " db info");

            for (DBInstanceInfo db : dbList) {
                DBGroupInfo cls = findGroup(db.getDbGroupName());

                if (cls == null) {
                    cls = new DBGroupInfo();
                    cls.setGroupName(db.getDbGroupName());
                    this.groups.put(db.getDbGroupName(), cls);
                }

                cls.getInstances().add(db);
            }

            return true;
        } else
            log.warn("Cannot find stored db info because there is no metrics db");

        return false;
    }

    /**
     * 获取meta db。
     *
     * @return 处理结果。
     */
    public MetaDB getMetaDb() {
        return projectDb;
    }

    /**
     * 设置meta db。
     *
     * @param projectDb project db参数。
     */
    public void setMetaDb(MetaDB projectDb) {
        this.projectDb = projectDb;
    }

    /**
     * 获取my databases。
     *
     * @param owner      owner参数。
     * @param restricted restricted参数。
     * @return 处理结果。
     */
    public MyDatabases getMyDatabases(String owner, boolean restricted) {
        MyDatabases mydb = null;
        synchronized (this.mydbs) {

            if (this.mydbs.containsKey(owner))
                return this.mydbs.get(owner);

            mydb = new MyDatabases();
            this.mydbs.put(owner, mydb);
        }
        //fill data from meta rep
        mydb.addDbs(this.projectDb.listMyDBs(owner, restricted));
        return mydb;
    }

    /**
     * Will list all DBs and the dbs with user specified credentials are listed at the top
     *
     * @param user
     * @return
     */
    public List<String> listDbsByUserInfo(String user, boolean restricted) {
        Set<String> mydb = this.getMyDatabases(user, restricted).getMyDbList();
        List<String> alldb = new ArrayList<>(this.groups.size());

        synchronized (this) {

            for (String db : mydb)
                if (this.groups.containsKey(db))
                    alldb.add(db);

            if (!restricted)
                for (String key : this.groups.keySet())
                    if (!mydb.contains(key))
                        alldb.add(key);

        }
        return alldb;
    }

    /**
     * 更新last scan time。
     *
     * @param dbgroup dbgroup参数。
     * @param dbhost  dbhost参数。
     * @param dt      dt参数。
     */
    public void updateLastScanTime(String dbgroup, String dbhost, Date dt) {
        synchronized (this.lastScanTime) {
            this.lastScanTime.put(dbgroup + ":" + dbhost, dt);
        }
    }

    /**
     * 获取last access date。
     *
     * @param dbgroup dbgroup参数。
     * @param dbhost  dbhost参数。
     * @return 处理结果。
     */
    public Date getLastAccessDate(String dbgroup, String dbhost) {
        synchronized (this.lastScanTime) {
            return this.lastScanTime.get(dbgroup + ":" + dbhost);
        }

    }

    /**
     * 执行retrieve dbinfo操作。
     *
     * @param dbGroupName db group name参数。
     * @param hostName    host name参数。
     * @return 处理结果。
     */
    public DBInstanceInfo retrieveDBInfo(String dbGroupName, String hostName) {

        if (this.metricsDb != null)
            return this.metricsDb.retrieveDBInfo(dbGroupName, hostName);

        return null;
    }

    /**
     * 执行upsert dbinfo操作。
     *
     * @param dbinfo dbinfo参数。
     */
    public void upsertDBInfo(DBInstanceInfo dbinfo) {

        if (this.metricsDb != null)
            this.metricsDb.upsertDBInfo(dbinfo);
        else
            throw new RuntimeException("There is no metricsdb defined yet.");
    }

    /**
     * 移除db group。
     *
     * @param dbGroupName db group name参数。
     * @param owner       owner参数。
     * @param force       force参数。
     * @return 是否满足条件。
     */
    public boolean removeDbGroup(String dbGroupName, String owner, boolean force) {

        if (this.metricsDb != null)
            return this.metricsDb.removeDbGroup(dbGroupName, owner, force);

        return false;
    }

    /**
     * 移除dbinfo。
     *
     * @param dbGroupName db group name参数。
     * @param hostName    host name参数。
     * @param owner       owner参数。
     * @param force       force参数。
     * @return 是否满足条件。
     */
    public boolean removeDBInfo(String dbGroupName, String hostName, String owner, boolean force) {

        if (this.metricsDb != null)
            return this.metricsDb.removeDBInfo(dbGroupName, hostName, owner, force);

        return false;
    }

    /**
     * 执行search db info操作。
     *
     * @param keyword keyword参数。
     * @return 处理结果。
     */
    public List<DBInstanceInfo> SearchDbInfo(String keyword) {

        if (this.metricsDb != null)
            return this.metricsDb.SearchDbInfo(keyword);

        return new ArrayList<>();

    }

    /**
     * 执行enable snmp操作。
     *
     * @param dbGroup  db group参数。
     * @param hostname hostname参数。
     * @param enabled  enabled参数。
     * @param owner    owner参数。
     * @param force    force参数。
     * @return 是否满足条件。
     */
    public boolean enableSnmp(String dbGroup, String hostname, boolean enabled, String owner, boolean force) {

        if (this.metricsDb != null)
            return this.metricsDb.enableSnmp(dbGroup, hostname, enabled, owner, force);

        return false;
    }

    /**
     * 执行enable metrics操作。
     *
     * @param dbGroup  db group参数。
     * @param hostname hostname参数。
     * @param enabled  enabled参数。
     * @param owner    owner参数。
     * @param force    force参数。
     * @return 是否满足条件。
     */
    public boolean enableMetrics(String dbGroup, String hostname, boolean enabled, String owner, boolean force) {

        if (this.metricsDb != null)
            return this.metricsDb.enableMetrics(dbGroup, hostname, enabled, owner, force);

        return false;
    }

    /**
     * 执行rename db group操作。
     *
     * @param oldname oldname参数。
     * @param newName new name参数。
     */
    synchronized public void renameDbGroup(String oldname, String newName) {
        //nothing to do
        if (!this.groups.containsKey(oldname.toLowerCase()) || newName == null || newName.isEmpty())
            return;

        DBGroupInfo dbgroups = this.groups.get(oldname.toLowerCase());
        this.groups.remove(oldname.toLowerCase());
        this.metricsDb.renameDbGroup(oldname, newName);
        List<DBInstanceInfo> instances = dbgroups.getInstances();
        dbgroups.setGroupName(newName.toLowerCase());

        for (DBInstanceInfo inst : instances)
            inst.setDbGroupName(newName.toLowerCase());

        this.groups.put(newName.toLowerCase(), dbgroups);
        this.projectDb.renameDbGroupName(oldname.toLowerCase(), newName.toLowerCase());

        for (Map.Entry<String, MyDatabases> entry : this.mydbs.entrySet())
            entry.getValue().replaceDb(oldname.toLowerCase(), newName.toLowerCase());

    }

    /**
     * 更新restricteduser acl。
     *
     * @param username    username参数。
     * @param dbgroupname dbgroupname参数。
     * @param visible     visible参数。
     * @return 是否满足条件。
     */
    public boolean updateRestricteduserAcl(String username, String dbgroupname, boolean visible) {

        if (this.projectDb.updateUserAcl(username, dbgroupname, visible)) {
            MyDatabases mydb = this.getMyDatabases(username, true);

            if (mydb != null)
                if (visible)
                    mydb.addDb(dbgroupname);
                else {
                    revokeDBCredential(username, dbgroupname);
                    mydb.removeDb(dbgroupname);
                }

            return true;
        }

        return false;

    }

    /**
     * This method will copy db credential when assign a DB to a restricted user or default admin/scanner user.
     * Assume the assigner can access the underlying database.
     *
     * @param targetUserName
     * @param restrictedUser
     * @param dbGroup
     * @param dbuser
     * @param password
     */
    public void copyManagedDBCredential(String targetUserName, boolean restrictedUser,
                                        String srcUsername, String dbGroup, String dbuser, String password) {

        if (targetUserName == null || targetUserName.isEmpty() || targetUserName.equalsIgnoreCase(srcUsername))
            return;

        DBCredential cred2 = new DBCredential();
        cred2.setAppUser(targetUserName);
        cred2.setDbGroupName(dbGroup);
        cred2.setUsername(dbuser);
        cred2.setPassword(password);
        getMetaDb().upsertDBCredential(cred2);
        //note we handle the allowed database for restricted user in different way.
        if (!restrictedUser)
            getMyDatabases(cred2.getAppUser(), false).addDb(cred2.getDbGroupName());
    }

    /**
     * For now, only used to handle restricted user
     *
     * @param owner
     * @param dbGroupName
     */
    public void revokeDBCredential(String owner, String dbGroupName) {
        getMetaDb().removeDBCredential(owner, dbGroupName);
    }
}
