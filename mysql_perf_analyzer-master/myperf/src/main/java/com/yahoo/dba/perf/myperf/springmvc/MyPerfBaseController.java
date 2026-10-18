/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.springmvc;

import com.yahoo.dba.perf.myperf.common.AppUser;
import com.yahoo.dba.perf.myperf.common.Constants;
import com.yahoo.dba.perf.myperf.common.MyPerfContext;
import com.yahoo.dba.perf.myperf.common.ResultListUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Base controller, includes some common functions, for example, to report
 * status and message from web calls.
 */
@Slf4j
public abstract class MyPerfBaseController extends AbstractController {

    //We use protected to allow subclass to access them directly
    /**
     * 保存framework context信息。
     */
    @Getter
    @Setter
    protected MyPerfContext frameworkContext;//configuration and resources
    /**
     * 保存nosess view信息。
     */
    @Getter
    @Setter
    protected String nosessView;//no session view
    /**
     * 保存form view信息。
     */
    @Getter
    @Setter
    protected String formView;//if it is going to display a form, form view
    /**
     * 保存json view信息。
     */
    @Getter
    @Setter
    protected String jsonView;//if a result will be sent back in json format
    /**
     * 标记require login状态。
     */
    @Getter
    @Setter
    private boolean requireLogin = true;//by default, we need login

    /**
     * 执行handle request internal操作。
     *
     * @param req  req参数。
     * @param resp resp参数。
     * @return 处理结果。
     */
    @Override
    protected ModelAndView handleRequestInternal(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        setupDebug(req);
        boolean debug = this.isDebug(req);

        if (debug) log.info("receive url path: " + req.getContextPath() + "," + req.getRequestURI() + ", "
                + req.getServletPath() + ", parameters: " + req.getQueryString());

        AppUser user = retrieveAppUser(req);

        if (this.requireLogin && user == null)//check if user valid when require login
        {
            if (this.nosessView != null)
                return new ModelAndView(new RedirectView(nosessView));

            return respondFailure("Session expired. Please relogin and try again.", req);
        } else if (this.requireLogin && !user.isVerified()) {
            //TODO use a message view
            return this.respondFailure("New user request has not been verified by administrator yet. Please contact administrator by email "
                            + this.frameworkContext.getMyperfConfig().getAdminEmail() + ".",
                    req);
        }

        return handleRequestImpl(req, resp);
    }

    //Create an MV to ack failure

    /**
     * 执行respond failure操作。
     *
     * @param message message参数。
     * @param req     req参数。
     * @return 处理结果。
     */
    protected ModelAndView respondFailure(String message, HttpServletRequest req) {
        return respondWithStatus(Constants.STATUS_BAD, message, req);

    }

    //create a simple response MV to ack success

    /**
     * 执行respond success操作。
     *
     * @param message message参数。
     * @param req     req参数。
     * @return 处理结果。
     */
    protected ModelAndView respondSuccess(String message, HttpServletRequest req) {
        return respondWithStatus(Constants.STATUS_OK, message, req);
    }

    //create a simple response MV to ack with provided status and message

    /**
     * 执行respond with status操作。
     *
     * @param status  status参数。
     * @param message message参数。
     * @param req     req参数。
     * @return 处理结果。
     */
    protected ModelAndView respondWithStatus(int status, String message, HttpServletRequest req) {
        ModelAndView mv = new ModelAndView(this.getJsonView());
        mv.addObject("json_result", "{\"resp\":{\"status\": " + status + ", \"message\": \""
                + ResultListUtil.escapeJson(message) + "\"}}");
        return mv;
    }

    //all subclasss need to implement this method

    /**
     * 执行handle request impl操作。
     *
     * @param req  req参数。
     * @param resp resp参数。
     * @return 处理结果。
     */
    protected abstract ModelAndView handleRequestImpl(HttpServletRequest req, HttpServletResponse resp) throws Exception;

    /**
     * Whether debug is enabled at session level
     *
     * @param req
     * @return
     */
    protected boolean isDebug(HttpServletRequest req) {
        //if system level use debug, return true
        if (frameworkContext.isDebug())
            return true;

        if (req.getSession() != null)
            try {

                if ("1".equals(req.getSession().getAttribute(Constants.SESSION_DEBUG)))
                    return true;

            } catch (Exception ex) {
            }


        return false;
    }

    /**
     * 设置up debug。
     *
     * @param req req参数。
     */
    private void setupDebug(HttpServletRequest req) {
        String paramString = req.getQueryString();

        if (paramString != null && (paramString.indexOf("debug=y") >= 0 || paramString.indexOf("DEBUG=Y") >= 0))
            req.getSession().setAttribute(Constants.SESSION_DEBUG, "1");
        else if (paramString != null && (paramString.indexOf("debug=n") >= 0 || paramString.indexOf("DEBUG=N") >= 0))
            req.getSession().removeAttribute(Constants.SESSION_DEBUG);

    }

    /**
     * 执行retrieve app user操作。
     *
     * @param req req参数。
     * @return 处理结果。
     */
    static protected AppUser retrieveAppUser(HttpServletRequest req) {
        return AppUser.class.cast(req.getSession().getAttribute(AppUser.SESSION_ATTRIBUTE));

    }

    /**
     * 查找user from request。
     *
     * @param req req参数。
     * @return 处理结果。
     */
    public static String findUserFromRequest(HttpServletRequest req) {

        if (req.getUserPrincipal() != null)
            return req.getUserPrincipal().getName();//find from security login JAAS

        if (req.getSession() != null)
            try {
                return retrieveAppUser(req).getName();//find from session
            } catch (Exception ex) {
            }

        return null;
    }

    /**
     * Date range in yyyyMMddHHmmss format, for metrics and alert retrieval.
     *
     * @param startDate
     * @param endDate
     * @return
     */
    public static String[] getDateRange(String startDate, String endDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");//metricsDB TS column format
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH");//URL request format
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d1 = null;

        if (startDate != null && !startDate.isEmpty())
            try {

                if (startDate.indexOf('-') < 0)
                    d1 = sdf.parse(startDate);
                else
                    d1 = sdf2.parse(startDate);

            } catch (Exception ex) {
            }

        //default to one day earlier
        if (d1 == null) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DATE, -1);
            d1 = c.getTime();
        }

        String startDateFmt = sdf.format(d1);

        Date d2 = null;

        if (endDate != null && !endDate.isEmpty())
            try {

                if (endDate.indexOf('-') < 0)
                    d2 = sdf.parse(endDate);
                else
                    d2 = sdf2.parse(endDate);

            } catch (Exception ex) {
            }

        //default to now
        if (d2 == null)
            d2 = new Date();

        String endDateFmt = sdf.format(d2);
        return new String[]{startDateFmt, endDateFmt};
    }
}
