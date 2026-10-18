/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import com.yahoo.dba.perf.myperf.meta.KeyTool;
import java.io.Serializable;

/**
 * Database access user name and password
 *
 * @author xrao
 */
public class DBCredential implements Serializable {

    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    private String appUser;//the user stores this db username/password pair
    @Getter
    @Setter
    private String username;//db username
    @Getter
    @Setter
    private String password;//db password
    @Getter
    @Setter
    private String dbGroupName;//the name of the database group

    @Getter
    @Setter
    private String encrypted; //encypted password

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
