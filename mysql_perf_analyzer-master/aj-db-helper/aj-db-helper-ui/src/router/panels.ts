import { t } from "../i18n";

/** 性能概览子面板。 */
export const overviewPanels = [
  { path: '/top/health', get title() { return t('overview.health', '健康状态'); }, get description() { return t('overview.healthDescription', '在同一屏幕中显示所有受管理服务器的最新性能指标。'); }, icon: 'ios-heart-outline' },
  { path: '/top/realtime', get title() { return t('overview.realtimeTop', '实时 Top'); }, get description() { return t('overview.realtimeTopDescription', '查看数据库实时性能指标与资源使用排行。'); }, icon: 'ios-speedometer-outline' },
  { path: '/top/alerts', get title() { return t('overview.alerts24h', '24小时告警'); }, get description() { return t('overview.alerts24hDescription', '查看最近 24 小时的数据库告警。'); }, icon: 'ios-notifications-outline' },
] as const;
/** 实时监控的子面板；菜单与路由共用同一份定义。 */
export const realtimePanels = [
  { path: "/realtime/processes", get title() { return t('ui.processes', "进程列表"); }, get description() { return t('ui.viewActiveMySQLConnectionsSessionsAndRunningSQL', "查看 MySQL 活动连接、会话与正在执行的 SQL。"); }, icon: "ios-list-box-outline" },
  { path: "/realtime/global-status", get title() { return t('ui.globalStatus', "全局状态"); }, get description() { return t('ui.viewMySQLGlobalStatusMetricsAndRefreshToCompareChanges', "查看 MySQL 全局状态指标，刷新后比较指标增量。"); }, icon: "ios-pulse-outline" },
  { path: "/realtime/global-variables", get title() { return t('ui.globalVariables', "全局变量"); }, get description() { return t('ui.viewMySQLGlobalConfigurationVariablesAndTheirCurrentValues', "查看 MySQL 全局配置变量及当前取值。"); }, icon: "ios-options-outline" },
  { path: "/realtime/variable-diffs", get title() { return t('ui.variableDifferences', "变量差异"); }, get description() { return t('ui.compareGlobalVariablesAcrossDatabaseInstancesToIdentifyConfigurationDifferences', "对比不同数据库实例的全局变量，定位配置差异。"); }, icon: "ios-git-compare" },
  { path: "/realtime/replication", get title() { return t('ui.replicationStatus', "复制状态"); }, get description() { return t('ui.viewMySQLReplicationStatusLagAndErrors', "查看 MySQL 主从复制状态、复制延迟与异常信息。"); }, icon: "ios-git-network" },
  { path: "/realtime/innodb-engine-status", get title() { return t('ui.innodbEngineStatus', "InnoDB 引擎状态"); }, get description() { return t('ui.viewInnoDBTransactionsLocksDeadlocksAndIOStatus', "查看 InnoDB 引擎的事务、锁、死锁与 I/O 状态。"); }, icon: "ios-document-outline" },
  { path: "/realtime/innodb-statistics", get title() { return t('ui.innodbStatistics', "InnoDB 统计"); }, get description() { return t('ui.viewInnoDBBufferPoolReadWriteAndTransactionStatistics', "查看 InnoDB 缓冲池、读写与事务相关的统计指标。"); }, icon: "ios-stats-outline" },
  { path: "/realtime/user-stats", get title() { return t('ui.userStatistics', "用户统计"); }, get description() { return t('ui.viewUserStatisticsSupportedByPerconaMariaDBAndOtherServers', "查看 Percona、MariaDB 等支持的用户级统计信息。"); }, icon: "ios-people-outline" },
  { path: "/realtime/explain-plan", get title() { return t('ui.executionPlan', "执行计划"); }, get description() { return t('ui.viewTheEXPLAINPlanToAnalyzeIndexUsageAndTableAccess', "查看 SQL 的 EXPLAIN 执行计划，分析索引使用与表访问方式。"); }, icon: "ios-search-outline" },
] as const;

/** 性能剖析的两个独立子面板。 */
export const profilingPanels = [
  { path: "/profiling/explain-plan", get title() { return t('ui.simpleExecutionPlan', "简单执行计划"); }, get description() { return t('ui.viewSQLExecutionPlansUsingMySQLEXPLAIN', "通过 MySQL EXPLAIN 查看 SQL 的执行计划。"); }, icon: "ios-search-outline" },
  { path: "/profiling/analysis", get title() { return t('ui.profiling', "性能剖析"); }, get description() { return t('ui.executeOnceToCollectSessionStatisticsAndExecutionStageTimings', "一次查询生成会话状态统计和各执行阶段的耗时分析。"); }, icon: "ios-timer-outline" },
] as const;

