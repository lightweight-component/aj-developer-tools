import { t } from '../i18n';
import { canonicalTopQueryField, topQueryCounts, topQueryMinutes, topQuerySortFields, type TopQueryOptions, type TopQueryRow } from '../utils/top-queries';
import { digestChangeFields, validDigest, type DigestSnapshot } from '../utils/digest-changes';

export async function getTopQueries(options: TopQueryOptions, signal?: AbortSignal): Promise<TopQueryRow[]> {
  if (!topQuerySortFields.includes(options.by) || !topQueryMinutes.some(value => value === options.backToMins) || !topQueryCounts.some(value => value === options.count))
    throw new Error(t('topQueries.invalidOptions', '热点查询筛选参数无效'));
  const query = new URLSearchParams({ by: options.by, backToMins: String(options.backToMins), count: String(options.count) });
  const data = await requestDigestData('top_queries', query, signal);
  if (!Array.isArray(data))
    throw new Error(t('topQueries.invalidData', '热点查询数据格式错误'));
  return data.map(normalizeDigestRow);
}

export async function getTopQueryChanges(schemaName: string, digest: string, signal?: AbortSignal): Promise<DigestSnapshot | null> {
  if (!validDigest(digest))
    throw new Error(t('digestChanges.invalidDigest', '请输入 32 或 64 位十六进制 Digest。'));
  const query = new URLSearchParams({ schemaName, digest: digest.trim().toLowerCase() });
  const data = await requestDigestData('top_queries/changes', query, signal);
  if (data === null)
    return null;
  const row = normalizeDigestRow(data);
  if (row.COUNT_STAR == null)
    throw new Error(t('topQueries.invalidData', '热点查询数据格式错误'));
  const values = {} as DigestSnapshot['values'];
  for (const field of digestChangeFields) {
    const value = row[field];
    if (value != null && !/^\d+$/.test(value))
      throw new Error(t('topQueries.invalidData', '热点查询数据格式错误'));
    values[field] = value == null ? null : BigInt(value).toString();
  }
  return { sampledAt: Date.now(), values };
}

async function requestDigestData(endpoint: string, query: URLSearchParams, signal?: AbortSignal): Promise<unknown> {
  const root = (import.meta.env.VITE_DB_API_ROOT || '/api_v1').replace(/\/+$/, '');
  const response = await fetch(`${root}/pref_schema/${endpoint}?${query}`, { headers: { Accept: 'application/json' }, signal });
  if (!response.headers.get('content-type')?.includes('application/json'))
    throw new Error(t('topQueries.invalidResponse', '热点查询接口未返回 JSON，请检查服务地址。'));

  // 在 Number 舍入前读取 JSON 原始数值；计数、时间和内存均以字符串展示。
  const result = JSON.parse(await response.text(), (key: string, value: unknown, context?: { source?: string }) => {
    if (typeof value !== 'number' || ['status', 'errorCode'].includes(key))
      return value;
    if (context?.source)
      return context.source;
    if (!Number.isSafeInteger(value))
      throw new Error(t('topQueries.precision', '浏览器无法精确读取此大整数，请升级浏览器或让接口返回字符串。'));
    return String(value);
  }) as { status: number; message?: string; data: unknown };
  if (!response.ok || result?.status !== 1)
    throw new Error(result?.message || t('topQueries.loadFailed', '读取热点查询失败'));
  return result.data;
}

function normalizeDigestRow(raw: unknown): TopQueryRow {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw))
    throw new Error(t('topQueries.invalidData', '热点查询数据格式错误'));
  const row: TopQueryRow = {};
  for (const [key, value] of Object.entries(raw)) {
    if (value != null && typeof value !== 'string')
      throw new Error(t('topQueries.invalidData', '热点查询数据格式错误'));
    row[canonicalTopQueryField(key)] = value == null ? null : value;
  }
  return row;
}
