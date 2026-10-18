/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.springmvc;

import lombok.extern.slf4j.Slf4j;

import com.yahoo.dba.perf.myperf.common.*;
import com.yahoo.dba.perf.myperf.db.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.util.*;
import com.yahoo.dba.perf.myperf.snmp.SNMPQueryProcessor;

@Slf4j
public class QueryController extends MyPerfBaseController {
    private static Map<String, CustomQueryProcessor> CUSTOMER_PROCESSOR
            = new HashMap<>();

    private static Map<String, PostQueryResultProcessor> POST_PROCESSOR
            = new HashMap<>();

    static {
        //current implementation requires the customized processor to be specified in sql.xml
        CUSTOMER_PROCESSOR.put("TableMetaProcessor", new TableMetaProcessor());
        CUSTOMER_PROCESSOR.put("SNMPQueryProcessor", new SNMPQueryProcessor());
        CUSTOMER_PROCESSOR.put("ReplLagQueryProcessor", new ReplLagQueryProcessor());
        CUSTOMER_PROCESSOR.put("ReplShowProcessor", new ReplShowProcessor());
        CUSTOMER_PROCESSOR.put("MySQLStatusQueryProcessor", new MySQLStatusQueryProcessor());

        POST_PROCESSOR.put("mysql_innodb_mutex", new InnoDbMutexPostProccessor());
    }

    @Override
    protected ModelAndView handleRequestImpl(HttpServletRequest req,
                                             HttpServletResponse resp) throws Exception {
        int status = Constants.STATUS_OK;
        String message = "OK";

        log.info("receive url " + req.getQueryString());
        QueryParameters qps = null;
        DBInstanceInfo dbinfo = null;
        ModelAndView mv = null;
        ResultList rList = null;
        LinkedHashMap<String, ResultList> listMap = null; //if multiple result set

        qps = WebAppUtil.parseRequestParameter(req);
        //validation input
        String validation = qps.validate();
        if (validation == null || validation.isEmpty()) {
            //do we have such query?
            try {
                QueryInputValidator.validateSql(this.frameworkContext.getSqlManager(), qps);
            } catch (Exception ex) {
                validation = ex.getMessage();
            }
        }
        if (validation != null && !validation.isEmpty())
            return this.respondFailure("Input validation: " + validation, req);

        //valid DB?
        dbinfo = this.frameworkContext.getDbInfoManager().findDB(qps.getGroup(), qps.getHost());
        if (dbinfo == null)
            return this.respondFailure("Cannot find database (" + qps.getGroup() + ", " + qps.getHost() + ")", req);

        //create connection if needed
        DBConnectionWrapper connWrapper = null;
        Sql sql = this.frameworkContext.getSqlManager().getSql(qps.getSql());
        if (sql == null || sql.getQueryProcessor() == null
                || !CUSTOMER_PROCESSOR.containsKey(sql.getQueryProcessor())
                || CUSTOMER_PROCESSOR.get(sql.getQueryProcessor()).requireDBConnection()) {
            //connect to db
            try {
                connWrapper = WebAppUtil.getDBConnection(req, this.frameworkContext, dbinfo);

            } catch (Throwable th) {
                log.error("Exception", th);
                status = Constants.STATUS_BAD;
                return this.respondFailure("Failed to connecto to database (" + dbinfo + "): " + th.getMessage(), req);
            }
        }

        //when we reach here, at least we have valid query and can connect to db if needed
        WebAppUtil.storeLastDbInfoRequest(qps.getGroup(), qps.getHost(), req);

        try {
            log.info("execute query " + qps.getSql() + ", " + qps.getSqlParams());
            if (sql != null && sql.getQueryProcessor() != null
                    && CUSTOMER_PROCESSOR.containsKey(sql.getQueryProcessor())) //custom processor
            {
                CustomQueryProcessor prc = CUSTOMER_PROCESSOR.get(sql.getQueryProcessor());
                if (prc.isMultiple()) {
                    listMap = new LinkedHashMap<>();
                    prc.queryMultiple(frameworkContext, dbinfo, findUserFromRequest(req), connWrapper, qps, listMap);
                } else
                    rList = prc.querySingle(this.frameworkContext, dbinfo, findUserFromRequest(req), connWrapper, qps);
            } else
                rList = this.frameworkContext.getQueryEngine().executeQueryGeneric(qps, connWrapper, qps.getMaxRows());
            log.info("Done query " + qps.getSql() + " with " + (rList != null ? rList.getRows().size() : 0) + " records.");
            if (connWrapper != null)
                WebAppUtil.closeDBConnection(req, connWrapper, false, this.getFrameworkContext().getMyperfConfig().isReuseMonUserConnction());
        } catch (Throwable ex) {
            log.error("Exception", ex);
            if (connWrapper != null) {
                if (ex instanceof SQLException) {
                    SQLException sqlEx = SQLException.class.cast(ex);
                    String msg = ex.getMessage();
                    log.info(sqlEx.getSQLState() + ", " + sqlEx.getErrorCode() + ", " + msg);
                    //check if the connection is still good
                    if (!DBUtils.checkConnection(connWrapper.getConnection())) {
                        WebAppUtil.closeDBConnection(req, connWrapper, true, false);
                    } else
                        WebAppUtil.closeDBConnection(req, connWrapper, true, false);
                } else {
                    WebAppUtil.closeDBConnection(req, connWrapper, false, this.getFrameworkContext().getMyperfConfig().isReuseMonUserConnction());
                }
            }
            status = Constants.STATUS_BAD;
            message = "Exception: " + ex.getMessage();
        }

        if (status != Constants.STATUS_OK)
            return this.respondFailure(message, req);

        if (rList != null && POST_PROCESSOR.containsKey(qps.getSql()))
            rList = POST_PROCESSOR.get(qps.getSql()).process(rList);

        mv = new ModelAndView(this.jsonView);
        if (listMap != null)
            mv.addObject("json_result", ResultListUtil.toMultiListJSONStringUpper(listMap, qps, status, message));
        else
            mv.addObject("json_result", ResultListUtil.toJSONString(filterResultList(rList, req), qps, status, message));
        return mv;
    }

    /**
     * If filter condition is provided, filter the results
     * Currently we only support single column based filtering
     * with req parameter rf=col&rfv=value&rfv=value....
     * If provided, only result satisfying the condition (eq IN LIST) will be returned.
     * For now, we allow case insensitive
     *
     * @param rList
     * @param req
     */
    private ResultList filterResultList(ResultList rList, HttpServletRequest req) {
        try {
            String rf = req.getParameter("rf");
            if (rf == null || rf.isEmpty())
                return rList;
            int idx = rList.getColumnIndex(rf);
            if (idx < 0)
                return rList;

            String[] filtered_vals = req.getParameterValues("rfv");
            if (filtered_vals == null || filtered_vals.length == 0)
                return rList;
            Set<String> filteredSet = new HashSet<>(filtered_vals.length);
            for (String s : filtered_vals)
                filteredSet.add(s.toLowerCase());

            ResultList newList = new ResultList();
            newList.setColumnDescriptor(rList.getColumnDescriptor());
            for (ResultRow row : rList.getRows()) {
                String v = row.getColumns().get(idx);
                if (v != null && filteredSet.contains(v.toLowerCase()))
                    newList.addRow(row);
            }
            return newList;
        } catch (Exception ex) {
            log.info("Failed to filter data", ex);
        }
        return rList;
    }
}
