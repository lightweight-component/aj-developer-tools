/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import com.yahoo.dba.perf.myperf.springmvc.WebAppUtil;
import lombok.Getter;
import lombok.Setter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * First we move all auth (authentication/authorization) related functions together
 * TODO add retry count
 *
 * @author xrao
 */
public class Auth {
    /**
     * 保存context信息。
     */
    @Getter
    @Setter
    private MyPerfContext context;

    /**
     * 创建Auth对象。
     */
    public Auth() {
    }

    /**
     * 创建Auth对象。
     *
     * @param ctx ctx参数。
     */
    public Auth(MyPerfContext ctx) {
        this.context = ctx;
    }


    /**
     * Find user by name. Return null if not found
     *
     * @param name
     * @return
     */
    public AppUser findUserByName(String name) {

        if (name == null || name.isEmpty())
            return null;

        String username = name.trim().toLowerCase();
        //find the user from the system cache
        AppUser appUser = this.context.getUserManager().getUser(username);

        if (appUser != null)
            return appUser;
        //find the user from db and add it to system cache
        appUser = this.context.getUserManager().retrieveUserInfoFromMetaDB(username);

        if (appUser != null)
            this.context.getUserManager().addUser(appUser);

        return appUser;
    }

    /**
     * 记录in。
     *
     * @param appUser app user参数。
     * @param request request参数。
     * @return 是否满足条件。
     */
    public boolean login(AppUser appUser, HttpServletRequest request) {

        if (appUser == null)
            return false;

        //check if login session timed out
        HttpSession sess = request.getSession();

        if (sess != null)//if no more session, we cannot log in
        {
            long stored_server_ts = System.currentTimeMillis();

            if (sess.getAttribute(AppUser.SERVER_TS) != null)
                stored_server_ts = (Long) sess.getAttribute(AppUser.SERVER_TS);

            int stored_seed = (int) (Math.random() * Integer.MAX_VALUE);

            if (sess.getAttribute(AppUser.RANDOM_SEED) != null)
                stored_seed = (Integer) sess.getAttribute(AppUser.RANDOM_SEED);

            boolean authed = appUser.match(request.getParameter("s"), stored_server_ts, stored_seed);

            if (authed) {
                String user = null;
                try {
                    user = WebAppUtil.findUserFromRequest(request);
                } catch (Exception ex) {
                }
                ;

                if (!appUser.getName().equalsIgnoreCase(user)) {
                    sess.invalidate();
                    sess = request.getSession(true);//get a new session
                }
            }

            sess.setAttribute(AppUser.SESSION_ATTRIBUTE, appUser);
            sess.removeAttribute(AppUser.SERVER_TS);
            sess.removeAttribute(AppUser.RANDOM_SEED);
            return authed;
        }

        return false;
    }

}
