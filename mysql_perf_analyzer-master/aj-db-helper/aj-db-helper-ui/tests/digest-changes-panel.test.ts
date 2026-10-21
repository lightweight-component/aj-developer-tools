// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import DigestChangesPanel from '../src/components/DigestChangesPanel.vue';
import { getTopQueryChanges } from '../src/api/top-queries';
import { digestChangeFields, type DigestSnapshot } from '../src/utils/digest-changes';
import { i18n } from '../src/i18n';

vi.mock('../src/api/top-queries', () => ({ getTopQueryChanges: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const sample = (value: string, sampledAt: number): DigestSnapshot => ({ sampledAt, values: Object.fromEntries(digestChangeFields.map(field => [field, value])) as DigestSnapshot['values'] });
const globals = { plugins: [i18n], stubs: {
  Button: { template: '<button><slot /></button>' },
  Input: { props: ['modelValue'], emits: ['update:modelValue'], template: `<input :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />` },
  Card: { template: '<div><slot /></div>' }, Alert: { template: '<div><slot /></div>' },
  Table: { name: 'DigestTableStub', props: ['data'], template: '<div />' },
} };

it('自动建立基准、刷新作差、切换摘要重建、计数下降自动恢复', async () => {
  vi.mocked(getTopQueryChanges).mockResolvedValueOnce(sample('100', 1000)).mockResolvedValueOnce(sample('130', 2000))
    .mockResolvedValueOnce(sample('9', 3000)).mockResolvedValueOnce(sample('1', 4000));
  const wrapper = mount(DigestChangesPanel, { props: { target: { schemaName: 'robot', digest: 'a'.repeat(64), selection: 1 } }, global: globals });
  await flushPromises();
  const rows = () => wrapper.findComponent({ name: 'DigestTableStub' }).props('data');
  expect(rows()[0].VARIABLE_VALUE).toBe('100');
  const refresh = () => wrapper.findAll('button').find(button => button.text() === '刷新并比较')!;
  await refresh().trigger('click');
  await flushPromises();
  expect(rows()[0].VARIABLE_VALUE_DELTA).toBe('30');
  await wrapper.setProps({ target: { schemaName: 'other', digest: 'b'.repeat(64), selection: 2 } });
  await flushPromises();
  expect(getTopQueryChanges).toHaveBeenLastCalledWith('other', 'b'.repeat(64), expect.any(AbortSignal));
  expect(rows()[0].VARIABLE_VALUE).toBe('9');
  expect(rows()[0].VARIABLE_VALUE_DELTA).toBeUndefined();
  await refresh().trigger('click');
  await flushPromises();
  expect(rows()[0].VARIABLE_VALUE).toBe('1');
  expect(rows()[0].VARIABLE_VALUE_DELTA).toBeUndefined();
  expect(wrapper.text()).toContain('检测到累计计数下降');
  wrapper.unmount();
});

it('切换摘要时中止旧请求，即使旧结果晚到也不能覆盖新结果', async () => {
  let resolveOld: (value: DigestSnapshot) => void = () => {};
  vi.mocked(getTopQueryChanges).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }))
    .mockResolvedValueOnce(sample('2', 2000));
  const wrapper = mount(DigestChangesPanel, { props: { target: { schemaName: '', digest: 'a'.repeat(64), selection: 1 } }, global: globals });
  await flushPromises();
  const oldSignal = vi.mocked(getTopQueryChanges).mock.calls[0]![2]!;
  await wrapper.setProps({ target: { schemaName: '', digest: 'b'.repeat(64), selection: 2 } });
  await flushPromises();
  expect(oldSignal.aborted).toBe(true);
  resolveOld(sample('999', 1000));
  await flushPromises();
  expect(wrapper.findComponent({ name: 'DigestTableStub' }).props('data')[0].VARIABLE_VALUE).toBe('2');
  wrapper.unmount();
});
