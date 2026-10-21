import { t } from '../i18n';
import { memoryFields, normalizeMemoryKey, normalizeThreadFilter, type MemoryRow, type MemorySnapshot } from '../utils/memory-global';

export const metadataLockFields = [
  'PROCESSLIST_ID', 'PROCESSLIST_USER', 'PROCESSLIST_HOST', 'PROCESSLIST_COMMAND', 'PROCESSLIST_STATE', 'PROCESSLIST_TIME',
  'OBJECT_TYPE', 'OBJECT_SCHEMA', 'OBJECT_NAME', 'LOCK_TYPE', 'LOCK_STATUS', 'LOCK_DURATION', 'PROCESSLIST_INFO',
] as const;
export type MetadataLockRow = Record<typeof metadataLockFields[number], string | number | null>;

export async function getMetadataLocks(signal?: AbortSignal): Promise<MetadataLockRow[]> {
  const root = (import.meta.env.VITE_DB_API_ROOT || '/api_v1').replace(/\/+$/, '');
  const response = await fetch(`${root}/pref_schema/metadata_lock`, { headers: { Accept: 'application/json' }, signal });
  if (!response.headers.get('content-type')?.includes('application/json'))
    throw new Error(t('metadataLocks.invalidResponse', '元数据锁接口未返回 JSON，请检查服务地址。'));
  const result = await response.json() as { status: number; message?: string; data: unknown };
  if (!response.ok || result.status !== 1)
    throw new Error(result.message || t('metadataLocks.loadFailed', '读取元数据锁失败'));
  if (!Array.isArray(result.data))
    throw new Error(t('metadataLocks.invalidData', '元数据锁数据格式错误'));
  return result.data.map(raw => {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw))
      throw new Error(t('metadataLocks.invalidData', '元数据锁数据格式错误'));
    const values = Object.fromEntries(Object.entries(raw).map(([key, value]) => {
      const normalized = normalizeMemoryKey(key).replace(/_/g, '');
      const field = metadataLockFields.find(name => name.replace(/_/g, '') === normalized);
      return [field ?? key, value];
    }));
    const row = {} as MetadataLockRow;
    for (const field of metadataLockFields) {
      const value = values[field];
      if (value != null && typeof value !== 'string' && typeof value !== 'number')
        throw new Error(t('metadataLocks.invalidData', '元数据锁数据格式错误'));
      row[field] = value == null ? null : value;
    }
    return row;
  });
}

/** 读取现有接口；兼容 SQLMan 的 camelCase 列名和 JDBC 原始列名。 */
export async function getMemoryGlobal(signal?: AbortSignal): Promise<MemorySnapshot> {
  return getMemory('memory_global', '', signal);
}

export async function getMemoryThread(threadId: string, signal?: AbortSignal): Promise<MemorySnapshot> {
  let filter: string;
  try {
    filter = normalizeThreadFilter(threadId);
  } catch {
    throw new Error(t('memory.invalidThread', '线程 ID 只能包含数字及通配符 %、_。'));
  }
  return getMemory('memory_thread', filter ? '?' + new URLSearchParams({ threadId: filter }) : '', signal);
}

async function getMemory(endpoint: string, query: string, signal?: AbortSignal): Promise<MemorySnapshot> {
  const root = (import.meta.env.VITE_DB_API_ROOT || '/api_v1').replace(/\/+$/, '');
  const response = await fetch(`${root}/pref_schema/${endpoint}${query}`, { headers: { Accept: 'application/json' }, signal });
  if (!response.headers.get('content-type')?.includes('application/json'))
    throw new Error(t('memory.invalidResponse', '内存统计接口未返回有效 JSON，请检查服务地址。'));

  // 新版 JSON.parse 提供数字的原始文本，避免 64 位计数先被转换为不精确的 Number。
  const result = JSON.parse(await response.text(), (key: string, value: unknown, context?: { source?: string }) => {
    if (typeof value === 'number' && (normalizeMemoryKey(key) === 'THREAD_ID' || memoryFields.some(field => field === normalizeMemoryKey(key)))) {
      if (context?.source)
        return context.source;
      if (!Number.isSafeInteger(value))
        throw new Error(t('memory.precisionError', '浏览器无法精确读取此大整数，请升级浏览器或让接口返回字符串。'));
      return String(value);
    }
    return value;
  }) as { status: number; message?: string; data: Record<string, unknown>[] };
  if (!response.ok || result.status !== 1)
    throw new Error(result.message || t('memory.loadFailed', '读取内存统计失败'));
  if (!Array.isArray(result.data))
    throw new Error(t('memory.invalidData', '内存统计数据格式错误'));

  const rows = result.data.map(raw => {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw))
      throw new Error(t('memory.invalidData', '内存统计数据格式错误'));
    const values = Object.fromEntries(Object.entries(raw).map(([key, value]) => [normalizeMemoryKey(key), value]));
    if (typeof values.EVENT_NAME !== 'string')
      throw new Error(t('memory.invalidData', '内存统计数据格式错误'));
    const row = { EVENT_NAME: values.EVENT_NAME } as MemoryRow;
    if (endpoint === 'memory_thread') {
      if (values.THREAD_ID == null || !/^\d+$/.test(String(values.THREAD_ID)))
        throw new Error(t('memory.invalidData', '内存统计数据格式错误'));
      row.THREAD_ID = String(values.THREAD_ID);
    }
    for (const field of memoryFields) {
      const value = values[field];
      if (value != null && !/^[+-]?\d+$/.test(String(value)))
        throw new Error(t('memory.invalidData', '内存统计数据格式错误'));
      row[field] = value == null ? null : String(value);
    }
    return row;
  });
  return { sampledAt: Date.now(), rows };
}
