<template>
  <section>
    <p class="description">{{ $t('digestChanges.rules', '追踪指定 Schema 和 Digest 的累计指标。首次查询或重设基准显示原始值；刷新只显示变化项，速率按距基准建立时的总时间计算。修改摘要后需重新查询，时间指标单位为皮秒。') }}</p>
    <div class="toolbar">
      <label>Schema <Input v-model="schemaName" :disabled="loading" :placeholder="$t('digestChanges.schemaHint', '留空表示未指定默认数据库')" class="schema-input" @on-enter="load(true)" /></label>
      <label>Digest <Input v-model="digest" :disabled="loading" :placeholder="$t('digestChanges.digestHint', '32 或 64 位摘要标识')" class="digest-input" @on-enter="load(true)" /></label>
      <Button type="primary" :loading="loading" @click="load(false)">{{ $t('ui.refreshAndCompare', '刷新并比较') }}</Button>
      <Button :disabled="loading" @click="load(true)">{{ $t('ui.resetBaseline', '重设基准') }}</Button>
    </div>
    <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    <Alert v-if="missing" type="warning" show-icon>{{ $t('digestChanges.missing', '未找到该摘要记录，可能尚未采集、已被清理或已被淘汰。下次找到记录时将重新建立基准。') }}</Alert>
    <Alert v-else-if="resetDetected" type="warning" show-icon>{{ $t('digestChanges.counterReset', '检测到累计计数下降，可能发生重启或统计重置，已自动重新建立基准。') }}</Alert>
    <Alert v-else-if="current" show-icon>{{ compared ? $t('memory.comparing', '正在与固定基准比较，连续刷新不会移动基准。') : $t('memory.baselineReady', '当前显示原始采样值，已作为比较基准。点击刷新并比较查看变化。') }}</Alert>
    <Card :bordered="false">
      <div class="summary">
        <span>{{ $t('ui.items', '共 {count} 项', { named: { count: rows.length } }) }}</span>
        <span v-if="baseline && current">{{ $t('ui.baselineCurrentIntervalSeconds', '基准：{baseline}　当前：{current}　间隔：{interval} 秒', { named: { baseline: formatTime(baseline.sampledAt), current: formatTime(current.sampledAt), interval: ((current.sampledAt - baseline.sampledAt) / 1000).toFixed(3) } }) }}</span>
      </div>
      <Table :columns="columns" :data="rows" :loading="loading" border stripe size="small" :max-height="620" :no-data-text="current && compared ? $t('memory.noChanges', '没有匹配的变化项') : $t('digestChanges.empty', '选择摘要后建立基准，或输入 Schema 和 Digest 进行查询。')">
        <template #metric="{ row }"><code>{{ row.VARIABLE_NAME }}</code><span class="metric-description">{{ topQueryFieldTitles[row.VARIABLE_NAME] || '' }}</span></template>
        <template #value="{ row, column }">{{ row[column.key] ?? '—' }}</template>
      </Table>
    </Card>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { t, formatTime } from '../i18n';
import { getTopQueryChanges } from '../api/top-queries';
import { digestChangeFields, compareDigestChanges, digestCountersReset, validDigest, type DigestSnapshot, type DigestTarget } from '../utils/digest-changes';
import { topQueryFieldTitles } from '../utils/top-queries';

const props = defineProps<{ target?: DigestTarget }>();
const schemaName = ref('');
const digest = ref('');
const baseline = ref<DigestSnapshot>();
const current = ref<DigestSnapshot>();
const loading = ref(false);
const error = ref('');
const missing = ref(false);
const compared = ref(false);
const resetDetected = ref(false);
let controller: AbortController | undefined;
let revision = 0;
const rows = computed(() => {
  if (!current.value || !baseline.value)
    return [];
  return compared.value ? compareDigestChanges(current.value, baseline.value) : digestChangeFields.map(field => ({
    VARIABLE_NAME: field, VARIABLE_VALUE: current.value!.values[field],
  }));
});
const columns = computed(() => [
  { title: t('ui.metricName', '指标名称'), key: 'VARIABLE_NAME', slot: 'metric', minWidth: 350 },
  { title: t('ui.currentValue', '当前值'), key: 'VARIABLE_VALUE', slot: 'value', minWidth: 170 },
  ...(compared.value ? [
    { title: t('ui.baselineValue', '基准值'), key: 'VARIABLE_VALUE_OLD', slot: 'value', minWidth: 170 },
    { title: t('ui.delta', '增量'), key: 'VARIABLE_VALUE_DELTA', slot: 'value', minWidth: 160 },
    { title: t('ui.changePerSecond', '每秒变化量'), key: 'VARIABLE_VALUE_PER_SEC', slot: 'value', minWidth: 170 },
  ] : []),
]);

function invalidate(): void {
  revision++;
  controller?.abort();
  loading.value = false;
  baseline.value = undefined;
  current.value = undefined;
  compared.value = false;
  missing.value = false;
  resetDetected.value = false;
  error.value = '';
}
watch([schemaName, digest], invalidate);
watch(() => props.target, async target => {
  if (!target)
    return;
  invalidate();
  schemaName.value = target.schemaName;
  digest.value = target.digest;
  await nextTick();
  if (target === props.target)
    void load(true);
}, { immediate: true });

async function load(reset: boolean): Promise<void> {
  if (!validDigest(digest.value)) {
    error.value = t('digestChanges.invalidDigest', '请输入 32 或 64 位十六进制 Digest。');
    return;
  }
  controller?.abort();
  const request = new AbortController();
  controller = request;
  const version = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const sample = await getTopQueryChanges(schemaName.value, digest.value, request.signal);
    if (version !== revision)
      return;
    missing.value = !sample;
    resetDetected.value = false;
    if (!sample) {
      current.value = undefined;
      baseline.value = undefined;
      compared.value = false;
      return;
    }
    resetDetected.value = !reset && !!baseline.value && digestCountersReset(sample, baseline.value);
    if (reset || !baseline.value || resetDetected.value) {
      baseline.value = sample;
      compared.value = false;
    } else
      compared.value = true;
    current.value = sample;
  } catch (cause) {
    if (!request.signal.aborted && version === revision)
      error.value = cause instanceof Error ? cause.message : t('topQueries.loadFailed', '读取热点查询失败');
  } finally {
    if (version === revision)
      loading.value = false;
  }
}
onBeforeUnmount(invalidate);
</script>

<style scoped>
.description { margin-bottom: 18px; color: #808695; line-height: 1.8; }
.toolbar, .summary { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-bottom: 18px; }
label { display: flex; align-items: center; gap: 8px; }
.schema-input { width: 220px; }
.digest-input { width: 380px; }
.summary { justify-content: space-between; color: #808695; }
.metric-description { display: block; margin: 4px 0; color: #808695; }
</style>
