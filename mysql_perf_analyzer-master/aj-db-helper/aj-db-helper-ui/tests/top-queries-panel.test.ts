// @vitest-environment jsdom
import { expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import TopQueriesView from '../src/views/TopQueriesView.vue';
import { getTopQueries } from '../src/api/top-queries';
import { i18n } from '../src/i18n';

vi.mock('../src/api/top-queries', () => ({ getTopQueries: vi.fn() }));

it('默认请求与下拉选择后提交正确参数，关闭页面取消请求', async () => {
  vi.mocked(getTopQueries).mockResolvedValue([]);
  const wrapper = mount(TopQueriesView, { global: { plugins: [i18n], stubs: {
    Button: { template: '<button><slot /></button>' }, Card: { template: '<div><slot /></div>' },
    Table: true, Input: true, Alert: true, Page: true, SqlCodeCell: true,
  } } });
  await flushPromises();
  expect(getTopQueries).toHaveBeenLastCalledWith({ by: 'sum_timer_wait', backToMins: 1, count: 5 }, expect.any(AbortSignal));
  const selects = wrapper.findAll('select');
  await selects[0]!.setValue('avg_timer_wait');
  await selects[1]!.setValue('52560000');
  await selects[2]!.setValue('20');
  await wrapper.find('button').trigger('click');
  await flushPromises();
  expect(getTopQueries).toHaveBeenLastCalledWith({ by: 'avg_timer_wait', backToMins: 52560000, count: 20 }, expect.any(AbortSignal));
  const signal = vi.mocked(getTopQueries).mock.calls.at(-1)![1]!;
  wrapper.unmount();
  expect(signal.aborted).toBe(true);
});
