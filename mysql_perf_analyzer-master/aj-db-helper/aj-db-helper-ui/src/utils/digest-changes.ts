import { compareGlobalStatus } from './global-status';

/** 只比较累加计数，排除 MIN、MAX、AVG、分位数等不能直接作差的指标。 */
export const digestChangeFields = [
  'COUNT_STAR', 'SUM_TIMER_WAIT', 'SUM_LOCK_TIME', 'SUM_ERRORS', 'SUM_WARNINGS', 'SUM_ROWS_AFFECTED',
  'SUM_ROWS_SENT', 'SUM_ROWS_EXAMINED', 'SUM_CREATED_TMP_DISK_TABLES', 'SUM_CREATED_TMP_TABLES',
  'SUM_SELECT_FULL_JOIN', 'SUM_SELECT_FULL_RANGE_JOIN', 'SUM_SELECT_RANGE', 'SUM_SELECT_RANGE_CHECK',
  'SUM_SELECT_SCAN', 'SUM_SORT_MERGE_PASSES', 'SUM_SORT_RANGE', 'SUM_SORT_ROWS', 'SUM_SORT_SCAN',
  'SUM_NO_INDEX_USED', 'SUM_NO_GOOD_INDEX_USED',
] as const;
export interface DigestTarget { schemaName: string; digest: string; selection: number; }
export interface DigestSnapshot {
  sampledAt: number;
  values: Record<typeof digestChangeFields[number], string | null>;
}
export const validDigest = (value: string) => /^(?:[0-9a-f]{32}|[0-9a-f]{64})$/i.test(value.trim());

export function digestCountersReset(current: DigestSnapshot, baseline: DigestSnapshot): boolean {
  return digestChangeFields.some(field => {
    const now = current.values[field];
    const old = baseline.values[field];
    return now !== null && old !== null && BigInt(now) < BigInt(old);
  });
}

/** 复用 BigInt 定点计算，与首次采样的固定基准比较，速率按总间隔计算。 */
export function compareDigestChanges(current: DigestSnapshot, baseline: DigestSnapshot) {
  const snapshot = (sample: DigestSnapshot) => ({
    sampledAt: sample.sampledAt, uptime: null,
    variables: digestChangeFields.map(field => ({ VARIABLE_NAME: field, VARIABLE_VALUE: sample.values[field] })),
  });
  return compareGlobalStatus(snapshot(current), snapshot(baseline))
    .filter(row => row.VARIABLE_VALUE_DELTA !== null && row.VARIABLE_VALUE_DELTA !== '0');
}
