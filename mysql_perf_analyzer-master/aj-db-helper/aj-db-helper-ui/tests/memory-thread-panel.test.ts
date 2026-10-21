// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import MemoryStatsPanel from '../src/components/MemoryStatsPanel.vue';
import { getMemoryThread } from '../src/api/performance-schema';
import { memoryFields, type MemoryRow } from '../src/utils/memory-global';
import { i18n } from '../src/i18n';

vi.mock('../src/api/performance-schema', () => ({ getMemoryThread: vi.fn(), getMemoryGlobal: vi.fn() }));
afterEach(() => vi.clearAllMocks());

it('更改筛选会清除旧结果，下一次刷新建立新基准，重设也回到原始值', async () => {
  const sample = (value: string, sampledAt: number, thread = '1') => ({ sampledAt, rows: [{ EVENT_NAME: 'memory/test', THREAD_ID: thread, ...Object.fromEntries(memoryFields.map(field => [field, value])) } as MemoryRow] });
  vi.mocked(getMemoryThread)
    .mockResolvedValueOnce(sample('100', 1000))
    .mockResolvedValueOnce(sample('130', 2000))
    .mockResolvedValueOnce(sample('9', 3000, '2'))
    .mockResolvedValueOnce(sample('10', 4000, '2'));
  const wrapper = mount(MemoryStatsPanel, { props: { scope: 'thread' }, global: { plugins: [i18n], stubs: {
    Input: { props: ['modelValue'], emits: ['update:modelValue'], template: `<input :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />` },
    Button: { template: '<button><slot /></button>' },
    Table: { name: 'MemoryTableStub', props: ['data'], template: '<div />' },
    Card: { template: '<div><slot /></div>' }, Alert: { template: '<div><slot /></div>' }, Page: true,
  } } });
  await flushPromises();
  const data = () => wrapper.findComponent({ name: 'MemoryTableStub' }).props('data');
  const button = (text: string) => wrapper.findAll('button').find(item => item.text() === text)!;
  expect(data()[0].COUNT_ALLOC).toBe('100');
  await button('刷新并比较').trigger('click');
  await flushPromises();
  expect(data()[0].COUNT_ALLOC_DELTA).toBe('30');
  await wrapper.findAll('input')[0]!.setValue('2');
  expect(data()).toEqual([]);
  await button('刷新并比较').trigger('click');
  await flushPromises();
  expect(getMemoryThread).toHaveBeenLastCalledWith('2', expect.any(AbortSignal));
  expect(data()[0].COUNT_ALLOC).toBe('9');
  expect(data()[0].COUNT_ALLOC_DELTA).toBeUndefined();
  await button('重设基准').trigger('click');
  await flushPromises();
  expect(data()[0].COUNT_ALLOC).toBe('10');
  expect(data()[0].COUNT_ALLOC_DELTA).toBeUndefined();
  wrapper.unmount();
});
