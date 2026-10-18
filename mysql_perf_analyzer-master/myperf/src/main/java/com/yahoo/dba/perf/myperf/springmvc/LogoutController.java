/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.springmvc;

import com.yahoo.dba.perf.myperf.db.UserDBConnections;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 表示logout controller相关的类型。
 */
public class LogoutController extends MyPerfBaseController {

    /**
     * 执行handle request impl操作。
     *
     * @param req  req参数。
     * @param resp resp参数。
     * @return 处理结果。
     */
    @Override
    protected ModelAndView handleRequestImpl(HttpServletRequest req,
                                             HttpServletResponse resp) throws Exception {
        HttpSession sess = req.getSession();

        //do we have session
        if (sess != null) {
            UserDBConnections conns = UserDBConnections.class.cast(sess.getAttribute("UserDBConnections"));
            sess.removeAttribute("UserDBConnections");
            sess.invalidate();

            new Thread(new LogoutCleaner(conns)).start();//make it async.
            //TODO Add the thread handle for central process
        }

        ModelAndView mv = new ModelAndView(new RedirectView(this.getNosessView()));
        return mv;
    }

    /**
     * 表示logout cleaner相关的类型。
     */
    private static class LogoutCleaner implements Runnable {
        /**
         * 保存conns信息。
         */
        private UserDBConnections conns;

        /**
         * 创建LogoutCleaner对象。
         *
         * @param conns conns参数。
         */
        public LogoutCleaner(UserDBConnections conns) {
            this.conns = conns;
        }

        /**
         * 执行run操作。
         */
        public void run() {

            if (conns != null) {
                conns.setValid(false);
                conns.close();
            }
        }
    }
}
