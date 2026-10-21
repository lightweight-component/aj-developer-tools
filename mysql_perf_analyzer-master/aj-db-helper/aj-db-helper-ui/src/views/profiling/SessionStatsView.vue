<template>
  <section>
    <h1>{{ $t('ui.profiling', '性能剖析') }}</h1>
    <p class="description">{{ $t('ui.theSELECTRunsOnceAllRowsAreReadToCollectSession', 'SELECT 只执行一次，读取全部结果后生成会话统计与阶段分析，不展示查询数据。查询执行超时为 60 秒。') }}</p>
    <Card :bordered="false">
      <div class="editor-label">{{ $t('ui.sqlQuery', 'SQL 查询') }}</div>
      <SqlQueryEditor v-model="query" />
      <div class="actions">
        <Button type="primary" :loading="loading" @click="analyze">{{ $t('ui.analyze', '提交分析') }}</Button>
      </div>
      <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    </Card>
    <Card v-if="stats !== null" :bordered="false" class="result-card">
      <p class="result-sql">{{ $t('ui.sqlForTheseResults', '结果对应 SQL：') }}<code>{{ resultSql }}</code></p>
      <Tabs v-model="activeTab">
        <TabPane :label="$t('ui.sessionStats', '会话统计（Session Stats）')" name="session-stats">
          <p class="description">{{ $t('ui.onlyChangedCountersAreShownLASTQUERYMetricsShowTheLast', '显示有变化的计数器；LAST_QUERY 指标显示末次查询值。统计包含状态采样及 JDBC 驱动开销。') }}</p>
          <Table :columns="columns" :data="stats" border stripe size="small" :max-height="620" :no-data-text="$t('ui.noSessionStatistics', '没有会话统计数据')">
            <template #variable="{ row }">
              <span class="variable-name">{{ row.VARIABLE_NAME }}</span>
              <span v-if="getSessionStatDescription(row.VARIABLE_NAME)" class="variable-description">{{ getSessionStatDescription(row.VARIABLE_NAME) }}</span>
            </template>
          </Table>
        </TabPane>
        <TabPane :label="$t('ui.profileInfo', '阶段分析（Profile Info）')" name="profile-info">
          <Alert v-if="!profileAvailable" type="warning" show-icon>{{ profileMessage || $t('ui.profileInformationIsNotAvailableForThisDatabase', '当前数据库没有可用的阶段分析资料。') }}</Alert>
          <template v-else>
            <p class="description">{{ $t('ui.stagesAreShownInExecutionOrderWithDurationsInSecondsStage', '按实际执行顺序显示各阶段，耗时单位为秒；同名阶段可能重复。整列为空或零的可选指标自动隐藏。') }}</p>
            <Table :columns="profileColumns" :data="profileInfo" border stripe size="small" :max-height="620" :no-data-text="$t('ui.noProfileInformation', '没有阶段分析数据')" />
          </template>
        </TabPane>
      </Tabs>
    </Card>
  </section>
</template>

<script setup lang="ts">
import { t } from "../../i18n";
import { computed, onBeforeUnmount, ref } from "vue";
import SqlQueryEditor from "../../components/SqlQueryEditor.vue";
import { analyzeQuery, type SessionStat } from "../../api/profiling";
import { getSessionStatDescription } from "../../utils/session-stat-descriptions";
import { profileColumnTitles, visibleProfileColumns, type ProfileRow } from "../../utils/profile-info";

const query = ref("");
const resultSql = ref("");
const loading = ref(false);
const error = ref("");
const stats = ref<SessionStat[] | null>(null);
const profileInfo = ref<ProfileRow[]>([]);
const profileAvailable = ref(false);
const profileMessage = ref("");
const activeTab = ref("session-stats");
const profileColumns = computed(() => visibleProfileColumns(profileInfo.value).map((key) => ({
  title: profileColumnTitles[key] || key, key, minWidth: key === "STATUS" ? 240 : 160,
})));
const controller = new AbortController();
const columns = computed(() => [
  { title: t('ui.variableDescription', "变量名 / 说明"), key: "VARIABLE_NAME", slot: "variable", minWidth: 540, sortable: true },
  { title: t('ui.value', "变量值"), key: "VARIABLE_VALUE", minWidth: 180 },
]);

async function analyze(): Promise<void> {
  if (loading.value)
    return;

  const sql = query.value.trim();

  if (!sql) {
    error.value = t('ui.enterTheSELECTQueryToAnalyzeFirst', "请先输入需要分析的 SELECT 查询。");
    return;
  }

  loading.value = true;
  error.value = "";
  stats.value = null;

  try {
    const result = await analyzeQuery(sql, controller.signal);
    stats.value = result.sessionStats;
    profileInfo.value = result.profileInfo;
    profileAvailable.value = result.profileAvailable;
    profileMessage.value = result.profileMessage || "";
    resultSql.value = sql;
  } catch (cause) {
    if (!controller.signal.aborted)
      error.value = cause instanceof Error ? cause.message : t('ui.failedToLoadSessionStatistics', "读取会话统计失败");
  } finally {
    loading.value = false;
  }
}

onBeforeUnmount(() => controller.abort());
</script>

<style scoped>
.description { margin: 10px 0 16px; color: #808695; }
.editor-label { margin-bottom: 8px; font-weight: 500; }
.actions { margin: 16px 0; }
.result-card { margin-top: 20px; }
.result-sql { margin-bottom: 16px; overflow-wrap: anywhere; }
.variable-name { font-family: Consolas, monospace; overflow-wrap: anywhere; }
.variable-description { margin-left: 12px; color: #707784; }
</style>
