/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.dba.perf.myperf.process;

import com.yahoo.dba.perf.myperf.common.AppUser;
import com.yahoo.dba.perf.myperf.common.DBInstanceInfo;
import com.yahoo.dba.perf.myperf.common.MetricsBuffer;
import com.yahoo.dba.perf.myperf.common.MyPerfContext;
import com.yahoo.dba.perf.myperf.metrics.MetricsDbBase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This will automate AWRScanner and LongRunSqlScanner. The result can be accessed from
 * FrameworkContext
 */
@Slf4j
@RequiredArgsConstructor
public class AutoScanner {
    //	public static final char lineSeparator = '\n';
    /**
     * 定义scheduler metrics常量。
     */
    private static final String SCHEDULER_METRICS = "METRICS";
    /**
     * 定义scheduler alert常量。
     */
    private static final String SCHEDULER_ALERT = "ALERT";
    /**
     * 定义scheduler retention常量。
     */
    private static final String SCHEDULER_RETENTION = "RETENTION";
    /**
     * 定义scheduler monitor常量。
     */
    private static final String SCHEDULER_MONITOR = "MONITOR";

    /**
     * 保存context信息。
     */
    @Getter
    private final MyPerfContext context;
    /**
     * 保存app user信息。
     */
    private AppUser appUser;//The scanner will run as this user and use this user's credentials
    //If the user changed, the scanner needs to be restarted.

    /**
     * 标记initialized状态。
     */
    private boolean initialized = false;//if the scanner is initialized
    /**
     * 保存initialization message信息。
     */
    private String initializationMessage = "OK";//initialization message
    /**
     * 标记running状态。
     */
    private boolean running = false;//if the scanner is running

    /**
     * 保存metric db信息。
     */
    @Getter
    @Setter
    private MetricsDbBase metricDb; //let AutoScanner to handle its initialization

    //scheduler
    /**
     * 保存metrics scheduler信息。
     */
    private ScheduledExecutorService metricsScheduler;
    /**
     * 保存alert scheduler信息。
     */
    private ScheduledExecutorService alertScheduler;
    /**
     * 保存scheduler2信息。
     */
    private ScheduledExecutorService scheduler2;
    /**
     * 保存monitor scheduler信息。
     */
    private ScheduledExecutorService monitorScheduler; //monitor if the scanner hangs

    /**
     * 保存metrics task信息。
     */
    private MetricsScanTask metricsTask = null;
    /**
     * 保存alert task信息。
     */
    private AlertScanTask alertTask = null;
    /**
     * 保存retention task信息。
     */
    private MetricsRetentionTask retentionTask = null;
    /**
     * 保存config scan task信息。
     */
    private GlobalVariableChangeScanTask configScanTask = null;
    /**
     * 保存monitor task信息。
     */
    private MonitorTask monitorTask = null;

    //store data. the first key is dbgroupname;dbhost, the second key is metrics group name
    /**
     * 保存buffer信息。
     */
    private HashMap<String, Map<String, MetricsBuffer>> buffer = new HashMap<>();

    /**
     * 标记first scan状态。
     */
    private boolean firstScan = true;//we just started
    /**
     * 保存last scan time信息。
     */
    private long lastScanTime = System.currentTimeMillis();
    /**
     * 保存last alert scan time信息。
     */
    private long lastAlertScanTime = System.currentTimeMillis();
    /**
     * 保存max scan idle time信息。
     */
    private long maxScanIdleTime = 3600000L;

    /**
     * 执行init操作。
     */
    synchronized public void init() {

        if (!this.initialized) {
            // Read and verify configuration. Especially the user
            if (!this.context.getMyperfConfig().isConfigured()) {
                this.initializationMessage = "Cannot read configuration file.";
                log.warn(this.initializationMessage + " The auto scanner has yet to configure.");

                return;
            }
        }

        String scanuser = context.getMyperfConfig().getMetricsScannerUser().toLowerCase();
        this.appUser = context.getUserManager().getUser(scanuser);

        if (this.appUser == null) {
            this.initializationMessage = "Invalid user specified for scanner: " + scanuser;
            log.warn(this.initializationMessage);

            return;
        }

        this.metricDb = context.getMetricDb();

        this.initialized = true;
        this.initializationMessage = "OK";
    }