/** 性能模式子面板；与独立路由、工作区标签共用。 */
export const performanceSchemaPanels = [
  { path: "/performance-schema/setup", get title() { return t('performanceSchema.setup', "设置"); }, get description() { return t('performanceSchema.setupDescription', "查看 Performance Schema 的采集设置。"); }, icon: "ios-settings-outline" },
  { path: "/performance-schema/threads", get title() { return t('performanceSchema.threads', "线程"); }, get description() { return t('performanceSchema.threadsDescription', "查看 Performance Schema 线程列表。"); }, icon: "ios-list-box-outline" },
  { path: "/performance-schema/events-waits", get title() { return t('performanceSchema.eventsWaits', "等待事件"); }, get description() { return t('performanceSchema.eventsWaitsDescription', "查看当前等待事件。"); }, icon: "ios-hourglass-outline" },
  { path: "/performance-schema/events-waits-summary", get title() { return t('performanceSchema.eventsWaitsSummary', "等待事件汇总"); }, get description() { return t('performanceSchema.eventsWaitsSummaryDescription', "查看等待事件的汇总统计。"); }, icon: "ios-stats-outline" },
  { path: "/performance-schema/table-file-io", get title() { return t('performanceSchema.tableFileIO', "表与文件 I/O"); }, get description() { return t('performanceSchema.tableFileIODescription', "查看表和文件的 I/O 统计。"); }, icon: "ios-swap" },
  { path: "/performance-schema/mutex", get title() { return t('performanceSchema.mutex', "互斥锁"); }, get description() { return t('performanceSchema.mutexDescription', "查看当前被持有的互斥锁。"); }, icon: "ios-lock-outline" },
  { path: "/performance-schema/rw-locks", get title() { return t('performanceSchema.rwLocks', "读写锁"); }, get description() { return t('performanceSchema.rwLocksDescription', "查看读写锁的持有与等待情况。"); }, icon: "ios-key-outline" },
  { path: "/performance-schema/metadata-locks", get title() { return t('performanceSchema.metadataLocks', "元数据锁"); }, get description() { return t('performanceSchema.metadataLocksDescription', "元数据锁（MDL）用于保护表等数据库对象的结构，避免查询或事务执行期间被并发修改。未结束的事务可能持有锁，使 ALTER TABLE 等操作等待；不兼容的待授予锁也可能影响后续访问。"); }, icon: "ios-lock-outline" },
  { path: "/performance-schema/memory", get title() { return t('performanceSchema.memory', "内存"); }, get description() { return t('performanceSchema.memoryDescription', "查看 MySQL 内存分配统计。"); }, icon: "ios-pie-outline" },
  { path: "/performance-schema/top-queries", get title() { return t('performanceSchema.topQueries', "热点查询"); }, get description() { return t('performanceSchema.topQueriesDescription', "查看主要 SQL 查询的性能统计。"); }, icon: "ios-trending-up" },
] as const;

/** 左侧菜单与页面说明共用的面板定义。 */
export const panels = [
  { path: "/top", get title() { return t('ui.overview', "性能概览"); }, get description() { return t('ui.viewRealTimeKeyMetricsForTheDatabaseAndOperatingSystem', "查看数据库与操作系统的实时关键指标。"); }, icon: "ios-speedometer-outline", children: overviewPanels },
  { path: "/realtime", get title() { return t('ui.realtimeMonitoring', "实时监控"); }, get description() { return t('ui.trackDatabaseStatusActiveSessionsAndSQLExecution', "跟踪数据库运行状态、活动会话与 SQL 执行情况。"); }, icon: "ios-pulse-outline", children: realtimePanels },
  { path: "/charts", get title() { return t('ui.performanceCharts', "性能图表"); }, get description() { return t('ui.analyzeDatabasePerformanceTrendsUsingHistoricalCharts', "通过历史指标图表分析数据库性能变化。"); }, icon: "ios-stats-outline" },
  { path: "/meta", get title() { return t('ui.metadata', "元数据"); }, get description() { return t('ui.viewDatabasesTablesColumnsIndexesAndOtherSchemaInformation', "查看数据库、数据表、字段与索引等结构信息。"); }, icon: "ios-list-box-outline" },
  { path: "/profiling", get title() { return t('ui.profiling', "性能剖析"); }, get description() { return t('ui.viewSQLExecutionPlansAndAnalyzeQueryTimings', "查看 SQL 执行计划并分析查询耗时。"); }, icon: "ios-timer-outline", children: profilingPanels },
  { path: "/performance-schema", get title() { return t('ui.performanceSchema', "性能模式"); }, get description() { return t('ui.viewStatementsWaitEventsAndLocksInMySQLPerformanceSchema', "查看 MySQL Performance Schema 中的语句、等待事件与锁信息。"); }, icon: "ios-analytics-outline", children: performanceSchemaPanels },
] as const;

export const allPanels = [...panels, ...overviewPanels, ...realtimePanels, ...profilingPanels, ...performanceSchemaPanels] as const;
