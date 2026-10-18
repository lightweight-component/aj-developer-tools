/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import com.yahoo.dba.perf.myperf.meta.MetaDB;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 表示app user manager相关的类型。
 */
public class AppUserManager implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 保存user map集合。
     */
    private Map<String, AppUser> userMap = new HashMap<>();
    /**
     * 保存meta db信息。
     */
    @Getter
    @Setter
    private MetaDB metaDb; //to access meta db for persistent actions

    /**
     * 创建AppUserManager对象。
     */
    public AppUserManager() {
    }

    /**
     * get user by id
     *
     * @param name
     * @return
     */
    synchronized public AppUser getUser(String name) {

        if (name == null || name.isEmpty())
            return null;

        if (this.userMap.containsKey(name))
            return this.userMap.get(name);

        if (this.metaDb != null) {
            AppUser appUser = this.metaDb.retrieveUserInfo(name);

            if (appUser != null)
                this.addUser(appUser);

            return appUser;
        }

        return null;
    }

    /**
     * 添加user。
     *
     * @param user user参数。
     */
    synchronized public void addUser(AppUser user) {
        this.userMap.put(user.getName(), user);
    }

    /**
     * 获取user map。
     *
     * @return 处理结果。
     */
    public Map<String, AppUser> getUserMap() {
        return Collections.unmodifiableMap(this.userMap);
    }

    /**
     * 执行retrieve user info from meta db操作。
     *
     * @param username username参数。
     * @return 处理结果。
     */
    public AppUser retrieveUserInfoFromMetaDB(String username) {
        return metaDb.retrieveUserInfo(username);
    }

    /**
     * 执行store new password操作。
     *
     * @param username username参数。
     * @param newpwd   newpwd参数。
     * @return 是否满足条件。
     */
    public boolean storeNewPassword(String username, String newpwd) {
        return this.metaDb.changePasssword(username, newpwd);
    }

    /**
     * 更新password cache。
     *
     * @param username username参数。
     * @param passwd   passwd参数。
     */
    public void updatePasswordCache(String username, String passwd) {
        AppUser au = this.getUser(username.toLowerCase());

        if (au != null)
            au.setMd5Hash(au.calMd5(passwd));
    }

    /**
     * 执行store email操作。
     *
     * @param username username参数。
     * @param email    email参数。
     * @return 是否满足条件。
     */
    public boolean storeEmail(String username, String email) {
        return this.metaDb.changeEmail(username, email);
    }

    /**
     * 更新email cache。
     *
     * @param username username参数。
     * @param email    email参数。
     */
    public void updateEmailCache(String username, String email) {
        AppUser au = this.getUser(username.toLowerCase());

        if (au != null)
            au.setEmail(email);
    }

    /**
     * 执行store privilege操作。
     *
     * @param username username参数。
     * @param priv     priv参数。
     * @return 是否满足条件。
     */
    public boolean storePrivilege(String username, int priv) {
        return this.metaDb.changePrivilege(username, priv);
    }

    /**
     * 更新privilege cache。
     *
     * @param username username参数。
     * @param priv     priv参数。
     */
    public void updateprivilegeCache(String username, int priv) {
        AppUser au = this.getUser(username.toLowerCase());

        if (au != null)
            au.setUserprivilege(priv == AppUser.PRIV_USER_POWER ? AppUser.PRIV_USER_POWER : AppUser.PRIV_USER_STANDARD);
    }

    /**
     * 执行confirm new user操作。
     *
     * @param username  username参数。
     * @param confirmed confirmed参数。
     */
    public void confirmNewUser(String username, boolean confirmed) {

        if (this.metaDb.confirmUser(username, confirmed)) {
            AppUser au = this.getUser(username.toLowerCase());

            if (au != null)
                au.setVerified(confirmed);
        }
    }

    /**
     * 删除user。
     *
     * @param username username参数。
     * @return 是否满足条件。
     */
    public boolean deleteUser(String username) {
        boolean ret = this.metaDb.deleteUser(username);

        if (ret)
            this.userMap.remove(username);

        return ret;
    }

    /**
     * Add a new user. Return AppUser object if succeed. Otherwise null.
     *
     * @param userame
     * @param pwd
     * @param priv
     * @param email
     * @param confirmed If a user is added by admin, set it as confirmed
     * @return
     */
    public AppUser addNewUser(String username, String pwd, String priv, String email, boolean confirmed) {

        if (this.retrieveUserInfoFromMetaDB(username) != null)
            return null;

        AppUser newUser = new AppUser();
        newUser.setName(username.trim().toLowerCase());
        newUser.setPassword(pwd);
        newUser.setUserprivilege(AppUser.PRIV_USER_STANDARD);
        newUser.setEmail(email);
        newUser.setVerified(confirmed);
        try {
            int privInt = Integer.parseInt(priv);

            if (privInt == AppUser.PRIV_USER_POWER || privInt == AppUser.PRIV_USER_RESTRICTED)
                newUser.setUserprivilege(privInt);
            //otherwise standard
        } catch (Exception ex) {
        }
        newUser.setMd5Hash(newUser.calMd5(newUser.getPassword()));
        metaDb.upsertAppUser(newUser);//save to db
        this.addUser(newUser);//add to cache
        return newUser;
    }
}