    /**
     * 判断initialized。
     *
     * @return 是否满足条件。
     */
    synchronized public boolean isInitialized() {
        return this.initialized;
    }

    /**
     * 获取initialization message。
     *
     * @return 处理结果。
     */
    synchronized public String getInitializationMessage() {
        return this.initializationMessage;
    }

    /**
     * 判断running。
     *
     * @return 是否满足条件。
     */
    synchronized public boolean isRunning() {
        return this.running;
    }

    /**
     * 执行start操作。
     */
    synchronized public void start() {

        if (!this.initialized)
            return;

        if (!running) {
            this.running = true;
            startSchedulers();
        }
    }

    /**
     * 启动metric scheduler。
     */
    private void startMetricScheduler() {
        metricsScheduler = Executors.newSingleThreadScheduledExecutor();//we only need a single thread
        metricsTask = new MetricsScanTask();

        int secOfTheDay = getCurrentSeconds();
        int interval = context.getMyperfConfig().getScannerIntervalSeconds();
        maxScanIdleTime = interval * 3000L;

        if (maxScanIdleTime < 300000L)
            maxScanIdleTime = 300000L;//minimum check time 5 minutes

        log.info("maximun alowed hange time: " + maxScanIdleTime);
        int metricsDelay = (int) Math.ceil(((double) secOfTheDay) / (interval)) * interval - secOfTheDay;
        //configDelay2 = 10;
        ScheduledFuture<?> metricsTaskFuture = metricsScheduler.scheduleAtFixedRate(metricsTask,
                metricsDelay, interval, TimeUnit.SECONDS);

        log.info("AutoScanner started: runtime scan for each " + interval
                + " seconds with delay of  " + metricsDelay + " seconds, run as user "
                + context.getMyperfConfig().getMetricsScannerUser());
    }

    /**
     * 启动alert scheduler。
     */
    private void startAlertScheduler() {
        alertScheduler = Executors.newSingleThreadScheduledExecutor();//we only need a single thread
        alertTask = new AlertScanTask();

        int secOfTheDay = getCurrentSeconds();
        int interval = context.getMyperfConfig().getScannerIntervalSeconds();
        maxScanIdleTime = interval * 3000L;

        if (maxScanIdleTime < 300000L)
            maxScanIdleTime = 300000L;//minimum check time 5 minutes

        log.info("maximun alowed hange time: " + maxScanIdleTime);

        int alertDelay = (int) Math.ceil(((double) secOfTheDay) / (context.getMyperfConfig().getAlertScanIntervalSeconds())) * context.getMyperfConfig().getAlertScanIntervalSeconds() - secOfTheDay;
        ScheduledFuture<?> alertTaskFuture = alertScheduler.scheduleAtFixedRate(alertTask,
                alertDelay, context.getMyperfConfig().getAlertScanIntervalSeconds(), TimeUnit.SECONDS);
        log.info("AlertScanner started: runtime scan for each " + context.getMyperfConfig().getAlertScanIntervalSeconds()
                + " seconds with delay of  " + alertDelay + " seconds, run as user "
                + context.getMyperfConfig().getMetricsScannerUser());
    }

