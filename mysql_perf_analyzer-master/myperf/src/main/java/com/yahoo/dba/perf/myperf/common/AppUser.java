/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * an AppUser is the user who is going to use this app.
 *
 * @author xrao
 */
public class AppUser implements Serializable {
    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;
    /**
     * 定义priv user standard常量。
     */
    public static final int PRIV_USER_STANDARD = 0; //standard user.
    /**
     * 定义priv user power常量。
     */
    public static final int PRIV_USER_POWER = 1;    //power user, can manage this app.
    /**
     * 定义priv user restricted常量。
     */
    public static final int PRIV_USER_RESTRICTED = 2;    //restricted user, can only access database groups assgined by admin user.
    /**
     * 定义session attribute常量。
     */
    public static final String SESSION_ATTRIBUTE = "APP_USER"; //session handle for AppUser
    /**
     * 定义server ts常量。
     */
    public static final String SERVER_TS = "SERVER_TS"; //server timestamp, use for login
    /**
     * 定义random seed常量。
     */
    public static final String RANDOM_SEED = "RANDOM_SEED"; //random seed


    /**
     * 保存name信息。
     */
    @Getter
    @Setter
    private String name;//user name
    /**
     * 保存md5 hash信息。
     */
    @Getter
    @Setter
    private String md5Hash;//password hash to login to this site.
    /**
     * 保存password信息。
     */
    @Getter
    @Setter
    private String password;//not really used
    /**
     * 保存userprivilege信息。
     */
    @Getter
    @Setter
    private int userprivilege = PRIV_USER_STANDARD;//or uset type
    /**
     * 保存email信息。
     */
    @Getter
    @Setter
    private String email;
    /**
     * 标记verified状态。
     */
    @Getter
    @Setter
    private boolean verified = false; //for newly registered user

    /**
     * 创建AppUser对象。
     */
    public AppUser() {
    }

    /**
     * 创建AppUser对象。
     *
     * @param name name参数。
     */
    public AppUser(String name) {
        this.name = name;
    }

    /**
     * 执行match操作。
     *
     * @param pwd pwd参数。
     * @return 是否满足条件。
     */
    public boolean match(String pwd) {
        try {
            return this.md5Hash.equals(MD5Util.MD5(this.name + ":" + pwd));
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 执行match操作。
     *
     * @param hash hash参数。
     * @param ts   ts参数。
     * @param seed seed参数。
     * @return 是否满足条件。
     */
    public boolean match(String hash, long ts, int seed) {

        if (hash == null)
            return false;

        try {
            return hash.equals(MD5Util.MD5(ts + ":" + this.md5Hash + ":" + seed));
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 执行cal md5操作。
     *
     * @param pwd pwd参数。
     * @return 处理结果。
     */
    public String calMd5(String pwd) {
        try {
            return MD5Util.MD5(this.name + ":" + pwd);
        } catch (Exception ex) {
            return "0";
        }
    }

    /**
     * 判断admin user。
     *
     * @return 是否满足条件。
     */
    public boolean isAdminUser() {
        return this.userprivilege == PRIV_USER_POWER;
    }

    /**
     * 判断restricted user。
     *
     * @return 是否满足条件。
     */
    public boolean isRestrictedUser() {
        return this.userprivilege == PRIV_USER_RESTRICTED;
    }

    /**
     * 执行match password操作。
     *
     * @param pd pd参数。
     * @return 是否满足条件。
     */
    public boolean matchPassword(String pd) {

        if (pd == null)
            return false;//we always need password

        return this.calMd5(pd).equals(this.md5Hash);
    }

    /**
     * 定义letters常量。
     */
    private final static String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Utility to generate random password
     *
     * @return
     */
    public static String geterateRandomPwd() {
        char mypwd[] = new char[8];
        char[] chars = LETTERS.toCharArray();

        for (int i = 0; i < 8; i++)
            mypwd[i] = chars[(int) (Math.floor(Math.random() * chars.length))];

        return new String(mypwd);
    }

}
