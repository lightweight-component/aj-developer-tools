<template>
  <section>
    <h1>{{ $t('performanceSchema.metadataLocks', '元数据锁') }}</h1>
    <p class="description">{{ $t('performanceSchema.metadataLocksDescription', '元数据锁（MDL）用于保护表等数据库对象的结构，避免查询或事务执行期间被并发修改。未结束的事务可能持有锁，使 ALTER TABLE 等操作等待；不兼容的待授予锁也可能影响后续访问。') }}</p>
    <div class="toolbar">
      <Button type="primary" icon="md-refresh" :loading="loading" @click="load">{{ $t('ui.refresh', '刷新') }}</Button>
      <Input v-model="keyword" clearable class="keyword" :placeholder="$t('metadataLocks.keyword', '筛选连接、用户、对象或 SQL')" />
      <Checkbox v-model="onlyPending">{{ $t('metadataLocks.onlyPending', '仅显示等待中的锁') }}</Checkbox>
    </div>
    <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    <p class="description">{{ $t('metadataLocks.statusHelp', 'GRANTED 表示锁已授予，PENDING 表示正在等待授予。状态持续时间是会话处于当前状态的时间，不等于锁等待时长；锁持续范围表示锁按语句、事务或显式释放管理，不是秒数。') }}</p>
    <Card :bordered="false">
      <div class="summary">
        <span>{{ $t('ui.showingItems', '显示 {count} / {total} 项', { named: { count: filteredRows.length, total: rows.length } }) }}</span>
        <span v-if="updatedAt">{{ $t('ui.lastUpdated', '最近更新：{time}', { named: { time: formatTime(updatedAt) } }) }}</span>
      </div>
      <Table :columns="columns" :data="pageRows" :loading="loading" border stripe size="small" :max-height="620" :no-data-text="error ? $t('metadataLocks.retry', '加载失败，请刷新重试') : $t('metadataLocks.empty', '暂无匹配的元数据锁记录')">
        <template #value="{ row, column }">{{ row[column.key] ?? '—' }}</template>
        <template #status="{ row }"><Tag v-if="row.LOCK_STATUS" :color="row.LOCK_STATUS === 'PENDING' ? 'warning' : row.LOCK_STATUS === 'GRANTED' ? 'success' : 'default'">{{ row.LOCK_STATUS }}</Tag><span v-else>—</span></template>
        <template #sql="{ row }"><SqlCodeCell v-if="row.PROCESSLIST_INFO" :value="String(row.PROCESSLIST_INFO)" /><span v-else>—</span></template>
      </Table>
      <Page v-if="filteredRows.length > pageSize" :current="page" :total="filteredRows.length" :page-size="pageSize" @on-change="page = $event" class="pagination" />
    </Card>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { t, formatTime } from '../i18n';
import { getMetadataLocks, metadataLockFields, type MetadataLockRow } from '../api/performance-schema';
import SqlCodeCell from '../components/SqlCodeCell.vue';

const rows = ref<MetadataLockRow[]>([]);
const loading = ref(false);
const error = ref('');
const keyword = ref('');
const onlyPending = ref(false);
const page = ref(1);
const pageSize = 50;
const updatedAt = ref<number>();
const controller = new AbortController();
const titles = computed(() => [
  t('metadataLocks.connectionId', '连接 ID'), t('ui.user', '用户'), t('ui.clientAddress', '客户端地址'),
  t('ui.command', '命令'), t('metadataLocks.sessionState', '会话状态'), t('metadataLocks.stateTime', '状态持续时间（秒）'),
  t('metadataLocks.objectType', '对象类型'), t('metadataLocks.objectSchema', '对象数据库'), t('metadataLocks.objectName', '对象名称'),
  t('metadataLocks.lockType', '锁类型'), t('metadataLocks.lockStatus', '锁状态'), t('metadataLocks.lockDuration', '锁持续范围'), t('ui.sqlStatement', 'SQL 语句'),
]);
const columns = computed(() => metadataLockFields.map((key, index) => ({
  title: titles.value[index], key, minWidth: key === 'PROCESSLIST_INFO' ? 380 : key === 'PROCESSLIST_TIME' ? 190 : 150,
  slot: key === 'PROCESSLIST_INFO' ? 'sql' : key === 'LOCK_STATUS' ? 'status' : 'value',
})));
const filteredRows = computed(() => {
  const filter = keyword.value.trim().toLowerCase();
  return rows.value.filter(row => (!onlyPending.value || row.LOCK_STATUS === 'PENDING')
    && (!filter || metadataLockFields.some(key => String(row[key] ?? '').toLowerCase().includes(filter))));
});
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize, page.value * pageSize));
watch([keyword, onlyPending], () => { page.value = 1; });

async function load(): Promise<void> {
  if (loading.value)
    return;
  loading.value = true;
  error.value = '';
  try {
    rows.value = await getMetadataLocks(controller.signal);
    updatedAt.value = Date.now();
    page.value = 1;
  } catch (cause) {
    if (!controller.signal.aborted)
      error.value = cause instanceof Error ? cause.message : t('metadataLocks.loadFailed', '读取元数据锁失败');
  } finally {
    loading.value = false;
  }
}
onMounted(load);
onBeforeUnmount(() => controller.abort());
</script>

<style scoped>
h1 { margin-bottom: 12px; }
.description { color: #808695; margin-bottom: 18px; line-height: 1.8; }
.toolbar, .summary { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 16px; }
.summary { justify-content: space-between; color: #808695; }
.keyword { width: 320px; }
.pagination { margin-top: 16px; }
</style>
