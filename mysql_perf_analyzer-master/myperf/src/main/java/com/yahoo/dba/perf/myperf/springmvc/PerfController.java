/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.springmvc;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * A common controller to display different JSP pages
 *
 * @author xrao
 */
@Slf4j
public class PerfController extends MyPerfBaseController {
    private static String HOURS[] = {"00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
            "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "20", "21", "22", "23"};
    @Getter
    @Setter
    private String defaultView;//if no page is supplied, display default, should not require session
    //private String sessExpireView;//for page requires session
    @Getter
    @Setter
    private Map<String, String> pageMap;//page requires session, key in lower case
    @Getter
    @Setter
    private Map<String, String> noSessionPageMap;//pages not require session, key in lower case
    //private MyPerfContext frameworkContext;

    @Override
    protected ModelAndView handleRequestImpl(HttpServletRequest request,
                                             HttpServletResponse response) throws Exception {
        String pg = request.getParameter("pg");
        String tgtPage = pg;
        if (tgtPage != null)
            tgtPage = tgtPage.trim().toLowerCase();
        if (tgtPage == null || tgtPage.isEmpty() || (!pageMap.containsKey(tgtPage) && !noSessionPageMap.containsKey(tgtPage)))
            tgtPage = this.defaultView;
        else if (pageMap.containsKey(tgtPage) && !WebAppUtil.hasValidSession(request)) {
            return new ModelAndView(new RedirectView(this.getNosessView()));
        } else if (pageMap.containsKey(tgtPage)) {
            tgtPage = pageMap.get(tgtPage);
        } else
            tgtPage = noSessionPageMap.get(tgtPage);

        ModelAndView mv = new ModelAndView(tgtPage);

        if (WebAppUtil.hasValidSession(request)) {
            mv.addObject("mydbs", this.frameworkContext.getDbInfoManager()
                    .listDbsByUserInfo(WebAppUtil.findUserFromRequest(request), retrieveAppUser(request).isRestrictedUser()));
            mv.addObject("mydbSize", this.frameworkContext.getDbInfoManager()
                    .getMyDatabases(WebAppUtil.findUserFromRequest(request), retrieveAppUser(request).isRestrictedUser()).size());
        } else {
            mv.addObject("mydbs", this.frameworkContext.getDbInfoManager().getClusters().keySet());
            mv.addObject("mydbSize", 0);
        }
        mv.addObject("hours", HOURS);
        mv.addObject("dbMap", this.frameworkContext.getDbInfoManager().getClusters());
        mv.addObject("help_key", pg);
        mv.addObject("config", this.frameworkContext.getMyperfConfig());
        mv.addObject("scanner_running", this.frameworkContext.getAutoScanner().isRunning());
        mv.addObject("setup", this.frameworkContext.getMyperfConfig().isConfigured() ? 1 : 0);

        if ("sp".equals(pg) || "scatterplot".equals(pg) || "m".equals(pg) || "metrics".equalsIgnoreCase(pg))
            mv.addObject("udms", this.frameworkContext.getMetricsList());
        return mv;

    }

}
