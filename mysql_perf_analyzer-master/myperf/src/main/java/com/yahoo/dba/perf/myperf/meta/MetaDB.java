/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.meta;

import com.yahoo.dba.perf.myperf.common.AppUser;
import com.yahoo.dba.perf.myperf.common.DBCredential;
import com.yahoo.dba.perf.myperf.common.DBUtils;
import com.yahoo.dba.perf.myperf.common.MD5Util;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.sql.*;
import java.util.*;

/**
 * 表示meta db相关的类型。
 */
@Slf4j
public class MetaDB implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 定义db name常量。
     */
    private static final String DB_NAME = "metaDB";
    /**
     * 定义schema name常量。
     */
    private static final String SCHEMA_NAME = "METADB";
    /**
     * 定义cred tablename常量。
     */
    private static final String CRED_TABLENAME = "DBCREDENTIALS";
    /**
     * 定义appuser tablename常量。
     */
    private static final String APPUSER_TABLENAME = "APPUSERS";
    /**
     * 定义useracl tablename常量。
     */
    private static final String USERACL_TABLENAME = "USERACLS"; //user, db group access control

    /**
     * 保存key tool信息。
     */
    private KeyTool.DesEncrypter keyTool = new KeyTool.DesEncrypter();
    /**
     * 保存dbkey信息。
     */
    @Getter
    @Setter
    private String dbkey;

    //store  credentials so we no need to go to db everytime
    //the key is owner||dbname
    /**
     * 保存cred cache集合。
     */
    private Map<String, DBCredential> credCache = Collections.synchronizedMap(new HashMap<>());

    /**
     * 定义default user常量。
     */
    public static final String DEFAULT_USER = "myperf";

    /**
     * 创建MetaDB对象。
     */
    public MetaDB() {
        try {
            //load embedded db driver
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
        } catch (Exception ex) {
        }
    }


    /**
     * 执行enc操作。
     *
     * @param str str参数。
     * @return 处理结果。
     */
    public String enc(String str) {
        return keyTool.encrypt(str);
    }

    /**
     * 执行dec操作。
     *
     * @param str str参数。
     * @return 处理结果。
     */
    public String dec(String str) {
        return keyTool.decrypt(str);
    }

    /**
     * Check required schema and tables
     *
     * @return
     */
    public boolean init() {
        try {
            checkAndCreateSchema();
            checkAndCreateTables();
            return true;
        } catch (Exception ex) {
            log.error("Exception", ex);
            return false;
        }
    }

    /**
     * Get a new connection
     *
     * @return
     */
    private Connection getConnection() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection("jdbc:derby:" + DB_NAME + ";create=true", SCHEMA_NAME, this.getDbkey());
            return conn;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return null;
    }

    /**
     * 检查and create schema。
     */
    private void checkAndCreateSchema() throws SQLException {
        Properties props = new Properties();
        boolean findSchema = false;
        try (Connection conn = DriverManager.getConnection("jdbc:derby:" + DB_NAME + ";create=true", props);
             ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {

                if (SCHEMA_NAME.equalsIgnoreCase(rs.getString("TABLE_SCHEM"))) {
                    log.info("Find schema " + SCHEMA_NAME);
                    findSchema = true;
                    break;
                }
            }
            if (!findSchema)
                try (Statement stmt = conn.createStatement()) {
                    log.info("Create schema " + SCHEMA_NAME + ".");
                    String sql = "CREATE SCHEMA " + SCHEMA_NAME + " AUTHORIZATION " + this.getDbkey();
                    stmt.execute(sql);
                }

        }
    }

    /**
     * 检查and create tables。
     */
    private void checkAndCreateTables() throws SQLException {
        try (Connection conn = getConnection()) {
            checkAndCreateAppUserTable(conn);
            checkAndCreateDBCredentialTable(conn);
            checkAndCreateUserAclTable(conn);
        }
    }

    /**
     * 检查and create app user table。
     *
     * @param conn conn参数。
     */
    private void checkAndCreateAppUserTable(Connection conn) throws SQLException {

        if (!DBUtils.hasTable(conn, SCHEMA_NAME, APPUSER_TABLENAME)) {

            if (!DBUtils.hasTable(conn, SCHEMA_NAME, APPUSER_TABLENAME))
                try (Statement stmt = conn.createStatement()) {
                    log.info("create table " + APPUSER_TABLENAME);
                    String sql = "create table " + APPUSER_TABLENAME + "(USERNAME VARCHAR(30) NOT NULL PRIMARY KEY, EMAIL VARCHAR(100), MD5HASH VARCHAR(100), USER_PRIVILEGE SMALLINT, CREATED TIMESTAMP DEFAULT CURRENT TIMESTAMP, VERIFIED SMALLINT DEFAULT 0)";
                    stmt.execute(sql);
                    sql = "create index UK_" + APPUSER_TABLENAME + " on " + APPUSER_TABLENAME + "(USERNAME)";
                    stmt.execute(sql);

                    try (PreparedStatement pstmt = conn.prepareStatement("insert into " + APPUSER_TABLENAME + " (USERNAME,MD5HASH,USER_PRIVILEGE, VERIFIED) VALUES(?,?,?,?)")) {
                        pstmt.setString(1, DEFAULT_USER);
                        try {
                            pstmt.setString(2, MD5Util.MD5(DEFAULT_USER + ":change"));
                        } catch (Exception ex) {
                        }
                        ;
                        pstmt.setInt(3, AppUser.PRIV_USER_POWER);
                        pstmt.setInt(4, 1);
                        pstmt.execute();
                        conn.commit();
                    }
                }

        } else
            try (Statement stmt = conn.createStatement()) {
                String sql = "select * from " + APPUSER_TABLENAME + " where 1=0";
                boolean hasCol;
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cnt = meta.getColumnCount();
                    hasCol = false;

                    for (int i = 1; i <= cnt; i++) {
                        String col = meta.getColumnName(i);

                        if ("VERIFIED".equalsIgnoreCase(col)) {
                            hasCol = true;
                            break;
                        }
                    }
                }
                if (!hasCol) {
                    log.info("Add new column VERIFIED");
                    stmt.execute("ALTER TABLE " + APPUSER_TABLENAME + " ADD COLUMN VERIFIED SMALLINT DEFAULT 0");
                    //update existing as 1
                    stmt.execute("UPDATE " + APPUSER_TABLENAME + " SET VERIFIED=1");
                }
            }

    }


    /**
     * 检查and create dbcredential table。
     *
     * @param conn conn参数。
     */
    private void checkAndCreateDBCredentialTable(Connection conn) throws SQLException {

        if (!DBUtils.hasTable(conn, SCHEMA_NAME, CRED_TABLENAME))
            try (Statement stmt = conn.createStatement()) {
                log.info("create table " + CRED_TABLENAME + ".");
                String sql = "create table " + CRED_TABLENAME + "(OWNER VARCHAR(30), DBGROUPNAME VARCHAR(30),USERNAME VARCHAR(60), CREDENTIAL VARCHAR(1024), VERIFIED SMALLINT DEFAULT 0, CREATED TIMESTAMP DEFAULT CURRENT TIMESTAMP)";
                stmt.execute(sql);
                sql = "create unique index UK_" + CRED_TABLENAME + " on " + CRED_TABLENAME + " (OWNER, DBGROUPNAME)";
                stmt.execute(sql);
            }
        else
            try (Statement stmt = conn.createStatement()) {
                //because of an earlier bug, we forgot to create uniq index/PK on this table
                String ukSQL = "SELECT * FROM " + " SYS.SYSCONGLOMERATES WHERE " + " CONGLOMERATENAME = 'UK_" + CRED_TABLENAME + "'";
                log.info("Check unique constraints on table " + CRED_TABLENAME + " with " + ukSQL);
                boolean hasIndex;
                try (ResultSet rs = stmt.executeQuery(ukSQL)) {
                    hasIndex = false;

                    if (rs != null && rs.next()) {
                        log.info("UK_" + CRED_TABLENAME + " exists, nothing to do.");
                        hasIndex = true;
                    }
                }
                if (!hasIndex) {
                    log.info("UK_" + CRED_TABLENAME + " does not exist, create it.");
                    try {
                        boolean ret = stmt.execute("CREATE UNIQUE INDEX UK_" + CRED_TABLENAME + " ON " + CRED_TABLENAME + " (OWNER, DBGROUPNAME)");
                        log.info("UK_" + CRED_TABLENAME + " created: " + ret);
                    } catch (Throwable ex) {
                        log.warn("Exception when create UK_" + CRED_TABLENAME, ex);
                    }
                }

                String sql = "select * from " + CRED_TABLENAME + " where 1=0";
                boolean hasCol;
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cnt = meta.getColumnCount();
                    hasCol = false;

                    for (int i = 1; i <= cnt; i++) {
                        String col = meta.getColumnName(i);

                        if ("CREDENTIAL".equalsIgnoreCase(col) && meta.getColumnDisplaySize(i) < 1024) {
                            hasCol = true;
                            break;
                        }
                    }
                }
                if (hasCol) {
                    log.info("Add new column credential size");
                    stmt.execute("ALTER TABLE " + CRED_TABLENAME + " ALTER COLUMN CREDENTIAL SET DATA TYPE VARCHAR(1024)");
                }
            }

    }

    /**
     * 检查and create user acl table。
     *
     * @param conn conn参数。
     */
    private void checkAndCreateUserAclTable(Connection conn) throws SQLException {

        if (!DBUtils.hasTable(conn, SCHEMA_NAME, USERACL_TABLENAME))
            try (Statement stmt = conn.createStatement()) {
                log.info("create table " + USERACL_TABLENAME + ".");
                String sql = "create table " + USERACL_TABLENAME + "(USERNAME VARCHAR(30), DBGROUPNAME VARCHAR(30), VISIBLE SMALLINT DEFAULT 0, CREATED TIMESTAMP DEFAULT CURRENT TIMESTAMP, PRIMARY KEY(USERNAME, DBGROUPNAME))";
                stmt.execute(sql);
            }

    }

    /**
     * Shut down metadb
     */
    public void destroy() {
        try {
            log.info("Shutdown meta db");
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException ex) {
            log.warn("Exception", ex);

            if ("XJ015".equalsIgnoreCase(ex.getSQLState()))
                log.info("meta DB has been shutdown.");

        }
    }

    /**
     * retrieve user info
     *
     * @param username
     * @return
     */
    public AppUser retrieveUserInfo(String username) {
        try (Connection conn = getConnection()) {
            return this.retrieveUserInfo(conn, username);
        } catch (Exception ex) {
        }
        return null;
    }

    /**
     * 执行retrieve user info操作。
     *
     * @param conn     conn参数。
     * @param username username参数。
     * @return 处理结果。
     */
    private AppUser retrieveUserInfo(Connection conn, String username) {
        String sql = "select * from " + APPUSER_TABLENAME + " where username=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.toLowerCase());
            try (ResultSet rs = pstmt.executeQuery()) {

                if (rs != null && rs.next()) {
                    AppUser user = new AppUser();
                    user.setName(rs.getString("USERNAME"));
                    user.setMd5Hash(rs.getString("MD5HASH"));
                    user.setUserprivilege(rs.getShort("USER_PRIVILEGE"));
                    user.setEmail(rs.getString("EMAIL"));
                    user.setVerified("1".equals(rs.getString("VERIFIED")));
                    return user;
                }
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return null;
    }


    /**
     * @param user
     */
    public void upsertAppUser(AppUser user) {

        if (user == null)
            return;

        String sql2 = "update " + APPUSER_TABLENAME + " set MD5HASH=?, USER_PRIVILEGE=?, EMAIL=?, VERIFIED=? where USERNAME=?";
        String sql3 = "insert into " + APPUSER_TABLENAME + " (MD5HASH, USER_PRIVILEGE, EMAIL, VERIFIED,USERNAME) values(?,?,?,?,?)";
        boolean findOne = false;
        try (Connection conn = getConnection()) {
            try {
                //first, check if we have record
                findOne = this.retrieveUserInfo(conn, user.getName()) != null;
                try (PreparedStatement pstmt = conn.prepareStatement(findOne ? sql2 : sql3)) {
                    int idx = 1;
                    pstmt.setString(idx++, user.getMd5Hash());
                    pstmt.setInt(idx++, user.getUserprivilege());
                    pstmt.setString(idx++, user.getEmail() == null ? "NA" : user.getEmail());
                    pstmt.setInt(idx++, user.isVerified() ? 1 : 0);
                    pstmt.setString(idx++, user.getName().toLowerCase());
                    pstmt.execute();
                    conn.commit();
                }
            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Update user email address
     *
     * @param user
     * @param newEmail
     * @return
     */
    public boolean changeEmail(String user, String newEmail) {

        if (user == null)
            return false;

        String sql2 = "update " + APPUSER_TABLENAME + " set EMAIL=? where USERNAME=?";
        try (Connection conn = getConnection()) {
            try {
                //first, check if we have record
                AppUser appUser = this.retrieveUserInfo(conn, user);

                if (appUser == null)
                    return false;

                try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                    pstmt.setString(1, newEmail);
                    pstmt.setString(2, appUser.getName().toLowerCase());
                    pstmt.execute();
                    conn.commit();
                    return true;
                }
            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * 执行confirm user操作。
     *
     * @param user      user参数。
     * @param confirmed confirmed参数。
     * @return 是否满足条件。
     */
    public boolean confirmUser(String user, boolean confirmed) {

        if (user == null)
            return false;

        String sql2 = "update " + APPUSER_TABLENAME + " set VERIFIED=? where USERNAME=?";
        try (Connection conn = getConnection()) {
            try {
                //first, check if we have record
                AppUser appUser = this.retrieveUserInfo(conn, user);

                if (appUser == null)
                    return false;

                try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                    pstmt.setInt(1, confirmed ? 1 : 0);
                    pstmt.setString(2, appUser.getName().toLowerCase());
                    pstmt.execute();
                    conn.commit();
                    return true;
                }
            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Update user privilege
     *
     * @param user
     * @param newPrivilege
     * @return
     */
    public boolean changePrivilege(String user, int newPrivilege) {

        if (user == null)
            return false;

        String sql2 = "update " + APPUSER_TABLENAME + " set USER_PRIVILEGE=? where USERNAME=?";
        try (Connection conn = getConnection()) {
            try {
                //first, check if we have record
                AppUser appUser = this.retrieveUserInfo(conn, user);

                if (appUser == null)
                    return false;

                appUser.setUserprivilege(newPrivilege);
                try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                    pstmt.setInt(1, appUser.getUserprivilege());
                    pstmt.setString(2, appUser.getName().toLowerCase());
                    pstmt.execute();
                    conn.commit();
                    return true;
                }
            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Update user password hash
     *
     * @param user
     * @param newPwd
     * @return
     */
    public boolean changePasssword(String user, String newPwd) {

        if (user == null)
            return false;

        String sql2 = "update " + APPUSER_TABLENAME + " set MD5HASH=? where USERNAME=?";
        try (Connection conn = getConnection()) {
            try {
                //first, check if we have record
                AppUser appUser = this.retrieveUserInfo(conn, user);

                if (appUser == null)
                    return false;

                appUser.setPassword(newPwd);
                appUser.setMd5Hash(appUser.calMd5(newPwd));
                try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                    pstmt.setString(1, appUser.getMd5Hash());
                    pstmt.setString(2, appUser.getName().toLowerCase());
                    pstmt.execute();
                    conn.commit();
                    return true;
                }
            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * 删除user。
     *
     * @param user user参数。
     * @return 是否满足条件。
     */
    public boolean deleteUser(String user) {

        if (user == null || DEFAULT_USER.equals(user))
            return false;

        String[] sql = new String[]{"delete from " + APPUSER_TABLENAME + " where USERNAME=?", "delete from " + CRED_TABLENAME + " where owner=?", "delete from " + USERACL_TABLENAME + " where username=?"};
        try (Connection conn = getConnection()) {
            try {

                for (int i = 0; i < sql.length; i++)
                    try (PreparedStatement pstmt = conn.prepareStatement(sql[i])) {
                        pstmt.setString(1, user);
                        pstmt.execute();
                        conn.commit();
                    }

                return true;
            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }


    /**
     * List all registered users
     *
     * @return
     */
    public List<AppUser> retrieveAllUsers() {
        String sql = "select * from " + APPUSER_TABLENAME + " order by username";
        ArrayList<AppUser> userList = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs != null && rs.next()) {
                AppUser user = new AppUser();
                user.setName(rs.getString("USERNAME"));
                //user.setMd5Hash(rs.getString("MD5HASH"));
                user.setUserprivilege(rs.getShort("USER_PRIVILEGE"));
                user.setEmail(rs.getString("EMAIL"));
                user.setVerified("1".equals(rs.getString("VERIFIED")));
                userList.add(user);
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return userList;
    }

    /**
     * @param owner
     * @param dbGroupName
     * @return
     */
    public DBCredential retrieveDBCredential(String owner, String dbGroupName) {
        try {

            if (!this.credCache.containsKey(owner + "||" + dbGroupName))
                try (Connection conn = getConnection()) {
                    DBCredential cred = retrieveDBCredential(conn, owner, dbGroupName);

                    if (cred != null)
                        this.credCache.put(owner + "||" + dbGroupName, cred);
                }

            DBCredential saved = this.credCache.get(owner + "||" + dbGroupName);

            if (//return a decrypted copy
                    saved != null) {
                saved = saved.copy();
                saved.decryptPassword(keyTool);
            }

            return saved;
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return null;
    }

    /**
     * 执行retrieve dbcredential操作。
     *
     * @param conn        conn参数。
     * @param owner       owner参数。
     * @param dbGroupName db group name参数。
     * @return 处理结果。
     */
    private DBCredential retrieveDBCredential(Connection conn, String owner, String dbGroupName) {
        String sql = (owner != null && owner.trim().length() > 0) ? "select * from " + CRED_TABLENAME + " where dbgroupname=?  and owner=?" : "select * from " + CRED_TABLENAME + " where dbgroupname=? and owner is null";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dbGroupName.toLowerCase());

            if (owner != null && owner.trim().length() > 0)
                pstmt.setString(2, owner);

            try (ResultSet rs = pstmt.executeQuery()) {

                if (rs != null && rs.next()) {
                    DBCredential cred = new DBCredential();
                    cred.setAppUser(owner);
                    cred.setDbGroupName(rs.getString("DBGROUPNAME"));
                    cred.setUsername(rs.getString("USERNAME"));
                    cred.setAppUser(rs.getString("OWNER"));
                    String credStringOrig = rs.getString("CREDENTIAL");
                    String credString = keyTool.decrypt(credStringOrig);
                    //we will use two :: as separator
                    int verified = rs.getInt("VERIFIED");

                    if (verified == 1) {
                        //credString = credString.substring(0, credString.lastIndexOf("::"));
                        //credString = credString.substring(credString.lastIndexOf("::")+2);
                        //cred.setPassword(credString);
                        cred.setEncrypted(credStringOrig);
                        return cred;
                    } else {
                        cred.setPassword(credString);
                        updateDBCredentialInternal(cred);
                        return retrieveDBCredential(conn, owner, dbGroupName);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return null;
    }

    /**
     * 执行rename db group name操作。
     *
     * @param oldName old name参数。
     * @param newName new name参数。
     */
    public void renameDbGroupName(String oldName, String newName) {
        //nothing to change
        if (newName == null || newName.equalsIgnoreCase(oldName))
            return;

        String sql1 = "select * from " + CRED_TABLENAME + " where dbgroupname=?";
        String sql2 = "insert into " + CRED_TABLENAME + " (owner,dbgroupname,username,credential,verified) values(?,?,?,?,1)";
        String sql3 = "delete from " + CRED_TABLENAME + " where dbgroupname=?";
        String sql4 = "select * from " + USERACL_TABLENAME + " where dbgroupname=?";
        String sql5 = "insert into " + USERACL_TABLENAME + " (username,dbgroupname,visible) values(?,?,?)";
        String sql6 = "delete from " + USERACL_TABLENAME + " where dbgroupname=?";
        Map<String, String> credMap = new HashMap<>();
        Map<String, Integer> aclMap = new HashMap<>();
        Map<String, String> credUserMap = new HashMap<>();
        try (Connection conn = getConnection()) {
            log.info("Rename db group: from " + oldName + " to " + newName);
            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                stmt.setString(1, oldName.toLowerCase());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs != null && rs.next()) {
                        String owner = rs.getString("OWNER");
                        String user = rs.getString("USERNAME");
                        String credString = rs.getString("CREDENTIAL");

                        if (credString != null) {
                            credString = keyTool.decrypt(credString);
                            credString = credString.substring(0, credString.lastIndexOf("::"));
                            credString = credString.substring(credString.lastIndexOf("::") + 2);
                            credMap.put(owner, credString);
                            //log.info("cred for "+owner+" is "+credString);
                            credUserMap.put(owner, user);
                        }
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql4)) {
                stmt.setString(1, oldName.toLowerCase());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs != null && rs.next()) {
                        String user = rs.getString("USERNAME");
                        int visible = rs.getInt("VISIBLE");
                        aclMap.put(user, visible);
                    }
                }
            }
            log.info("Update db group credentials: " + credUserMap.size());
            try (PreparedStatement stmt = conn.prepareStatement(sql2)) {

                for (Map.Entry<String, String> entry : credMap.entrySet()) {
                    credCache.remove(entry.getKey() + "||" + oldName.toLowerCase());
                    String owner = entry.getKey();
                    String pString = owner == null ? "NULL" : owner;
                    pString += "::" + newName.toLowerCase() + "::" + credUserMap.get(owner) + "::" + credMap.get(owner) + "::" + Math.random();
                    stmt.setString(1, owner);
                    stmt.setString(2, newName.toLowerCase());
                    stmt.setString(3, credUserMap.get(owner));
                    stmt.setString(4, this.keyTool.encrypt(pString));
                    stmt.execute();
                }
            }
            log.info("Update db acl: " + aclMap.size());
            try (PreparedStatement stmt = conn.prepareStatement(sql5)) {

                for (Map.Entry<String, Integer> entry : aclMap.entrySet()) {
                    stmt.setString(1, entry.getKey());
                    stmt.setString(2, newName.toLowerCase());
                    stmt.setInt(3, entry.getValue());
                    stmt.execute();
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql3)) {
                stmt.setString(1, oldName.toLowerCase());
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql6)) {
                stmt.setString(1, oldName.toLowerCase());
                stmt.execute();
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * 移除db group。
     *
     * @param grpName grp name参数。
     */
    public void removeDbGroup(String grpName) {
        String[] sql = new String[]{"delete from " + CRED_TABLENAME + " where dbgroupname=?", "delete from " + USERACL_TABLENAME + " where dbgroupname=?"};
        try (Connection conn = getConnection()) {

            for (int i = 0; i < sql.length; i++)
                try (PreparedStatement stmt = conn.prepareStatement(sql[i])) {
                    stmt.setString(1, grpName.toLowerCase());
                    stmt.execute();
                }

        } catch (Exception ex) {
            log.error("Exception", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * List database groups the specific user has provided passwords
     *
     * @param owner
     * @return
     */
    public List<String> listMyDBs(String owner, boolean restricted) {
        try (Connection conn = getConnection()) {
            return listMyDBs(conn, owner, restricted);
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return null;
    }

    /**
     * @param username
     * @param dbGroups the key is dbgroup name, the value is 1 or 0 (visible or not)
     */
    public void updateUserAcls(String username, Map<String, Integer> dbGroups) {
        try (Connection conn = getConnection()) {
            //first we will mask all existing db as not visible
            String sql = "update " + USERACL_TABLENAME + " set visible=0 where username=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.execute();
            }
            //Now lets grab the entries the user has so far
            sql = "select dbgroupname from " + USERACL_TABLENAME + " where username=?";
            HashSet<String> existingGroups;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    existingGroups = new HashSet<>();
                    while (rs != null && rs.next())
                        existingGroups.add(rs.getString(1));
                }
            }
            //first, let's update existing ones
            sql = "update " + USERACL_TABLENAME + " set visible=1 where username=? and dbgroupname=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                for (Map.Entry<String, Integer> e : dbGroups.entrySet()) {

                    if (e.getValue() == 0 || !existingGroups.contains(e.getKey()))
                        continue;

                    stmt.setString(1, username);
                    stmt.setString(2, e.getKey());
                    stmt.execute();
                }
            }
            //now new entries
            sql = "insert into " + USERACL_TABLENAME + " (username, dbgroupname, visible) values (?, ?, 1)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                for (Map.Entry<String, Integer> e : dbGroups.entrySet()) {

                    if (e.getValue() == 0 || existingGroups.contains(e.getKey()))
                        continue;

                    stmt.setString(1, username);
                    stmt.setString(2, e.getKey());
                    stmt.execute();
                }
            }
            conn.commit();
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
    }

    /**
     * Update visibility of a single dbgroup for a user
     *
     * @param username
     * @param dbgroupname
     * @param visible
     */
    public boolean updateUserAcl(String username, String dbgroupname, boolean visible) {
        try (Connection conn = getConnection()) {
            //Now lets grab the entries the user has so far
            String sql = "select dbgroupname from " + USERACL_TABLENAME + " where username=? and dbgroupname=?";
            boolean findOne;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, dbgroupname);
                try (ResultSet rs = stmt.executeQuery()) {
                    findOne = false;

                    if (rs != null && rs.next())
                        findOne = true;
                }
            }
            //first, let's update existing ones
            if (findOne) {
                sql = "update " + USERACL_TABLENAME + " set visible=? where username=? and dbgroupname=?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, visible ? 1 : 0);
                    stmt.setString(2, username);
                    stmt.setString(3, dbgroupname);
                    stmt.execute();
                }
            } else {
                //now new entries
                sql = "insert into " + USERACL_TABLENAME + " (username, dbgroupname, visible) values (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, dbgroupname);
                    stmt.setInt(3, visible ? 1 : 0);
                    stmt.execute();
                }
            }

            conn.commit();
            return true;
        } catch (Exception ex) {
            log.error("Exception", ex);
            return false;
        }
    }

    /**
     * 执行list my dbs操作。
     *
     * @param conn       conn参数。
     * @param owner      owner参数。
     * @param restricted restricted参数。
     * @return 处理结果。
     */
    private List<String> listMyDBs(Connection conn, String owner, boolean restricted) {
        String sql = "select dbgroupname from " + CRED_TABLENAME + " where owner=? order by dbgroupname";

        if (restricted)
            sql = "select dbgroupname from " + USERACL_TABLENAME + " where visible=1 and username=? order by dbgroupname";

        List<String> mydbs = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, owner);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs != null && rs.next()) {
                    mydbs.add(rs.getString("dbgroupname"));
                }
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
        }
        return mydbs;
    }

    /**
     * Store db credential per user/db base
     *
     * @param cred
     */
    public void upsertDBCredential(DBCredential cred) {

        if (cred == null)
            return;
        //bust our cached cred first
        if (this.credCache.containsKey(cred.getAppUser() + "||" + cred.getDbGroupName()))
            this.credCache.remove(cred.getAppUser() + "||" + cred.getDbGroupName());

        String sql1 = "update " + CRED_TABLENAME + " set username=?,credential=?,verified=1 where owner=? and dbgroupname=?";
        String sql2 = "insert into " + CRED_TABLENAME + " (owner,dbgroupname,username,credential,verified) values(?,?,?,?,1)";
        String pString = cred.getAppUser() == null ? "NULL" : cred.getAppUser();
        pString += "::" + cred.getDbGroupName() + "::" + cred.getUsername() + "::" + cred.getPassword() + "::" + Math.random();
        boolean findOne = false;
        try (Connection conn = getConnection()) {
            try {
                //first, check if we have record
                findOne = this.retrieveDBCredential(conn, cred.getAppUser(), cred.getDbGroupName()) != null;

                if (findOne)
                    try (PreparedStatement pstmt = conn.prepareStatement(sql1)) {
                        pstmt.setString(1, cred.getUsername());
                        pstmt.setString(2, keyTool.encrypt(pString));
                        pstmt.setString(3, cred.getAppUser());
                        pstmt.setString(4, cred.getDbGroupName().toLowerCase());
                        pstmt.execute();
                        conn.commit();
                    }
                else
                    try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                        pstmt.setString(1, cred.getAppUser());
                        pstmt.setString(2, cred.getDbGroupName().toLowerCase());
                        pstmt.setString(3, cred.getUsername());
                        pstmt.setString(4, keyTool.encrypt(pString));
                        pstmt.execute();
                        conn.commit();
                    }

            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * 移除dbcredential。
     *
     * @param owner       owner参数。
     * @param dbGroupName db group name参数。
     */
    public void removeDBCredential(String owner, String dbGroupName) {

        if (owner == null || owner.isEmpty())
            return;

        if (dbGroupName == null || dbGroupName.isEmpty())
            return;

        String sql = "delete from " + CRED_TABLENAME + " where  owner=? and dbgroupname=?";
        try (Connection conn = getConnection()) {
            try {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, owner);
                    pstmt.setString(2, dbGroupName.toLowerCase());
                    pstmt.execute();
                    conn.commit();
                }
                this.credCache.remove(owner + "||" + dbGroupName.toLowerCase());
            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * 更新dbcredential internal。
     *
     * @param cred cred参数。
     */
    private void updateDBCredentialInternal(DBCredential cred) {

        if (cred == null)
            return;

        String sql1 = "update " + CRED_TABLENAME + " set username=?,credential=?,verified=1 where owner=? and dbgroupname=?";
        String pString = cred.getAppUser() == null ? "NULL" : cred.getAppUser();
        pString += "::" + cred.getDbGroupName() + "::" + cred.getUsername() + "::" + cred.getPassword() + "::" + Math.random();
        try (Connection conn = getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql1)) {
                pstmt.setString(1, cred.getUsername());
                pstmt.setString(2, keyTool.encrypt(pString));
                pstmt.setString(3, cred.getAppUser());
                pstmt.setString(4, cred.getDbGroupName().toLowerCase());
                pstmt.execute();
                conn.commit();
            } catch (Exception transactionException) {

                if (conn != null)
                    try {
                        conn.rollback();
                    } catch (Exception iex) {
                    }

                throw transactionException;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