    /**
     * 启动config retention scheduler。
     */
    private void startConfigRetentionScheduler() {
        scheduler2 = Executors.newSingleThreadScheduledExecutor();//we only need a single thread
        retentionTask = new MetricsRetentionTask(this.context, context.getMyperfConfig().getRecordRententionDays(), null);
        configScanTask = new GlobalVariableChangeScanTask(this.context, this.appUser);

        int secOfTheDay = getCurrentSeconds();
        int interval = context.getMyperfConfig().getScannerIntervalSeconds();
        maxScanIdleTime = interval * 3000L;

        if (maxScanIdleTime < 300000L)
            maxScanIdleTime = 300000L;//minimum check time 5 minutes

        log.info("maximun alowed hange time: " + maxScanIdleTime);

        int configDelay = (int) Math.ceil(((double) secOfTheDay) / (720 * 60)) * 720 * 60 - secOfTheDay;
        int configDelay2 = (int) Math.ceil(((double) secOfTheDay) / (1440 * 60)) * 1440 * 60 - secOfTheDay;
        int monitorDelay = (int) Math.ceil(((double) secOfTheDay) / (600)) * 600 - secOfTheDay; //monitor delay
        ScheduledFuture<?> runtimeTaskFuture2 = scheduler2.scheduleAtFixedRate(retentionTask,
                configDelay2 + 60, 24 * 3600, TimeUnit.SECONDS);//once a day
        ScheduledFuture<?> runtimeTaskFuture3 = scheduler2.scheduleAtFixedRate(configScanTask,
                configDelay + 120, 12 * 3600, TimeUnit.SECONDS);//twice a day
        log.info("Rentention Task and configuratiion scan task scheduled.");
    }

    /**
     * 启动monitor scheduler。
     */
    private void startMonitorScheduler() {
        this.monitorScheduler = Executors.newScheduledThreadPool(2);

        monitorTask = new MonitorTask();

        int secOfTheDay = getCurrentSeconds();
        int interval = context.getMyperfConfig().getScannerIntervalSeconds();
        maxScanIdleTime = interval * 3000L;

        if (maxScanIdleTime < 300000L)
            maxScanIdleTime = 300000L;//minimum check time 5 minutes

        log.info("maximun alowed hange time: " + maxScanIdleTime);

        int monitorDelay = (int) Math.ceil(((double) secOfTheDay) / (600)) * 600 - secOfTheDay; //monitor delay
        //configDelay2 = 10;

        ScheduledFuture<?> monitorTaskFuture = this.monitorScheduler.scheduleAtFixedRate(monitorTask,
                monitorDelay, 600, TimeUnit.SECONDS);//each 10 minutes
    }

    /**
     * 启动schedulers。
     */
    private void startSchedulers() {
        this.startMetricScheduler();
        this.startAlertScheduler();
        this.startConfigRetentionScheduler();
        this.startMonitorScheduler();
    }

    /**
     * 获取current seconds。
     *
     * @return 处理结果。
     */
    private static int getCurrentSeconds() {
        Calendar rightNow = Calendar.getInstance();
        int h = rightNow.get(Calendar.HOUR_OF_DAY);
        int m = rightNow.get(Calendar.MINUTE);
        int s = rightNow.get(Calendar.SECOND);

        return h * 3600 + 60 * m + s;
    }

    /**
     * This will stop scheduler, but not the task already running
     */
    synchronized public void stop() {
        //stop the scheduler
        log.info("Stopping AutoScanner");
        forceStop();
        forceStopScheduler(SCHEDULER_MONITOR);
        this.running = false;
        this.metricDb.destroy();
        log.info("AutoScanner Stopped.");
    }

