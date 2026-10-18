/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.common;

import lombok.Getter;
import lombok.Setter;

import lombok.extern.slf4j.Slf4j;

import com.yahoo.dba.perf.myperf.db.QueryExecutor;
import com.yahoo.dba.perf.myperf.meta.MetaDB;
import com.yahoo.dba.perf.myperf.metrics.DerbyMetricsDb;
import com.yahoo.dba.perf.myperf.metrics.MetricsDbBase;
import com.yahoo.dba.perf.myperf.metrics.MySQLMetricsDb;
import com.yahoo.dba.perf.myperf.process.AutoScanner;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.File;
import java.sql.DriverManager;
import java.util.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/**
 * MyPerfContext contains configuration data, accessor to common resources,
 * and resource initialization.
 *
 * @author xrao
 */
@Slf4j
public class MyPerfContext implements Serializable, InitializingBean, DisposableBean {

    private static final long serialVersionUID = 1L;
    //Use this parameter to control debug log at system level at runtime
    //This way, we don't need change and load our log configuration
    private AtomicBoolean DEBUG = new AtomicBoolean();

    @Getter
    private MyPerfConfiguration myperfConfig = new MyPerfConfiguration(); //configurations, from settings page
    @Getter
    private SNMPSettings snmpSettings = new SNMPSettings();
    @Getter
    private DBInfoManager dbInfoManager = new DBInfoManager(); //managed database servers
    @Getter
    private SqlManager sqlManager = new SqlManager();//built in SQL definitions
    @Getter
    private MetricsDefManager metricsDef = new MetricsDefManager();//metrics and alert definitions, builtin and user defined
    @Getter
    private MetaDB metaDb = new MetaDB(); //meta data, now only hold users and db credentials
    @Getter
    private MetricsDbBase metricDb; //database to store metrics and managed database server info
    @Getter
    private QueryExecutor queryEngine = new QueryExecutor(); //Run queries using JDBC
    @Getter
    private AppUserManager userManager = new AppUserManager(); //managed registered users
    @Getter
    private Auth auth = new Auth();//user authentication

    @Getter
    private StatDefManager statDefManager = new StatDefManager();//help tool for terminology lookup

    @Getter
    private AutoScanner autoScanner = null; //metrics and alert scanner
    @Getter
    private AlertSettings alertSettings = new AlertSettings(); //alert thresholding settings

    //for each server, we keep two snapshots of selected metrics
    @Getter
    private InstanceStatesManager instanceStatesManager = new InstanceStatesManager();

    //location to find built in sql
    @Getter
    @Setter
    private String sqlPath = "sql.xml";

    //directory where we can save files, reports, etc.
    @Getter
    @Setter
    private String fileReposirtoryPath = "myperf_reports";
    @Getter
    @Setter
    private File alertRootPath;

    //database connection settings
    @Getter
    private long connectionIdleTime = 600000L;
    @Getter
    @Setter
    private long connectionTimeout = 5000L;//reduced to 5 seconds
    @Getter
    @Setter
    private long connectionReadTimeout = 10000L;
    @Getter
    @Setter
    private int queryTimeout = 300;
    @Getter
    @Setter
    private int queryFetchSize = 5000;

    //log settings
    @Getter
    @Setter
    private String logLevel = "INFO";
    @Getter
    @Setter
    private String logPath = "perf.log";
    @Getter
    @Setter
    private int logFileSize = 50000000;
    @Getter
    @Setter
    private int logFileCount = 5;

    @Getter
    @Setter
    private String configRepKey;

    @Getter
    @Setter
    private Map<String, String> jdbcDriver;

    @Getter
    private Date startTime = new Date();//when this server started

    //metrics list
    private List<String> metricsList;
    private Object metricsListRefreshLock = new Object();

    //hold alerts for notification
    @Getter
    private Alerts alerts = new Alerts();

    //attach HipchatIntegration to email alert
    @Getter
    private HipchatIntegration hipchat = new HipchatIntegration();

