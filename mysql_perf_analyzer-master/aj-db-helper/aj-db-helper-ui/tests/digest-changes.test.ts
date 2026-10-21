import { afterEach, expect, it, vi } from 'vitest';
import { compareDigestChanges, digestCountersReset, digestChangeFields, validDigest, type DigestSnapshot } from '../src/utils/digest-changes';
import { getTopQueryChanges } from '../src/api/top-queries';

afterEach(() => vi.unstubAllGlobals());
const sample = (value: string, sampledAt: number): DigestSnapshot => ({ sampledAt, values: Object.fromEntries(digestChangeFields.map(field => [field, value])) as DigestSnapshot['values'] });

it('21 个累计指标按固定基准作差并计算总时间速率，保留大整数精度', () => {
  const baseline = sample('9007199254740993', 1000);
  const first = compareDigestChanges(sample('9007199254741003', 2000), baseline);
  expect(first[0]?.VARIABLE_VALUE_DELTA).toBe('10');
  expect(first[0]?.VARIABLE_VALUE_PER_SEC).toBe('10');
  const second = compareDigestChanges(sample('9007199254741013', 5000), baseline);
  expect(second[0]?.VARIABLE_VALUE_DELTA).toBe('20');
  expect(second[0]?.VARIABLE_VALUE_PER_SEC).toBe('5');
  expect(digestChangeFields).toHaveLength(21);
  expect(compareDigestChanges(baseline, baseline)).toEqual([]);
  expect(digestCountersReset(sample('0', 6000), baseline)).toBe(true);
});

it('兼容 32/64 位 Digest 和无默认数据库，精确读取计数，区分记录消失', async () => {
  expect(validDigest('a'.repeat(32))).toBe(true);
  expect(validDigest('B'.repeat(64))).toBe(true);
  expect(validDigest('a'.repeat(33))).toBe(false);
  const fetcher = vi.fn()
    .mockResolvedValueOnce(new Response('{"status":1,"data":{"countStar":9007199254740993,"SUMTIMERWAIT":4}}', { headers: { 'Content-Type': 'application/json' } }))
    .mockResolvedValueOnce(new Response('{"status":1,"data":null}', { headers: { 'Content-Type': 'application/json' } }));
  vi.stubGlobal('fetch', fetcher);
  const result = await getTopQueryChanges('', 'B'.repeat(64));
  const url = new URL(fetcher.mock.calls[0]![0], 'http://localhost');
  expect(url.pathname).toContain('/top_queries/changes');
  expect(url.searchParams.has('schemaName')).toBe(true);
  expect(url.searchParams.get('schemaName')).toBe('');
  expect(url.searchParams.get('digest')).toBe('b'.repeat(64));
  expect(result?.values.COUNT_STAR).toBe('9007199254740993');
  expect(result?.values.SUM_TIMER_WAIT).toBe('4');
  expect(await getTopQueryChanges('robot', 'a'.repeat(32))).toBeNull();
  await expect(getTopQueryChanges('robot', 'invalid')).rejects.toThrow();
  expect(fetcher).toHaveBeenCalledTimes(2);
});

it('拒绝把旧数组响应、缺少数据或无效记录当作单条摘要', async () => {
  for (const data of [[], [{ countStar: 1 }], {}, { countStar: 'invalid' }, undefined]) {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 1, data }), { headers: { 'Content-Type': 'application/json' } })));
    await expect(getTopQueryChanges('robot', 'a'.repeat(32))).rejects.toThrow();
  }
});
