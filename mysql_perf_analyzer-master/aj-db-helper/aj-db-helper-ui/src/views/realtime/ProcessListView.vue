<template>
  <section>
    <header class="panel-header">
      <div>
        <h1>{{ $t('ui.processes', '进程列表') }}</h1>
        <p>{{ $t('ui.viewActiveMySQLConnectionsSessionsAndRunningSQL', '查看 MySQL 活动连接、会话与正在执行的 SQL。') }}</p>
      </div>
      <div class="panel-actions">
        <Checkbox v-model="onlyActive" :disabled="loading" @on-change="loadProcesses">{{ $t('ui.onlyShowActiveProcesses', '仅显示活跃进程') }}</Checkbox>
        <Button type="primary" icon="md-refresh" :loading="loading" @click="loadProcesses">{{ $t('ui.refresh', '刷新') }}</Button>
      </div>
    </header>

    <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>

    <Card :bordered="false">
      <div class="table-summary">
        <span>{{ $t('ui.processes2', '共 {count} 个进程', { named: { count: rows.length } }) }}</span>
        <span v-if="updatedAt">{{ $t('ui.lastUpdated', '最近更新：{time}', { named: { time: formatTime(updatedAt) } }) }}</span>
      </div>
      <Table :columns="columns" :data="rows" :loading="loading" :max-height="640" border stripe size="small"
        :no-data-text="error ? $t('ui.failedToLoadProcessesClickRefreshToRetry', '进程列表加载失败，请点击刷新重试') : $t('ui.noProcesses', '暂无进程')">
        <template #info="{ row }">
          <SqlCodeCell v-if="row.INFO" :value="row.INFO" />
          <span v-else>—</span>
        </template>
      </Table>
    </Card>
  </section>
</template>

<script setup lang="ts">
import { t, formatTime } from "../../i18n";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { getProcessList, type ProcessListEntry } from "../../api/realtime";
import SqlCodeCell from "../../components/SqlCodeCell.vue";

const columns = computed(() => [
  { title: t('ui.processID', "进程 ID"), key: "ID", width: 100, sortable: true },
  { title: t('ui.user', "用户"), key: "USER", minWidth: 110 },
  { title: t('ui.clientAddress', "客户端地址"), key: "HOST", minWidth: 190 },
  { title: t('ui.database', "数据库"), key: "DB", minWidth: 100 },
  { title: t('ui.command', "命令"), key: "COMMAND", minWidth: 100 },
  { title: t('ui.durationSeconds2', "持续时间（秒）"), key: "TIME", width: 155, sortable: true },
  { title: t('ui.state', "状态"), key: "STATE", minWidth: 120 },
  { title: t('ui.sqlStatement', "SQL 语句"), key: "INFO", slot: "info", minWidth: 380 },
]);

const rows = ref<ProcessListEntry[]>([]);
const onlyActive = ref(true);
const loading = ref(false);
const error = ref("");
const updatedAt = ref<number>();
const controller = new AbortController();

async function loadProcesses(): Promise<void> {
  if (loading.value)
    return;

  loading.value = true;
  error.value = "";

  try {
    rows.value = await getProcessList(!onlyActive.value, controller.signal);
    updatedAt.value = Date.now();
  } catch (cause) {
    if (!controller.signal.aborted)
      error.value = cause instanceof Error ? cause.message : t('ui.failedToLoadTheProcessList', "读取进程列表失败");
  } finally {
    loading.value = false;
  }
}

onMounted(loadProcesses);
onBeforeUnmount(() => controller.abort());
</script>

<style scoped lang="less">
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;

  h1 {
    margin: 0 0 8px;
    color: #17233d;
    font-size: 24px;
    font-weight: 500;
  }

  p {
    margin: 0;
    color: #808695;
  }
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-summary {
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
  color: #808695;
}

</style>