    public MyPerfContext() {
        DEBUG.set(false);
    }

    public void enableDebug(boolean enable) {
        DEBUG.set(enable);
    }

    public boolean isDebug() {
        return DEBUG.get();
    }

    /**
     * All the top level beans, sql manager, connection manage, db manager, etc, will be initialized here.
     */
    public void afterPropertiesSet() throws Exception {
        configureLogging();
        log.info("Setup afterPropertiesSet.");

        log.info("Loading JDBC Drivers");
        if (this.jdbcDriver != null) {
            DriverManager.setLoginTimeout(60);
            log.info("Set JDBC DriverManager loginTimeout as 60 seconds");
            for (Map.Entry<String, String> e : jdbcDriver.entrySet()) {
                log.info("Loading " + e.getKey() + ": " + e.getValue());
                try {
                    Class.forName(e.getValue());
                    log.info("Loaded " + e.getKey() + ": " + e.getValue());
                } catch (Throwable ex) {
                    log.info("Failed to Load " + e.getKey() + ": " + e.getValue());
                }
            }
        }

        alertRootPath = new File(new File(this.fileReposirtoryPath), "alerts");
        alertRootPath.mkdirs();

        this.sqlManager.setSqlPath(sqlPath);
        this.sqlManager.init();

        this.metricsDef.init();
        log.info("Refreshing metrics list ...");
        refreshMetricsList();
        log.info("Retrieved metrics list: " + this.metricsList.size());

        this.metaDb.setDbkey(this.configRepKey);
        this.metaDb.init();

        this.dbInfoManager.setMetaDb(metaDb);//since we store db info now in metricsdb,
        //it can only be initialized after metricsDB initialized

        this.userManager.setMetaDb(metaDb);
        this.auth.setContext(this);
        this.queryEngine.setSqlManager(this.sqlManager);
        this.queryEngine.setFrameworkContext(this);
        this.statDefManager.init();

        log.info("Initialize AutoScanner ...");
        this.myperfConfig.init(this);
        this.snmpSettings.init(this);
        if (this.myperfConfig.isConfigured()) {
            this.initMetricsDB(); //move metrics db creation and initialization away from scanner
            this.alertSettings.setContext(this);
            if (this.metricDb != null) {
                this.dbInfoManager.init(this.metricDb);//need metricsDB to update
                this.metricsDef.getUdmManager().loadSubscriptions(this);
                this.metricDb.loadAlertSetting(this.alertSettings);//load alert setting after DB info is loaded.
            }
        }
        this.instanceStatesManager.init(this);
        this.hipchat.init(this);

        autoScanner = new AutoScanner(this);
        autoScanner.init();//it will update metricsDB
        if (autoScanner.isInitialized()) {
            log.info("Starting AutoScanner ...");
            autoScanner.start();
        }

        log.info("Done setup afterPropertiesSet.");
    }

    public void destroy() throws Exception {
        if (this.autoScanner != null)
            this.autoScanner.stop();
        if (this.metricDb != null)
            this.metricDb.destroy();
        this.metaDb.destroy();
    }

    public void setConnectionIdleTime(long connectionIdleTime) {
        if (connectionIdleTime <= 0)
            this.connectionIdleTime = 600000L;
        else
            this.connectionIdleTime = connectionIdleTime;
    }

