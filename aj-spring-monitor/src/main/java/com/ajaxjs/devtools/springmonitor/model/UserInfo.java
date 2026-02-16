package com.ajaxjs.devtools.springmonitor.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息
 */
@Data
@NoArgsConstructor
public class UserInfo {
    private String salt;
    private String userName;
    private String password;
    private String userNameToken;

    public UserInfo(String salt, String userName, String password) {
        this.salt = salt;
        this.userName = userName;
        this.password = password;
    }
}
