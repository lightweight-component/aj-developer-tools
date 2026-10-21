<template>
  <section>
    <h1>{{ $t('ui.globalStatus', '全局状态') }}</h1>
    <p class="description">{{ $t('ui.deltaCurrentValueMinusBaselineRateDeltaDividedByTheSampling', '增量 = 当前值 − 基准值；每秒速率 = 增量 ÷ 两次采样间隔。非数值或新出现的指标不计算增量。') }}</p>
    <div class="toolbar">
      <Input v-model="keyword" :placeholder="$t('ui.metricKeywordEGThreadsOrBytes', '输入指标关键词，如 Threads、Bytes')" clearable :disabled="loading" @on-enter="load(true)" class="keyword" />
      <Button :disabled="loading" @click="load(true)">{{ $t('ui.search', '查询') }}</Button>
      <Button type="primary" icon="md-refresh" :loading="loading" @click="load(false)">{{ $t('ui.refreshAndCompare', '刷新并比较') }}</Button>
      <Button :disabled="loading || !current" @click="resetBaseline">{{ $t('ui.resetBaseline', '重设基准') }}</Button>
      <Checkbox v-model="onlyChanged">{{ $t('ui.onlyShowChanges', '仅显示变化项') }}</Checkbox>
    </div>
    <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    <Alert v-if="notice" show-icon>{{ notice }}</Alert>
    <Card :bordered="false">
      <div class="summary">
        <span>{{ $t('ui.items', '共 {count} 项', { named: { count: rows.length } }) }}</span>
        <span v-if="baseline && current">{{ $t('ui.baselineCurrentIntervalSeconds', '基准：{baseline}　当前：{current}　间隔：{interval} 秒', { named: { baseline: time(baseline.sampledAt), current: time(current.sampledAt), interval: ((current.sampledAt - baseline.sampledAt) / 1000).toFixed(3) } }) }}</span>
      </div>
      <Table :columns="columns" :data="rows" :loading="loading" border stripe size="small" :max-height="640" :no-data-text="$t('ui.noMatchingStatusMetrics', '暂无匹配的状态指标')">
        <template #value="{ row, column }">{{ row[column.key] ?? '—' }}</template>
      </Table>
    </Card>
  </section>
</template>

<script setup lang="ts">
import { t, formatTime } from "../../i18n";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { getGlobalStatus, type GlobalStatusSnapshot } from "../../api/realtime";
import { compareGlobalStatus } from "../../utils/global-status";

const columns = computed(() => [
  { title: t('ui.metricName', "指标名称"), key: "VARIABLE_NAME", minWidth: 260, sortable: true },
  { title: t('ui.changePerSecond', "每秒变化量"), key: "VARIABLE_VALUE_PER_SEC", minWidth: 145, slot: "value" },
  { title: t('ui.delta', "增量"), key: "VARIABLE_VALUE_DELTA", minWidth: 140, slot: "value" },
  { title: t('ui.currentValue', "当前值"), key: "VARIABLE_VALUE", minWidth: 150, slot: "value" },
  { title: t('ui.baselineValue', "基准值"), key: "VARIABLE_VALUE_OLD", minWidth: 150, slot: "value" },
]);
const keyword = ref("");
const appliedKeyword = ref("");
const onlyChanged = ref(false);
const loading = ref(false);
const error = ref("");
const notice = ref("");
const baseline = ref<GlobalStatusSnapshot>();
const current = ref<GlobalStatusSnapshot>();
const controller = new AbortController();
const rows = computed(() => {
  if (!current.value || !baseline.value)
    return [];

  const result = compareGlobalStatus(current.value, baseline.value);
  return onlyChanged.value ? result.filter((row) => row.changed) : result;
});

function time(timestamp: number): string {
  return formatTime(timestamp);
}

function resetBaseline(): void {
  baseline.value = current.value;
  notice.value = t('ui.theCurrentSampleIsNowTheBaselineRefreshToSeeChanges', "已将当前采样设为基准，下次刷新后显示增量。");
}

async function load(newBaseline: boolean): Promise<void> {
  if (loading.value)
    return;

  loading.value = true;
  error.value = "";

  try {
    const filter = keyword.value.trim();
    const snapshot = await getGlobalStatus(filter, controller.signal);
    const restarted = baseline.value?.uptime != null && snapshot.uptime != null && snapshot.uptime < baseline.value.uptime;
    notice.value = "";

    if (newBaseline || !baseline.value || filter !== appliedKeyword.value || restarted) {
      baseline.value = snapshot;
      notice.value = restarted ? t('ui.databaseUptimeDecreasedTheComparisonBaselineHasBeenReset', "检测到数据库运行时间回退，已自动重设比较基准。") : t('ui.aBaselineHasBeenCreatedClickRefreshAndCompareToSee', "已建立采样基准，点击“刷新并比较”查看增量。");
    }

    appliedKeyword.value = filter;
    current.value = snapshot;
  } catch (cause) {
    if (!controller.signal.aborted)
      error.value = cause instanceof Error ? cause.message : t('ui.failedToLoadGlobalStatus', "读取全局状态失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => load(true));
onBeforeUnmount(() => controller.abort());
</script>

<style scoped lang="less">
h1 { margin: 0 0 8px; color: #17233d; font-size: 24px; font-weight: 500; }
.description { margin-bottom: 24px; color: #808695; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 18px; }
.keyword { width: 300px; }
.summary { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; color: #808695; }
</style>
