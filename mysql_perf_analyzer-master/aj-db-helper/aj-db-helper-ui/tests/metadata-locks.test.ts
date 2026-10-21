import { afterEach, expect, it, vi } from 'vitest';
import { getMetadataLocks, metadataLockFields } from '../src/api/performance-schema';

afterEach(() => vi.unstubAllGlobals());

it('兼容实际 SQLMan 返回的大写无下划线列名', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 1, data: [{ PROCESSLISTID: 42, LOCKSTATUS: 'GRANTED', LOCKDURATION: 'TRANSACTION', objectName: 'robot', PROCESSLISTINFO: 'SELECT 1' }] }), { headers: { 'Content-Type': 'application/json' } })));
  const rows = await getMetadataLocks();
  expect(rows[0]?.PROCESSLIST_ID).toBe(42);
  expect(rows[0]?.LOCK_STATUS).toBe('GRANTED');
  expect(rows[0]?.LOCK_DURATION).toBe('TRANSACTION');
  expect(rows[0]?.PROCESSLIST_INFO).toBe('SELECT 1');
});

it('元数据锁读取现有接口，兼容 SQLMan 列名并保留所有字段和空值', async () => {
  const data = { processlistId: 42, processlistUser: 'app', processlistHost: 'localhost', processlistCommand: 'Query', processlistState: 'Waiting for table metadata lock', processlistTime: 12, objectType: 'TABLE', objectSchema: 'test', objectName: 'robot', lockType: 'EXCLUSIVE', lockStatus: 'PENDING', lockDuration: 'TRANSACTION', processlistInfo: null };
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 1, data: [data] }), { headers: { 'Content-Type': 'application/json' } }));
  vi.stubGlobal('fetch', fetcher);
  const rows = await getMetadataLocks();
  expect(fetcher.mock.calls[0]?.[0]).toContain('/pref_schema/metadata_lock');
  expect(Object.keys(rows[0]!)).toEqual([...metadataLockFields]);
  expect(rows[0]?.LOCK_STATUS).toBe('PENDING');
  expect(rows[0]?.PROCESSLIST_TIME).toBe(12);
  expect(rows[0]?.PROCESSLIST_INFO).toBeNull();
});

it('元数据锁业务错误保留后端消息，错误数据不作为空表显示', async () => {
  const fetcher = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ status: 0, message: '需要 MySQL 5.7' }), { headers: { 'Content-Type': 'application/json' } }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ status: 1, data: null }), { headers: { 'Content-Type': 'application/json' } }));
  vi.stubGlobal('fetch', fetcher);
  await expect(getMetadataLocks()).rejects.toThrow('需要 MySQL 5.7');
  await expect(getMetadataLocks()).rejects.toThrow('元数据锁数据格式错误');
});