    /**
     * 执行force stop scheduler操作。
     *
     * @param name name参数。
     */
    synchronized public void forceStopScheduler(String name) {
        ScheduledExecutorService svc = null;

        if (SCHEDULER_METRICS.equals(name))
            svc = this.metricsScheduler;
        else if (SCHEDULER_ALERT.equals(name))
            svc = this.alertScheduler;

        if (SCHEDULER_RETENTION.equals(name))
            svc = this.scheduler2;

        if (SCHEDULER_MONITOR.equals(name))
            svc = this.monitorScheduler;

        if (svc == null)
            return;

        try {
            log.info("Shutdown scheduler " + name);
            svc.shutdownNow();
            svc.awaitTermination(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.info("AutoScanner failed to shutdown in 5 sec.");
        }
    }

    /**
     * This will stop the scheduler and scanner tasks
     */
    synchronized public void forceStop() {
        forceStopScheduler(SCHEDULER_ALERT);
        forceStopScheduler(SCHEDULER_METRICS);
        forceStopScheduler(SCHEDULER_RETENTION);
    }

    /**
     * 获取buffer by db。
     *
     * @param dbName          db name参数。
     * @param hostName        host name参数。
     * @param metricGroupName metric group name参数。
     * @return 处理结果。
     */
    public MetricsBuffer getBufferByDB(String dbName, String hostName, String metricGroupName) {

        if (this.buffer.containsKey(dbName + ":" + hostName))
            if (this.buffer.get(dbName + ":" + hostName).containsKey(metricGroupName))
                return this.buffer.get(dbName + ":" + hostName).get(metricGroupName);


        return null;
    }

    /**
     * 获取buffer by db。
     *
     * @param db              db参数。
     * @param metricGroupName metric group name参数。
     * @return 处理结果。
     */
    public MetricsBuffer getBufferByDB(DBInstanceInfo db, String metricGroupName) {
        return getBufferByDB(db.getDbGroupName(), db.getHostName(), metricGroupName);
    }

    /**
     * 表示metrics scan task相关的类型。
     */
    private final class MetricsScanTask implements Runnable {
        /**
         * 保存scanner信息。
         */
        private MetricScanner scanner = null;

        /**
         * 执行run操作。
         */
        public void run() {
            lastScanTime = System.currentTimeMillis();
            Thread.currentThread().setName("RunetimeScanTask");
            scanner = new MetricScanner();
            scanner.setAppUser(appUser);
            scanner.setFrameworkContext(context);
            scanner.setThreadCount(context.getMyperfConfig().getScannerThreadCount());
            scanner.setBuffer(buffer);
            scanner.setRecordCount(context.getMyperfConfig().getRecordRententionDays());

            try {
                scanner.scanAuto();
            } catch (Exception ex) {
                log.error("Exception during auto metric scan", ex);
            }
        }

        /**
         * 执行shutdown操作。
         */
        public void shutdown() {

            if (this.scanner != null)
                this.scanner.shutdown();
        }
    }

    /**
     * 表示alert scan task相关的类型。
     */
    private final class AlertScanTask implements Runnable {
        /**
         * 保存scanner信息。
         */
        private AlertScanner scanner = null;

        /**
         * 执行run操作。
         */
        public void run() {
            lastAlertScanTime = System.currentTimeMillis();
            Thread.currentThread().setName("AlertScanTask");
            scanner = new AlertScanner();
            scanner.setAppUser(appUser);
            scanner.setFrameworkContext(context);
            scanner.setThreadCount(context.getMyperfConfig().getScannerThreadCount());
            try {
                scanner.scanAuto();
            } catch (Exception ex) {
                log.error("Exception during alert scan", ex);
            }
        }

    }

    /**
     * 表示monitor task相关的类型。
     */
    private final class MonitorTask implements Runnable {
        /**
         * 执行run操作。
         */
        public void run() {
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastScanTime >= maxScanIdleTime) {//if the last run is 3 times of an interval

                log.error("Metrics Scanner hangs, shut it down.");

                if (metricsTask != null)
                    metricsTask.shutdown();

                forceStopScheduler(SCHEDULER_METRICS);
                try {
                    Thread.sleep(60000L);
                } catch (Exception ex) {
                }
                //restart schedulers
                startMetricScheduler();
            }

            if (currentTime - lastAlertScanTime >= maxScanIdleTime) {//if the last run is 3 times of an interval

                log.error("Alert Scanner hangs, shut it down.");
                forceStopScheduler(SCHEDULER_ALERT);
                try {
                    Thread.sleep(60000L);
                } catch (Exception ex) {

                }

                //restart schedulers
                startAlertScheduler();
            }
        }
    }
}
