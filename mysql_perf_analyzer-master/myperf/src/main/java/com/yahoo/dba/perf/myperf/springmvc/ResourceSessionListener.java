/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.springmvc;

import com.yahoo.dba.perf.myperf.common.AppUser;
import com.yahoo.dba.perf.myperf.db.UserDBConnections;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * 表示resource session listener相关的类型。
 */
@Slf4j
public class ResourceSessionListener implements HttpSessionListener {
    /**
     * 执行session created操作。
     *
     * @param evt evt参数。
     */
    public void sessionCreated(HttpSessionEvent evt) {
    }

    /**
     * 执行session destroyed操作。
     *
     * @param evt evt参数。
     */
    public void sessionDestroyed(HttpSessionEvent evt) {
        AppUser appUser = AppUser.class.cast(evt.getSession().getAttribute(AppUser.SESSION_ATTRIBUTE));
        UserDBConnections conns = UserDBConnections.class.cast(evt.getSession().getAttribute("UserDBConnections"));

        if (conns != null) {

            if (appUser != null)
                log.info("Session destroyed. Close all connections held by " + appUser.getName());

            conns.setValid(false);
            conns.close();
            evt.getSession().removeAttribute("UserDBConnections");
        }
    }
}
