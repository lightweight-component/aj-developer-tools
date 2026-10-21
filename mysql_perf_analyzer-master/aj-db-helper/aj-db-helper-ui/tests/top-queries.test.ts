import { afterEach, expect, it, vi } from 'vitest';
import { getTopQueries } from '../src/api/top-queries';
import { canonicalTopQueryField, topQueryFields, topQuerySortFields, topQueryMinutes, topQueryCounts } from '../src/utils/top-queries';
import cn from '../src/i18n/locales/zh-CN.json';
import tw from '../src/i18n/locales/zh-TW.json';
import en from '../src/i18n/locales/en-US.json';

afterEach(() => vi.unstubAllGlobals());

it('保留大整数、兼容列名变化、空摘要和未来字段', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"status":1,"data":[{"schemaName":"test","digest":null,"DIGESTTEXT":"select ?","sumTimerWait":9007199254740993,"quantile999":999,"newMetric":123}]}', { headers: { 'Content-Type': 'application/json' } })));
  const rows = await getTopQueries({ by: 'sum_timer_wait', backToMins: 1, count: 5 });
  expect(rows[0]?.SUM_TIMER_WAIT).toBe('9007199254740993');
  expect(rows[0]?.DIGEST).toBeNull();
  expect(rows[0]?.DIGEST_TEXT).toBe('select ?');
  expect(rows[0]?.QUANTILE_999).toBe('999');
  expect(rows[0]?.newMetric).toBe('123');
  expect(canonicalTopQueryField('SUM_NO_GOOD_INDEX_USED')).toBe('SUM_NO_GOOD_INDEX_USED');
});

it('13 项排序及截图全部时间、条数选项正确编码，不发送无效参数', async () => {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response('{"status":1,"data":[]}', { headers: { 'Content-Type': 'application/json' } })));
  vi.stubGlobal('fetch', fetcher);
  for (const by of topQuerySortFields)
    await getTopQueries({ by, backToMins: 52560000, count: 1000 });
  expect(topQuerySortFields).toHaveLength(13);
  expect(topQueryMinutes).toEqual([1, 5, 30, 60, 240, 720, 1440, 52560000]);
  expect(topQueryCounts).toEqual([5, 10, 20, 50, 100, 200, 500, 1000]);
  const url = new URL(fetcher.mock.calls[0]![0], 'http://localhost');
  expect(url.searchParams.get('backToMins')).toBe('52560000');
  expect(url.searchParams.get('count')).toBe('1000');
  await expect(getTopQueries({ by: 'sum_timer_wait', backToMins: -1, count: 5 })).rejects.toThrow();
  expect(fetcher).toHaveBeenCalledTimes(13);
});

it('所有 39 个字段均有三语标题，错误结果不会被当成空表', async () => {
  expect(topQueryFields).toHaveLength(39);
  for (const field of topQueryFields)
    for (const locale of [cn, tw, en])
      expect(locale.topQueries.fields[field]).toBeTruthy();
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"status":0,"message":"permission denied"}', { headers: { 'Content-Type': 'application/json' } })));
  await expect(getTopQueries({ by: 'sum_timer_wait', backToMins: 1, count: 5 })).rejects.toThrow('permission denied');
});
