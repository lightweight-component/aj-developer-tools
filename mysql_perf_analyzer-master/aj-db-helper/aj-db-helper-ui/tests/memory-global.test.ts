import { afterEach, describe, expect, it, vi } from 'vitest';
import { compareMemory, memoryFields, normalizeThreadFilter, type MemoryRow } from '../src/utils/memory-global';
import { getMemoryGlobal, getMemoryThread } from '../src/api/performance-schema';

const row = (name: string, value: string): MemoryRow => ({ EVENT_NAME: name, ...Object.fromEntries(memoryFields.map(field => [field, value])) }) as MemoryRow;
afterEach(() => vi.unstubAllGlobals());

describe('全局内存比较', () => {
  it('按线程 ID 与事件名联合匹配，不能混用其他线程的基准', () => {
    const baseline = { sampledAt: 1000, rows: [{ ...row('same-event', '10'), THREAD_ID: '1' }, { ...row('same-event', '100'), THREAD_ID: '2' }] };
    const sample = { sampledAt: 2000, rows: [{ ...row('same-event', '101'), THREAD_ID: '2' }, { ...row('same-event', '12'), THREAD_ID: '1' }] };
    const values = compareMemory(sample, baseline);
    expect(values[0]?.COUNT_ALLOC_DELTA).toBe('1');
    expect(values[1]?.COUNT_ALLOC_DELTA).toBe('2');
    expect(values[0]?.THREAD_ID).toBe('2');
  });

  it('线程筛选规范化，并保留旧系统的通配符', () => {
    expect(normalizeThreadFilter(' % ')).toBe('');
    expect(normalizeThreadFilter(' 123 ')).toBe('123');
    expect(normalizeThreadFilter('12%_')).toBe('12%_');
    expect(() => normalizeThreadFilter('123 or 1=1')).toThrow();
  });

  it('线程请求携带编码后的筛选参数且保留大线程 ID 精度', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response('{"status":1,"data":[{"threadId":9007199254740993,"eventName":"memory/test","countAlloc":1}]}', { headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetcher);
    const sample = await getMemoryThread(' 123% ');
    expect(fetcher.mock.calls[0]?.[0]).toContain('/memory_thread?threadId=123%25');
    expect(sample.rows[0]?.THREAD_ID).toBe('9007199254740993');
    await expect(getMemoryThread('invalid')).rejects.toThrow();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('连续刷新使用同一基准，只显示变化行且按总时间计算速率', () => {
    const baseline = { sampledAt: 1000, rows: [row('changed', '100'), row('same', '10')] };
    const first = compareMemory({ sampledAt: 2000, rows: [row('changed', '130'), row('same', '10')] }, baseline);
    expect(first).toHaveLength(1);
    expect(first[0]?.COUNT_ALLOC_DELTA).toBe('30');
    expect(first[0]?.COUNT_ALLOC_PER_SEC).toBe('30');
    const second = compareMemory({ sampledAt: 6000, rows: [row('changed', '150'), row('same', '10')] }, baseline);
    expect(second[0]?.COUNT_ALLOC_OLD).toBe('100');
    expect(second[0]?.COUNT_ALLOC_DELTA).toBe('50');
    expect(second[0]?.COUNT_ALLOC_PER_SEC).toBe('10');
    expect(baseline.rows[0]?.COUNT_ALLOC).toBe('100');
  });

  it('保留大整数精度、负变化和新事件，零间隔不计算速率', () => {
    const before = row('memory', '9007199254740993');
    const now = row('memory', '9007199254740995');
    now.CURRENT_NUMBER_OF_BYTES_USED = '9007199254740990';
    const values = compareMemory({ sampledAt: 1000, rows: [now, row('new', '3')] }, { sampledAt: 1000, rows: [before] });
    expect(values[0]?.COUNT_ALLOC_DELTA).toBe('2');
    expect(values[0]?.CURRENT_NUMBER_OF_BYTES_USED_DELTA).toBe('-3');
    expect(values[0]?.COUNT_ALLOC_PER_SEC).toBeNull();
    expect(values[1]?.COUNT_ALLOC_OLD).toBe('0');
    expect(values[1]?.COUNT_ALLOC_DELTA).toBe('3');
  });

  it('请求现有接口、规范化列名并从 JSON 原文读取大整数', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response('{"status":1,"data":[{"eventName":"memory/test","countAlloc":9007199254740993}]}', { headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetcher);
    const sample = await getMemoryGlobal();
    expect(fetcher.mock.calls[0]?.[0]).toContain('/pref_schema/memory_global');
    expect(sample.rows[0]?.COUNT_ALLOC).toBe('9007199254740993');
    expect(sample.rows[0]?.EVENT_NAME).toBe('memory/test');
    expect(sample.rows[0]?.COUNT_FREE).toBeNull();
  });

  it('保留不支持版本的接口错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 0, message: '需要 MySQL 5.7 或更高版本' }), { headers: { 'Content-Type': 'application/json' } })));
    await expect(getMemoryGlobal()).rejects.toThrow('需要 MySQL 5.7');
  });
});
