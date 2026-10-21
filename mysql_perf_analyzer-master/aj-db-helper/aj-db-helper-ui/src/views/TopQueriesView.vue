<template>
  <section>
    <h1>{{ $t('performanceSchema.topQueries', '热点查询') }}</h1>
    <p class="description">{{ $t('topQueries.description', '按 SQL 摘要汇总执行次数、等待时间及资源消耗，按选定指标降序排列。') }}</p>
    <Tabs v-model="activeTab" :animated="false">
      <TabPane :label="$t('digestChanges.summary', '摘要汇总')" name="summary">
    <div class="toolbar">
      <label>{{ $t('topQueries.by', '排序指标') }}
        <select v-model="by" :disabled="loading" :aria-label="$t('topQueries.by', '排序指标')">
          <option v-for="field in topQuerySortFields" :key="field" :value="field">{{ topQueryFieldTitles[field.toUpperCase()] }}</option>
        </select>
      </label>
      <label>{{ $t('topQueries.range', '最近出现范围') }}
        <select v-model="backToMins" :disabled="loading" :aria-label="$t('topQueries.range', '最近出现范围')">
          <option v-for="minutes in topQueryMinutes" :key="minutes" :value="minutes">{{ topQueryRangeLabel(minutes) }}</option>
        </select>
      </label>
      <label>{{ $t('topQueries.count', '返回条数') }}
        <select v-model="count" :disabled="loading" :aria-label="$t('topQueries.count', '返回条数')">
          <option v-for="size in topQueryCounts" :key="size" :value="size">{{ size }}</option>
        </select>
      </label>
      <Button type="primary" :loading="loading" @click="load">{{ $t('ui.search', '查询') }}</Button>
    </div>
    <p class="description">{{ $t('topQueries.rules', '时间范围按 LAST_SEEN 筛选最近出现的摘要，指标仍是累计值，并非该时间段内的增量。时间指标保留原始皮秒值（1 秒 = 10¹² 皮秒），内存单位为字节；当前版本未提供的字段显示 —。') }}</p>
    <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    <Card :bordered="false">
      <p v-if="applied" class="description" style="float:right">{{ $t('topQueries.applied', '当前结果：按 {by} 降序，范围 {range}，最多 {count} 条', { named: { by: topQueryFieldTitles[applied.by.toUpperCase()], range: topQueryRangeLabel(applied.backToMins), count: applied.count } }) }}</p>
      <div class="summary">
        <Input v-model="keyword" clearable class="keyword" :placeholder="$t('topQueries.keyword', '筛选数据库、摘要或 SQL')" />
        <span>{{ $t('ui.showingItems', '显示 {count} / {total} 项', { named: { count: filteredRows.length, total: rows.length } }) }}</span>
        <span v-if="updatedAt">{{ $t('ui.lastUpdated', '最近更新：{time}', { named: { time: formatTime(updatedAt) } }) }}</span>
      </div>
      <Table :columns="columns" :data="pageRows" :loading="loading" border stripe size="small" :max-height="620" :no-data-text="error ? $t('topQueries.retry', '加载失败，请查询重试') : $t('topQueries.empty', '暂无匹配的热点查询')">
        <template #value="{ row, column }"><span class="cell-value">{{ row[column.key] ?? '—' }}</span></template>
        <template #sql="{ row, column }"><SqlCodeCell v-if="row[column.key]" :value="row[column.key]" /><span v-else>—</span></template>
        <template #changes="{ row }"><Button type="text" size="small" :disabled="!row.DIGEST" :title="!row.DIGEST ? $t('digestChanges.noDigest', '空 Digest 是汇总记录，无法作为单个摘要追踪。') : ''" @click="trackChanges(row)">{{ $t('digestChanges.view', '查看变化') }}</Button></template>
      </Table>
      <Page v-if="filteredRows.length > pageSize" :current="page" :total="filteredRows.length" :page-size="pageSize" @on-change="page = $event" class="pagination" />
    </Card>
      </TabPane>
      <TabPane :label="$t('digestChanges.title', '变化追踪')" name="changes">
        <DigestChangesPanel :target="selectedDigest" />
      </TabPane>
    </Tabs>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { t, formatTime } from '../i18n';
import { getTopQueries } from '../api/top-queries';
import { topQueryFields, topQueryFieldTitles, topQuerySortFields, topQueryMinutes, topQueryCounts, topQueryRangeLabel, type TopQueryOptions, type TopQueryRow, type TopQuerySort } from '../utils/top-queries';
import SqlCodeCell from '../components/SqlCodeCell.vue';
import DigestChangesPanel from '../components/DigestChangesPanel.vue';
import type { DigestTarget } from '../utils/digest-changes';

const activeTab = ref('summary');
const selectedDigest = ref<DigestTarget>();
let selection = 0;
const by = ref<TopQuerySort>('sum_timer_wait');
const backToMins = ref(1);
const count = ref(5);
const loading = ref(false);
const error = ref('');
const rows = ref<TopQueryRow[]>([]);
const keyword = ref('');
const applied = ref<TopQueryOptions>();
const updatedAt = ref<number>();
const page = ref(1);
const pageSize = 50;
const controller = new AbortController();
const columns = computed(() => [
  ...[...new Set([...topQueryFields, ...rows.value.flatMap(row => Object.keys(row))])].map(key => ({
  title: topQueryFieldTitles[key] || key, key, slot: ['DIGEST_TEXT', 'QUERY_SAMPLE_TEXT'].includes(key) ? 'sql' : 'value',
  minWidth: ['DIGEST_TEXT', 'QUERY_SAMPLE_TEXT'].includes(key) ? 420 : key === 'DIGEST' ? 300 : 190,
  })),
  { title: t('digestChanges.actions', '操作'), slot: 'changes', fixed: 'right' as const, width: 120 },
]);
const filteredRows = computed(() => {
  const filter = keyword.value.trim().toLowerCase();
  return rows.value.filter(row => ['SCHEMA_NAME', 'DIGEST', 'DIGEST_TEXT', 'QUERY_SAMPLE_TEXT'].some(key => String(row[key] ?? '').toLowerCase().includes(filter)));
});
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize, page.value * pageSize));
watch(keyword, () => { page.value = 1; });

function trackChanges(row: TopQueryRow): void {
  if (!row.DIGEST)
    return;
  selectedDigest.value = { schemaName: row.SCHEMA_NAME ?? '', digest: row.DIGEST, selection: ++selection };
  activeTab.value = 'changes';
}

async function load(): Promise<void> {
  if (loading.value)
    return;
  const options = { by: by.value, backToMins: backToMins.value, count: count.value };
  loading.value = true;
  error.value = '';
  try {
    rows.value = await getTopQueries(options, controller.signal);
    applied.value = options;
    updatedAt.value = Date.now();
    page.value = 1;
  } catch (cause) {
    if (!controller.signal.aborted)
      error.value = cause instanceof Error ? cause.message : t('topQueries.loadFailed', '读取热点查询失败');
  } finally {
    loading.value = false;
  }
}
onMounted(load);
onBeforeUnmount(() => controller.abort());
</script>

<style scoped>
h1 { margin-bottom: 12px; }
.description { margin-bottom: 18px; color: #808695; line-height: 1.8; }
.toolbar, .summary { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; margin-bottom: 18px; }
label { display: flex; align-items: center; gap: 8px; }
select { padding: 6px 9px; border: 1px solid #dcdee2; border-radius: 4px; background: white; color: #515a6e; }
.keyword { width: 300px; }
.summary { color: #808695; }
.cell-value { overflow-wrap: anywhere; }
.pagination { margin-top: 16px; }
</style>
