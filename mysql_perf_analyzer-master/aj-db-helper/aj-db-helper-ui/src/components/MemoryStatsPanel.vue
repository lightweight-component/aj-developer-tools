<template>
  <section>
    <p class="description">{{ $t('memory.rules', '刷新并比较时只显示发生变化的行；每秒速率按距基准建立时的总时间计算。重设基准只影响当前页面，不会清空数据库统计或释放内存。') }}</p>
    <div class="toolbar">
      <template v-if="scope === 'thread'">
        <label for="memory-thread-id">{{ $t('memory.threadId', '线程 ID') }}</label>
        <Input element-id="memory-thread-id" v-model="threadId" :disabled="loading" clearable :placeholder="$t('memory.threadPlaceholder', '留空查询全部线程，可使用 %、_ 通配符')" class="keyword" @on-enter="load(true)" />
        <Button :disabled="loading" @click="load(true)">{{ $t('ui.search', '查询') }}</Button>
      </template>
      <Button type="primary" icon="md-refresh" :loading="loading" @click="load(false)">{{ $t('ui.refreshAndCompare', '刷新并比较') }}</Button>
      <Button :disabled="loading" @click="load(true)">{{ $t('ui.resetBaseline', '重设基准') }}</Button>
      <Input v-model="keyword" clearable :placeholder="$t('memory.keyword', '按事件名称筛选')" class="keyword" />
    </div>
    <p v-if="scope === 'thread'" class="description">{{ $t('memory.threadRules', '线程 ID 是 Performance Schema 内部编号，不是进程列表的连接 ID。修改线程筛选后重新查询，会建立新基准。') }}</p>
    <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    <Alert v-if="current" show-icon>{{ compared ? $t('memory.comparing', '正在与固定基准比较，连续刷新不会移动基准。') : $t('memory.baselineReady', '当前显示原始采样值，已作为比较基准。点击刷新并比较查看变化。') }}</Alert>
    <Card :bordered="false">
      <div class="summary">
        <span>{{ $t('ui.items', '共 {count} 项', { named: { count: rows.length } }) }}</span>
        <span v-if="scope === 'thread' && current">{{ $t('memory.appliedThread', '已查询线程：{thread}', { named: { thread: appliedFilter || $t('memory.allThreads', '全部线程') } }) }}</span>
        <span v-if="baseline && current">{{ $t('ui.baselineCurrentIntervalSeconds', '基准：{baseline}　当前：{current}　间隔：{interval} 秒', { named: { baseline: formatTime(baseline.sampledAt), current: formatTime(current.sampledAt), interval: ((current.sampledAt - baseline.sampledAt) / 1000).toFixed(3) } }) }}</span>
      </div>
      <Table :columns="columns" :data="pageRows" :loading="loading" border stripe size="small" :max-height="620" :no-data-text="compared ? $t('memory.noChanges', '没有匹配的变化项') : $t('memory.noData', '暂无匹配的内存统计')">
        <template #value="{ row, column }">{{ row[column.key] ?? '—' }}</template>
      </Table>
      <Page v-if="rows.length > pageSize" :total="rows.length" :page-size="pageSize" :current="page" @on-change="page = $event" class="pagination" />
    </Card>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { t, formatTime } from '../i18n';
import { getMemoryGlobal, getMemoryThread } from '../api/performance-schema';
import { compareMemory, memoryFields, normalizeThreadFilter, type MemorySnapshot } from '../utils/memory-global';

const props = defineProps<{ scope: 'global' | 'thread' }>();
const threadId = ref('');
const appliedFilter = ref('');
const page = ref(1);
const pageSize = 50;

const baseline = ref<MemorySnapshot>();
const current = ref<MemorySnapshot>();
const compared = ref(false);
const loading = ref(false);
const error = ref('');
const keyword = ref('');
const controller = new AbortController();
const fieldNames = computed(() => [
  t('memory.allocCount', '分配次数'), t('memory.freeCount', '释放次数'),
  t('memory.allocBytes', '累计分配字节数'), t('memory.freeBytes', '累计释放字节数'),
  t('memory.lowCount', '最低使用块数'), t('memory.currentCount', '当前使用块数'), t('memory.highCount', '最高使用块数'),
  t('memory.lowBytes', '最低使用字节数'), t('memory.currentBytes', '当前使用字节数'), t('memory.highBytes', '最高使用字节数'),
]);
const rows = computed(() => {
  if (!current.value || !baseline.value)
    return [];
  const values = compared.value ? compareMemory(current.value, baseline.value) : current.value.rows;
  const filter = keyword.value.trim().toLowerCase();
  return values.filter(row => row.EVENT_NAME?.toLowerCase().includes(filter));
});
const columns = computed(() => [
  ...(props.scope === 'thread' ? [{ title: t('memory.threadId', '线程 ID'), key: 'THREAD_ID', fixed: 'left' as const, width: 100 }] : []),
  { title: t('memory.eventName', '事件名称'), key: 'EVENT_NAME', fixed: 'left' as const, width: 320, sortable: true },
  ...memoryFields.map((field, index) => compared.value ? {
    title: fieldNames.value[index], children: [
      { title: t('ui.currentValue', '当前值'), key: field, width: 160, slot: 'value' },
      { title: t('ui.baselineValue', '基准值'), key: field + '_OLD', width: 160, slot: 'value' },
      { title: t('ui.delta', '增量'), key: field + '_DELTA', width: 150, slot: 'value' },
      { title: t('ui.changePerSecond', '每秒变化量'), key: field + '_PER_SEC', width: 160, slot: 'value' },
    ],
  } : { title: fieldNames.value[index], key: field, width: 190, slot: 'value' }),
]);

async function load(reset: boolean): Promise<void> {
  if (loading.value)
    return;
  loading.value = true;
  error.value = '';
  try {
    const filter = props.scope === 'thread' ? normalizeThreadFilter(threadId.value) : '';
    const sample = props.scope === 'thread'
      ? await getMemoryThread(filter, controller.signal)
      : await getMemoryGlobal(controller.signal);
    if (reset || !baseline.value || filter !== appliedFilter.value) {
      baseline.value = sample;
      compared.value = false;
    } else
      compared.value = true;
    current.value = sample;
    appliedFilter.value = filter;
    page.value = 1;
  } catch (cause) {
    if (!controller.signal.aborted)
      error.value = cause instanceof Error && cause.message === 'INVALID_THREAD_FILTER'
        ? t('memory.invalidThread', '线程 ID 只能包含数字及通配符 %、_。')
        : cause instanceof Error ? cause.message : t('memory.loadFailed', '读取内存统计失败');
  } finally {
    loading.value = false;
  }
}
const pageRows = computed(() => rows.value.slice((page.value - 1) * pageSize, page.value * pageSize));
watch(keyword, () => { page.value = 1; });
watch(threadId, (value, old) => {
  if (value.trim() === old.trim())
    return;
  baseline.value = undefined;
  current.value = undefined;
  compared.value = false;
  error.value = '';
  page.value = 1;
});
onMounted(() => load(true));
onBeforeUnmount(() => controller.abort());
</script>

<style scoped>
h1 { margin-bottom: 16px; }
.description { margin: 8px 0 18px; color: #808695; }
.toolbar, .summary { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 16px; }
.summary { justify-content: space-between; color: #808695; }
.keyword { width: 280px; }
.pagination { margin-top: 16px; }
</style>



