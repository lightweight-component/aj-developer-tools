<template>
  <section>
    <header class="panel-header">
      <div>
        <h1>{{ $t('ui.globalVariables', '全局变量') }}</h1>
        <p>{{ $t('ui.viewMySQLGlobalConfigurationFilterLocallyByVariableNameIgnoringCase', '查看 MySQL 全局配置，关键词按变量名进行本地筛选，不区分大小写。') }}</p>
      </div>
      <Button type="primary" icon="md-refresh" :loading="loading" @click="loadVariables">{{ $t('ui.refresh', '刷新') }}</Button>
    </header>
    <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    <Card :bordered="false">
      <div class="toolbar">
        <Input v-model="keyword" :placeholder="$t('ui.variableKeywordEGMaxConnections', '输入变量名关键词，如 max_connections')" clearable prefix="ios-search" class="keyword" />
        <span>{{ $t('ui.showingItems', '显示 {count} / {total} 项', { named: { count: filteredRows.length, total: variables.length } }) }}</span>
        <span v-if="updatedAt" class="updated-at">{{ $t('ui.lastUpdated', '最近更新：{time}', { named: { time: formatTime(updatedAt) } }) }}</span>
      </div>
      <Table :columns="columns" :data="filteredRows" :loading="loading" border stripe size="small" :max-height="640" :no-data-text="error ? $t('ui.loadingFailedClickRefreshToRetry', '加载失败，请点击刷新重试') : $t('ui.noMatchingGlobalVariables', '暂无匹配的全局变量')">
        <template #value="{ row }"><span class="variable-value">{{ row.VARIABLE_VALUE === null ? '—' : row.VARIABLE_VALUE === '' ? $t('ui.emptyString', '（空字符串）') : row.VARIABLE_VALUE }}</span></template>
      </Table>
    </Card>
  </section>
</template>

<script setup lang="ts">
import { t, formatTime } from "../../i18n";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { getGlobalVariables, type GlobalStatusEntry } from "../../api/realtime";

const columns = computed(() => [
  { title: t('ui.variableName', "变量名"), key: "VARIABLE_NAME", minWidth: 280, sortable: true },
  { title: t('ui.value', "变量值"), key: "VARIABLE_VALUE", minWidth: 380, slot: "value" },
]);
const variables = ref<GlobalStatusEntry[]>([]);
const keyword = ref("");
const loading = ref(false);
const error = ref("");
const updatedAt = ref<number>();
const controller = new AbortController();
const filteredRows = computed(() => {
  const filter = keyword.value.trim().toUpperCase();
  return variables.value.filter((row) => row.VARIABLE_NAME.toUpperCase().includes(filter));
});

async function loadVariables(): Promise<void> {
  if (loading.value)
    return;

  loading.value = true;
  error.value = "";

  try {
    variables.value = await getGlobalVariables(controller.signal);
    updatedAt.value = Date.now();
  } catch (cause) {
    if (!controller.signal.aborted)
      error.value = cause instanceof Error ? cause.message : t('ui.failedToLoadGlobalVariables', "读取全局变量失败");
  } finally {
    loading.value = false;
  }
}

onMounted(loadVariables);
onBeforeUnmount(() => controller.abort());
</script>

<style scoped lang="less">
.panel-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
h1 { margin: 0 0 8px; color: #17233d; font-size: 24px; font-weight: 500; }
p { margin: 0; color: #808695; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 16px; color: #808695; }
.keyword { width: 340px; }
.updated-at { margin-left: auto; }
.variable-value { display: block; padding: 8px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
