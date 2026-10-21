import { t } from "../i18n";
/**
 * 常用会话状态说明。未收录的变量保持原名展示，便于兼容不同 MySQL 版本。
 * MySQL 指标参考：https://dev.mysql.com/doc/refman/8.0/en/server-status-variables.html
 * 以下 _ 开头的三个指标由本系统计算。
 */
export const sessionStatDescriptions: Readonly<Record<string, string>> = {
  get BYTES_RECEIVED() { return t('ui.bytesReceivedFromTheClient', "从客户端接收的字节数"); },
  get BYTES_SENT() { return t('ui.bytesSentToTheClient', "发送给客户端的字节数"); },
  get COM_SELECT() { return t('ui.numberOfSELECTStatementsExecuted', "执行 SELECT 语句的次数"); },
  get COM_SET_OPTION() { return t('ui.numberOfSETStatementsExecutedMayIncludeDriverSettings', "执行 SET 语句的次数（可能包含驱动设置）"); },
  get COM_SHOW_STATUS() { return t('ui.numberOfSHOWSTATUSStatementsExecutedIncludingStatusSampling', "执行 SHOW STATUS 的次数（包含状态采样）"); },
  get COM_SHOW_VARIABLES() { return t('ui.numberOfSHOWVARIABLESStatementsExecuted', "执行 SHOW VARIABLES 的次数"); },
  get COM_STMT_PREPARE() { return t('ui.numberOfPreparedStatementsPrepared', "预处理语句的准备次数"); },
  get COM_STMT_EXECUTE() { return t('ui.numberOfPreparedStatementExecutions', "预处理语句的执行次数"); },
  get COM_STMT_CLOSE() { return t('ui.numberOfPreparedStatementsClosed', "预处理语句的关闭次数"); },
  get HANDLER_COMMIT() { return t('ui.numberOfInternalStorageEngineCommits', "存储引擎内部提交的次数"); },
  get HANDLER_ROLLBACK() { return t('ui.numberOfInternalStorageEngineRollbacks', "存储引擎内部回滚的次数"); },
  get HANDLER_EXTERNAL_LOCK() { return t('ui.storageEngineExternalLockCallsUsuallyAtTheStartAndEnd', "存储引擎外部锁调用次数，通常发生在表访问开始和结束时"); },
  get HANDLER_READ_FIRST() { return t('ui.requestsToReadTheFirstIndexEntry', "读取索引首条记录的请求次数"); },
  get HANDLER_READ_KEY() { return t('ui.requestsToReadARowUsingAnIndexKey', "按索引键读取记录的请求次数"); },
  get HANDLER_READ_NEXT() { return t('ui.requestsToReadTheNextRowInIndexOrder', "按索引顺序读取下一条记录的请求次数"); },
  get HANDLER_READ_PREV() { return t('ui.requestsToReadThePreviousRowInIndexOrder', "按索引顺序读取上一条记录的请求次数"); },
  get HANDLER_READ_LAST() { return t('ui.requestsToReadTheLastIndexEntry', "读取索引末条记录的请求次数"); },
  get HANDLER_READ_RND() { return t('ui.requestsToReadARowAtAFixedPosition', "按固定位置读取记录的请求次数"); },
  get HANDLER_READ_RND_NEXT() { return t('ui.requestsToReadTheNextRowInDataFileOrderOften', "按数据文件顺序读取下一条记录的请求次数，常见于表扫描"); },
  get HANDLER_WRITE() { return t('ui.requestsToInsertARowMayInvolveInternalTemporaryTables', "向表中插入记录的请求次数（可能涉及内部临时表）"); },
  get HANDLER_UPDATE() { return t('ui.requestsToUpdateARow', "更新表中记录的请求次数"); },
  get HANDLER_DELETE() { return t('ui.requestsToDeleteARow', "删除表中记录的请求次数"); },
  get LAST_QUERY_COST() { return t('ui.optimizerCostEstimateForTheLastCompiledQueryNotActualElapsed', "优化器估算的末次已编译查询成本，并非实际耗时"); },
  get LAST_QUERY_PARTIAL_PLANS() { return t('ui.partialExecutionPlansEvaluatedByTheOptimizerForTheLastQuery', "优化器为末次查询评估的部分执行计划数量"); },
  get QUERIES() { return t('ui.statementsExecutedIncludingStatementsInsideStoredPrograms', "执行的语句数，包含存储程序内部语句"); },
  get QUESTIONS() { return t('ui.clientStatementsExecutedExcludingStatementsInsideStoredPrograms', "客户端提交并执行的语句数，不包含存储程序内部语句"); },
  get SELECT_RANGE() { return t('ui.joinsUsingIndexRangeAccessOnTheFirstTable', "首个表使用索引范围访问的连接次数"); },
  get SELECT_SCAN() { return t('ui.joinsUsingAFullTableScanOnTheFirstTable', "首个表执行全表扫描的连接次数"); },
  get SELECT_FULL_JOIN() { return t('ui.joinsUsingTableScansWithoutIndexes', "因未使用索引而执行表扫描的连接次数"); },
  get SELECT_FULL_RANGE_JOIN() { return t('ui.joinsUsingIndexRangeSearchesOnTheJoinedTable', "对被连接表使用索引范围查找的连接次数"); },
  get SELECT_RANGE_CHECK() { return t('ui.joinsCheckingIndexUsageForEachRow', "对每行重新检查索引使用情况的连接次数"); },
  get SORT_ROWS() { return t('ui.numberOfRowsSorted', "参与排序的行数"); },
  get SORT_SCAN() { return t('ui.sortsPerformedUsingTableScans', "通过扫描表完成排序的次数"); },
  get SORT_RANGE() { return t('ui.sortsPerformedUsingIndexRangeAccess', "通过索引范围访问完成排序的次数"); },
  get SORT_MERGE_PASSES() { return t('ui.mergePassesPerformedDuringSorting', "排序过程中执行归并的次数"); },
  get CREATED_TMP_TABLES() { return t('ui.internalTemporaryTablesCreatedWhileExecutingStatements', "执行语句时创建的内部临时表数量"); },
  get CREATED_TMP_DISK_TABLES() { return t('ui.internalOnDiskTemporaryTablesCreatedWhileExecutingStatements', "执行语句时创建的磁盘内部临时表数量"); },
  get CREATED_TMP_FILES() { return t('ui.numberOfTemporaryFilesCreated', "创建的临时文件数量"); },
  get TABLE_LOCKS_IMMEDIATE() { return t('ui.tableLocksAcquiredImmediately', "立即获得表锁的次数"); },
  get TABLE_LOCKS_WAITED() { return t('ui.tableLocksAcquiredAfterWaiting', "需要等待才能获得表锁的次数"); },
  get TABLE_OPEN_CACHE_HITS() { return t('ui.tableCacheHitsWhenOpeningTables', "打开表时命中表缓存的次数"); },
  get TABLE_OPEN_CACHE_MISSES() { return t('ui.tableCacheMissesWhenOpeningTables', "打开表时未命中表缓存的次数"); },
  get TABLE_OPEN_CACHE_OVERFLOWS() { return t('ui.tableCacheInstanceOverflows', "表缓存实例发生溢出的次数"); },
  get OPENED_TABLES() { return t('ui.numberOfTablesOpened', "打开表的次数"); },
  get _RETURNED_ROWS() { return t('ui.rowsReturnedAndReadByThisQuery', "本次查询实际返回并读取的行数"); },
  get _TIME_EXEC_MS() { return t('ui.timeFromQueryExecutionToObtainingTheResultSetMilliseconds', "执行查询至获得结果集的耗时（毫秒）"); },
  get _TIME_FETCH_MS() { return t('ui.timeToReadAllRowsAndCloseTheResultSetMilliseconds', "读取全部结果并关闭结果集的耗时（毫秒）"); },
};

export function getSessionStatDescription(name: string): string | undefined {
  const key = name.toUpperCase();
  return Object.hasOwn(sessionStatDescriptions, key) ? sessionStatDescriptions[key] : undefined;
}