    private void configureLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        String configuredLevel = getLogLevel();
        if ("SEVERE".equals(configuredLevel))
            configuredLevel = "ERROR";
        else if ("WARNING".equals(configuredLevel)) configuredLevel = "WARN";
        else if ("CONFIG".equals(configuredLevel) || "FINE".equals(configuredLevel)) configuredLevel = "DEBUG";
        else if ("FINER".equals(configuredLevel) || "FINEST".equals(configuredLevel)) configuredLevel = "TRACE";
        rootLogger.setLevel(Level.toLevel(configuredLevel, Level.INFO));
        try {
            String logRoot = System.getProperty("logPath", ".");
            String filePath = new File(logRoot, getLogPath()).getPath();
            RollingFileAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new RollingFileAppender<>();
            appender.setContext(context);
            appender.setName("MYPERF_FILE");
            appender.setFile(filePath);

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger - %msg%n");
            encoder.start();
            appender.setEncoder(encoder);

            FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
            rollingPolicy.setContext(context);
            rollingPolicy.setParent(appender);
            rollingPolicy.setFileNamePattern(filePath + ".%i");
            rollingPolicy.setMinIndex(1);
            rollingPolicy.setMaxIndex(Math.max(1, this.logFileCount - 1));
            rollingPolicy.start();
            appender.setRollingPolicy(rollingPolicy);

            SizeBasedTriggeringPolicy<ch.qos.logback.classic.spi.ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
            triggeringPolicy.setContext(context);
            triggeringPolicy.setMaxFileSize(new FileSize(this.logFileSize));
            triggeringPolicy.start();
            appender.setTriggeringPolicy(triggeringPolicy);
            appender.start();
            rootLogger.detachAndStopAllAppenders();
            rootLogger.addAppender(appender);

            ch.qos.logback.core.ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> console = new ch.qos.logback.core.ConsoleAppender<>();
            console.setContext(context);
            console.setEncoder(encoder);
            ch.qos.logback.classic.filter.ThresholdFilter threshold = new ch.qos.logback.classic.filter.ThresholdFilter();
            threshold.setLevel("ERROR");
            threshold.start();
            console.addFilter(threshold);
            console.start();
            rootLogger.addAppender(console);
        } catch (Exception ex) {
            log.error("Failed to configure logging", ex);
        }
    }

    public void setMetricDbBase(MetricsDbBase metricDb) {
        this.metricDb = metricDb;
    }

    public List<String> getMetricsList() {
        synchronized (metricsListRefreshLock) {
            return Collections.unmodifiableList(metricsList);
        }
    }

    public void refreshMetricsList() {
        synchronized (metricsListRefreshLock) {
            if (metricsList == null)
                metricsList = new ArrayList<>();
            else
                metricsList.clear();
            //user defined:
            //udm list
            for (Map.Entry<String, UserDefinedMetrics> e :
                    this.getMetricsDef().getUdmManager().getUdms().entrySet()) {
                String key = e.getKey();
                UserDefinedMetrics udm = e.getValue();
                for (Metric m : udm.getMetrics())
                    metricsList.add("UDM." + key + "." + m.getName());
            }

            String[] mgNames = this.metricsDef.getGroupNames();
            for (String mgName : mgNames) {
                MetricsGroup mg = this.metricsDef.getGroupByName(mgName);
                if (mg == null)
                    continue;
                if (mg.getSubGroups() != null && mg.getSubGroups().size() > 0) {
                    //deal with sub groups
                    for (MetricsGroup subGrp : mg.getSubGroups()) {
                        for (Metric m : subGrp.getMetrics())
                            metricsList.add(mg.getGroupName().toUpperCase() + "."
                                    + subGrp.getGroupName() + "." + m.getName());
                    }
                } else {
                    //deal with top level metrics with name convention: {group name}._.{metric name}
                    for (Metric m : mg.getMetrics())
                        metricsList.add(mg.getGroupName().toUpperCase() + "._." + m.getName());
                }
            }
            Collections.sort(metricsList);
        }
    }

    public String getSqlTextForMetricsGroup(String groupName) {
        if (groupName == null)
            return null;
        String sql = null;

        MetricsGroup mg = getMetricsDef().getGroupByName(groupName);
        if (mg != null) {
            String sqlId = mg.getSql();
            if (sqlId != null) {
                Sql sqlObj = getSqlManager().getSql(sqlId);
                if (sqlObj != null)
                    sql = sqlObj.getSqlText();
            }
        }
        return sql;
    }

    public boolean initMetricsDB() {
        if (!this.getMyperfConfig().isConfigured())
            return false;
        if (this.metricDb != null)
            this.metricDb.destroy();
        //by default, use derby
        if ("derby".equalsIgnoreCase(getMyperfConfig().getMetricsDbType()))
            this.metricDb = new DerbyMetricsDb();
        else //otherwise, use mysql
        {
            this.metricDb = new MySQLMetricsDb();
            this.metricDb.setUsername(getMyperfConfig().getMetricsDbUserName());
            this.metricDb.setPassword(getMyperfConfig().getMetricsDbPassword());
            this.metricDb.setSchemaName(getMyperfConfig().getMetricsDbName());
            this.metricDb.setConnectionString("jdbc:mysql://"
                    + getMyperfConfig().getMetricsDbHost() + ":"
                    + getMyperfConfig().getMetricsDbPort() + "/"
                    + getMyperfConfig().getMetricsDbName()
                    + "?useSSL=true&enabledTLSProtocols=TLSv1.2&verifyServerCertificate=false");
        }
        this.metricDb.setFrameworkContext(this);
        this.metricDb.setMetricsGroups(this.getMetricsDef());
        this.metricDb.init();

        return true;
    }

    public void addAlert(AlertEntry alert) {
        try {
            if (!this.instanceStatesManager.getStates(
                            this.dbInfoManager.findDB(alert.getDbGroup(), alert.getDbHost()).getDbid())
                    .canSendWebNotification(alert.getTs(), alert.getAlertReason(), this.myperfConfig.getWebAlertIntervalMinutes())
            )
                return;
        } catch (Exception ex) {
        }
        alerts.addAlert(alert);
    }

    /**
     * helper method to create an alert report
     *
     * @param reportTimestamp
     * @param detecedTimestamp
     * @param dbGroupName
     * @param dbHost
     * @param alertReason
     * @param alertValue
     * @return
     */
    public AlertReport createAlertReport(long reportTimestamp,
                                         long detecedTimestamp,
                                         String dbGroupName,
                                         String dbHost,
                                         String alertReason,
                                         String alertValue
    ) {
        AlertReport ar = new AlertReport();
        ar.setReportTimestamp(reportTimestamp);
        ar.setDbGroupName(dbGroupName);
        ar.setDbHostName(dbHost);
        ar.setTimestamp(detecedTimestamp);
        ar.setRootPath(getAlertRootPath());
        ar.setAlertReason(alertReason);
        ar.setAlertValue(alertValue);
        return ar;
    }

    /**
     * Helper method to create an alert report
     *
     * @param reportTimestamp
     * @param alert
     * @return
     */
    public AlertReport createAlertReport(long reportTimestamp, AlertEntry alert) {
        AlertReport ar = new AlertReport();
        ar.setReportTimestamp(reportTimestamp);
        ar.setDbGroupName(alert.getDbGroup());
        ar.setDbHostName(alert.getDbHost());
        ar.setTimestamp(alert.getTs());
        ar.setRootPath(getAlertRootPath());
        ar.setAlertReason(alert.getAlertReason());
        ar.setAlertValue(alert.getAlertValue());
        return ar;
    }

    /**
     * helper method to send out alert
     * suppress alerts if similar alert was sent earlier
     *
     * @param alert
     */
    public void emailAlert(AlertEntry alert) {
        try {
            if (!this.instanceStatesManager.getStates(
                            this.dbInfoManager.findDB(alert.getDbGroup(), alert.getDbHost()).getDbid())
                    .canSendEmailNotification(alert.getTs(), alert.getAlertReason(), this.myperfConfig.getEmailAlertIntervalMinutes())
            )
                return;
        } catch (Exception ex) {
        }

        //String receiver = getMyperfConfig().getAlertNotificationEmails();
        String receiver = this.alertSettings.getNotificationEmails(alert.getDbGroup(), alert.getDbHost());
        if (receiver != null && !receiver.isEmpty()) {
            String subject = this.getAlertEmailSubject(alert);
            String msg = this.getAlertMessage(alert);
            //send alert to hipchat room if enabled
            this.hipchat.sendMessage(msg);
            MailUtil.sendMail(receiver, subject, msg);
        }

    }

    /**
     * Helper method to construct alert subject
     *
     * @param alert
     * @return
     */
    private String getAlertEmailSubject(AlertEntry alert) {
        return "MySQL Perf Analyzer Alert - " + alert.getAlertReason() + ": "
                + alert.getDbGroup() + " - " + alert.getDbHost();
    }

    /**
     * Helper method to format alert time
     *
     * @param alert
     * @return
     */
    private String getAlertEmailTime(AlertEntry alert) {
        long mytime = alert.getTs();
        if (mytime <= 0L)
            mytime = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return sdf.format(new Date(mytime));
        } catch (Exception ex) {

        }
        return "";
    }

    /**
     * Helper method to construct alert email message
     *
     * @param alert
     * @return
     */
    private String getAlertMessage(AlertEntry alert) {
        String newline = "\n";//mailx does not like \r
        StringBuilder sb = new StringBuilder();
        sb.append("Alert Reason: ").append(alert.getAlertReason()).append(" @ ").append(alert.getAlertValue()).append(newline);
        sb.append("Time        : ").append(getAlertEmailTime(alert)).append(newline);
        sb.append("DB Group    : ").append(alert.getDbGroup()).append(newline);
        sb.append("DB Host     : ").append(alert.getDbHost()).append(newline);
        return sb.toString();
    }

    public void saveManagedDBCredentialForScanner(String username, String dbGroup, String dbuser, String password) {
        AppUser defaultUser = getUserManager().getUser(MetaDB.DEFAULT_USER);
        AppUser metricsUser = getUserManager().getUser(getMyperfConfig().getMetricsScannerUser());
        if (!MetaDB.DEFAULT_USER.equals(username) && //save one db search
                DBUtils.findDBCredential(this, dbGroup, defaultUser) == null) {
            this.dbInfoManager.copyManagedDBCredential(defaultUser.getName(),
                    false, username, dbGroup, dbuser, password);
        }

        if (metricsUser != null && !MetaDB.DEFAULT_USER.equals(metricsUser.getName())
                && !username.equals(metricsUser.getName()) //save one db search
                && DBUtils.findDBCredential(this, dbGroup, metricsUser) == null) {
            this.dbInfoManager.copyManagedDBCredential(metricsUser.getName(),
                    false, username, dbGroup, dbuser, password);
        }

    }

    /**
     * Keep as reference. Remove later
     *
     * @param username
     * @param dbGroup
     * @param dbuser
     * @param password
     */
    public void saveManagedDBCredentialForScanner2(String username, String dbGroup, String dbuser, String password) {
        AppUser defaultUser = getUserManager().getUser(MetaDB.DEFAULT_USER);
        AppUser metricsUser = getUserManager().getUser(getMyperfConfig().getMetricsScannerUser());
        if (!MetaDB.DEFAULT_USER.equals(username) && //save one db search
                DBUtils.findDBCredential(this, dbGroup, defaultUser) == null) {
            DBCredential cred2 = new DBCredential();
            cred2.setAppUser(defaultUser.getName());
            cred2.setDbGroupName(dbGroup);
            cred2.setUsername(dbuser);
            cred2.setPassword(password);
            getMetaDb().upsertDBCredential(cred2);
            this.dbInfoManager.getMyDatabases(cred2.getAppUser(), false).addDb(cred2.getDbGroupName());
        }

        if (metricsUser != null && !MetaDB.DEFAULT_USER.equals(metricsUser.getName())
                && !username.equals(metricsUser.getName()) //save one db search
                && DBUtils.findDBCredential(this, dbGroup, metricsUser) == null) {
            DBCredential cred2 = new DBCredential();
            cred2.setAppUser(metricsUser.getName());
            cred2.setDbGroupName(dbGroup);
            cred2.setUsername(dbuser);
            cred2.setPassword(password);
            getMetaDb().upsertDBCredential(cred2);
            this.dbInfoManager.getMyDatabases(cred2.getAppUser(), false).addDb(cred2.getDbGroupName());
        }

    }

}
