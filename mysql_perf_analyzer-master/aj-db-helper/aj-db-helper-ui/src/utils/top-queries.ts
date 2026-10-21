import { t } from '../i18n';

export const topQueryFields = [
  "SCHEMA_NAME",
  "DIGEST",
  "DIGEST_TEXT",
  "COUNT_STAR",
  "SUM_TIMER_WAIT",
  "MIN_TIMER_WAIT",
  "AVG_TIMER_WAIT",
  "MAX_TIMER_WAIT",
  "SUM_LOCK_TIME",
  "SUM_ERRORS",
  "SUM_WARNINGS",
  "SUM_ROWS_AFFECTED",
  "SUM_ROWS_SENT",
  "SUM_ROWS_EXAMINED",
  "SUM_CREATED_TMP_DISK_TABLES",
  "SUM_CREATED_TMP_TABLES",
  "SUM_SELECT_FULL_JOIN",
  "SUM_SELECT_FULL_RANGE_JOIN",
  "SUM_SELECT_RANGE",
  "SUM_SELECT_RANGE_CHECK",
  "SUM_SELECT_SCAN",
  "SUM_SORT_MERGE_PASSES",
  "SUM_SORT_RANGE",
  "SUM_SORT_ROWS",
  "SUM_SORT_SCAN",
  "SUM_NO_INDEX_USED",
  "SUM_NO_GOOD_INDEX_USED",
  "SUM_CPU_TIME",
  "MAX_CONTROLLED_MEMORY",
  "MAX_TOTAL_MEMORY",
  "COUNT_SECONDARY",
  "FIRST_SEEN",
  "LAST_SEEN",
  "QUANTILE_95",
  "QUANTILE_99",
  "QUANTILE_999",
  "QUERY_SAMPLE_TEXT",
  "QUERY_SAMPLE_SEEN",
  "QUERY_SAMPLE_TIMER_WAIT"
] as const;
export const topQuerySortFields = [
  "sum_timer_wait",
  "avg_timer_wait",
  "sum_lock_time",
  "sum_rows_examined",
  "sum_rows_sent",
  "sum_created_tmp_disk_tables",
  "sum_created_tmp_tables",
  "sum_select_full_join",
  "sum_select_scan",
  "sum_sort_merge_passes",
  "sum_sort_rows",
  "sum_no_index_used",
  "sum_no_good_index_used"
] as const;
export type TopQuerySort = typeof topQuerySortFields[number];
export const topQueryMinutes = [1, 5, 30, 60, 240, 720, 1440, 52560000] as const;
export const topQueryCounts = [5, 10, 20, 50, 100, 200, 500, 1000] as const;
export interface TopQueryOptions { by: TopQuerySort; backToMins: number; count: number; }
export type TopQueryRow = Record<string, string | null>;
export const topQueryFieldTitles: Record<string, string> = {
  get SCHEMA_NAME() { return t('topQueries.fields.SCHEMA_NAME', '数据库'); },
  get DIGEST() { return t('topQueries.fields.DIGEST', '摘要标识'); },
  get DIGEST_TEXT() { return t('topQueries.fields.DIGEST_TEXT', '归一化 SQL'); },
  get COUNT_STAR() { return t('topQueries.fields.COUNT_STAR', '执行次数'); },
  get SUM_TIMER_WAIT() { return t('topQueries.fields.SUM_TIMER_WAIT', '总执行等待时间'); },
  get MIN_TIMER_WAIT() { return t('topQueries.fields.MIN_TIMER_WAIT', '最短执行等待时间'); },
  get AVG_TIMER_WAIT() { return t('topQueries.fields.AVG_TIMER_WAIT', '平均执行等待时间'); },
  get MAX_TIMER_WAIT() { return t('topQueries.fields.MAX_TIMER_WAIT', '最长执行等待时间'); },
  get SUM_LOCK_TIME() { return t('topQueries.fields.SUM_LOCK_TIME', '总锁等待时间'); },
  get SUM_ERRORS() { return t('topQueries.fields.SUM_ERRORS', '错误总数'); },
  get SUM_WARNINGS() { return t('topQueries.fields.SUM_WARNINGS', '警告总数'); },
  get SUM_ROWS_AFFECTED() { return t('topQueries.fields.SUM_ROWS_AFFECTED', '影响行数总计'); },
  get SUM_ROWS_SENT() { return t('topQueries.fields.SUM_ROWS_SENT', '返回行数总计'); },
  get SUM_ROWS_EXAMINED() { return t('topQueries.fields.SUM_ROWS_EXAMINED', '扫描行数总计'); },
  get SUM_CREATED_TMP_DISK_TABLES() { return t('topQueries.fields.SUM_CREATED_TMP_DISK_TABLES', '创建磁盘临时表总数'); },
  get SUM_CREATED_TMP_TABLES() { return t('topQueries.fields.SUM_CREATED_TMP_TABLES', '创建临时表总数'); },
  get SUM_SELECT_FULL_JOIN() { return t('topQueries.fields.SUM_SELECT_FULL_JOIN', '全表扫描式 JOIN 累计次数'); },
  get SUM_SELECT_FULL_RANGE_JOIN() { return t('topQueries.fields.SUM_SELECT_FULL_RANGE_JOIN', '全范围 JOIN 次数'); },
  get SUM_SELECT_RANGE() { return t('topQueries.fields.SUM_SELECT_RANGE', '范围查询次数'); },
  get SUM_SELECT_RANGE_CHECK() { return t('topQueries.fields.SUM_SELECT_RANGE_CHECK', '范围检查次数'); },
  get SUM_SELECT_SCAN() { return t('topQueries.fields.SUM_SELECT_SCAN', 'SELECT 扫描次数'); },
  get SUM_SORT_MERGE_PASSES() { return t('topQueries.fields.SUM_SORT_MERGE_PASSES', '排序归并累计次数'); },
  get SUM_SORT_RANGE() { return t('topQueries.fields.SUM_SORT_RANGE', '范围排序次数'); },
  get SUM_SORT_ROWS() { return t('topQueries.fields.SUM_SORT_ROWS', '排序行数总计'); },
  get SUM_SORT_SCAN() { return t('topQueries.fields.SUM_SORT_SCAN', '扫描排序次数'); },
  get SUM_NO_INDEX_USED() { return t('topQueries.fields.SUM_NO_INDEX_USED', '未使用索引累计次数'); },
  get SUM_NO_GOOD_INDEX_USED() { return t('topQueries.fields.SUM_NO_GOOD_INDEX_USED', '未使用合适索引累计次数'); },
  get SUM_CPU_TIME() { return t('topQueries.fields.SUM_CPU_TIME', '总 CPU 时间'); },
  get MAX_CONTROLLED_MEMORY() { return t('topQueries.fields.MAX_CONTROLLED_MEMORY', '受控内存峰值（字节）'); },
  get MAX_TOTAL_MEMORY() { return t('topQueries.fields.MAX_TOTAL_MEMORY', '总内存峰值（字节）'); },
  get COUNT_SECONDARY() { return t('topQueries.fields.COUNT_SECONDARY', '次级引擎执行次数'); },
  get FIRST_SEEN() { return t('topQueries.fields.FIRST_SEEN', '首次出现时间'); },
  get LAST_SEEN() { return t('topQueries.fields.LAST_SEEN', '最近出现时间'); },
  get QUANTILE_95() { return t('topQueries.fields.QUANTILE_95', '95% 分位耗时'); },
  get QUANTILE_99() { return t('topQueries.fields.QUANTILE_99', '99% 分位耗时'); },
  get QUANTILE_999() { return t('topQueries.fields.QUANTILE_999', '99.9% 分位耗时'); },
  get QUERY_SAMPLE_TEXT() { return t('topQueries.fields.QUERY_SAMPLE_TEXT', 'SQL 样本'); },
  get QUERY_SAMPLE_SEEN() { return t('topQueries.fields.QUERY_SAMPLE_SEEN', '样本采集时间'); },
  get QUERY_SAMPLE_TIMER_WAIT() { return t('topQueries.fields.QUERY_SAMPLE_TIMER_WAIT', '样本执行等待时间'); },
};

/** 下划线、驼峰和旧版全大写列名统一为 JDBC 列名。 */
export function canonicalTopQueryField(key: string): string {
  const normalized = key.replace(/_/g, '').toUpperCase();
  return topQueryFields.find(field => field.replace(/_/g, '') === normalized) ?? key;
}
export function topQueryRangeLabel(minutes: number): string {
  const labels: Record<number, string> = {
    1: t('topQueries.oneMinute', '1 分钟'), 5: t('topQueries.fiveMinutes', '5 分钟'), 30: t('topQueries.thirtyMinutes', '30 分钟'),
    60: t('topQueries.oneHour', '1 小时'), 240: t('topQueries.fourHours', '4 小时'), 720: t('topQueries.halfDay', '半天'),
    1440: t('topQueries.oneDay', '一天'), 52560000: t('topQueries.allTime', '全部时间'),
  };
  return labels[minutes] ?? String(minutes);
}
