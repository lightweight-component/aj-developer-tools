/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import com.yahoo.dba.perf.myperf.meta.KeyTool;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Database access user name and password
 *
 * @author xrao
 */
public class DBCredential implements Serializable {

    /**
     * 定义serial version uid常量。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 保存app user信息。
     */
    @Getter
    @Setter
    private String appUser;//the user stores this db username/password pair
    /**
     * 保存username信息。
     */
    @Getter
    @Setter
    private String username;//db username
    /**
     * 保存password信息。
     */
    @Getter
    @Setter
    private String password;//db password
    /**
     * 保存db group name信息。
     */
    @Getter
    @Setter
    private String dbGroupName;//the name of the database group

    /**
     * 保存encrypted信息。
     */
    @Getter
    @Setter
    private String encrypted; //encypted password

    /**
     * 创建DBCredential对象。
     */
    public DBCredential() {
    }

    /**
     * Retrun a copy
     *
     * @return
     */
    public DBCredential copy() {
        DBCredential cred = new DBCredential();
        cred.setAppUser(this.appUser);
        cred.setDbGroupName(this.dbGroupName);
        cred.setUsername(this.username);
        cred.setPassword(this.password);
        cred.setEncrypted(encrypted);
        return cred;
    }

    /**
     * Invoke this method if password is encrypted
     *
     * @param keyTool
     */
    public void decryptPassword(KeyTool.DesEncrypter keyTool) {

        if (encrypted == null)
            return;

        String credString = keyTool.decrypt(encrypted);
        credString = credString.substring(0, credString.lastIndexOf("::"));
        credString = credString.substring(credString.lastIndexOf("::") + 2);
        this.password = credString;
    }
}
