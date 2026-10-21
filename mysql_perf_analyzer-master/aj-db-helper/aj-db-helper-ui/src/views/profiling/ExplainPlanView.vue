<template>
  <section>
    <h1>{{ $t('ui.simpleExecutionPlan', '简单执行计划') }}</h1>
    <p class="description">{{ $t('ui.enterASELECTQueryToInspectTableAccessIndexesAndEstimated', '输入 SELECT 查询，查看表访问方式、索引与预估成本。') }}</p>
    <Card :bordered="false">
      <div class="editor-label">{{ $t('ui.sqlQuery', 'SQL 查询') }}</div>
      <SqlQueryEditor v-model="query" />
      <div class="actions">
        <Button type="primary" :loading="loading === 'plan'" :disabled="loading === 'json'" @click="analyze('plan')">{{ $t('ui.executionPlan', '执行计划') }}</Button>
        <Button :loading="loading === 'json'" :disabled="loading === 'plan'" @click="analyze('json')">{{ $t('ui.jsonExecutionPlan', 'JSON 执行计划') }}</Button>
      </div>
      <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    </Card>

    <div v-if="resultSql" class="result-sql">{{ $t('ui.sqlForTheseResults', '结果对应 SQL：') }}<code>{{ resultSql }}</code></div>
    <Card v-if="planRows !== null" :bordered="false" class="result-card">
      <template #title>{{ $t('ui.executionPlanTable', '执行计划表格') }} <span class="count">{{ $t('ui.rows', '共 {count} 行', { named: { count: planRows.length } }) }}</span></template>
      <Table :columns="columns" :data="planRows" border stripe size="small" :max-height="480" :no-data-text="$t('ui.theQueryReturnedNoExecutionPlan', '查询未返回执行计划')">
        <template #value="{ row, column }">{{ row[column.key] ?? '—' }}</template>
      </Table>
    </Card>
    <Card v-if="jsonPlan !== null" :bordered="false" class="result-card">
      <template #title>{{ $t('ui.executionPlanTree', '执行计划树') }}</template>
      <Tree :data="tree" class="plan-tree" :expand-node="true" />
      <Button size="small" class="raw-toggle" @click="showRaw = !showRaw">{{ showRaw ? $t('ui.hideRawJSON', '收起原始 JSON') : $t('ui.viewRawJSON', '查看原始 JSON') }}</Button>
      <JsonCodeViewer v-if="showRaw" class="raw-json" :value="JSON.stringify(jsonPlan, null, 2)" />
    </Card>
  </section>
</template>

<script setup lang="ts">
import { t } from "../../i18n";
import { computed, onBeforeUnmount, ref } from "vue";
import SqlQueryEditor from "../../components/SqlQueryEditor.vue";
import JsonCodeViewer from "../../components/JsonCodeViewer.vue";
import { getExplainPlan, getExplainPlanJson, type ExplainRow } from "../../api/profiling";
import { buildPlanTree, parseExplainJson } from "../../utils/explain-plan";

const query = ref("");
const resultSql = ref("");
const loading = ref<"plan" | "json" | null>(null);
const error = ref("");
const planRows = ref<ExplainRow[] | null>(null);
const jsonPlan = ref<Record<string, unknown> | null>(null);
const showRaw = ref(false);
const controller = new AbortController();
const tree = computed(() => jsonPlan.value ? buildPlanTree(jsonPlan.value) : []);
const fieldTitles: Record<string, string> = {
  get ID() { return t('ui.id', "序号"); }, get SELECT_TYPE() { return t('ui.selectType', "查询类型"); }, get TABLE() { return t('ui.table', "数据表"); }, get PARTITIONS() { return t('ui.partitions', "分区"); },
  get TYPE() { return t('ui.accessType', "访问类型"); }, get POSSIBLE_KEYS() { return t('ui.possibleIndexes', "候选索引"); }, get KEY() { return t('ui.selectedIndex', "使用索引"); }, get KEY_LEN() { return t('ui.indexLength', "索引长度"); },
  get REF() { return t('ui.referencedColumns', "引用列"); }, get ROWS() { return t('ui.estimatedRows', "预估行数"); }, get FILTERED() { return t('ui.filtered', "过滤比例（%）"); }, get EXTRA() { return t('ui.extraInformation', "额外信息"); },
};
const columns = computed(() => {
  const keys = new Set([...Object.keys(fieldTitles), ...(planRows.value ?? []).flatMap((row) => Object.keys(row))]);
  return [...keys].map((key) => ({
    title: fieldTitles[key] ?? key, key, slot: "value",
    minWidth: key === "POSSIBLE_KEYS" || key === "EXTRA" ? 190 : 120,
    sortable: true,
  }));
});

async function analyze(mode: "plan" | "json"): Promise<void> {
  if (loading.value)
    return;

  const sql = query.value.trim();

  if (!sql) {
    error.value = t('ui.enterTheSQLQueryToAnalyzeFirst', "请先输入需要分析的 SQL 查询。");
    return;
  }

  loading.value = mode;
  error.value = "";

  try {
    if (mode === "plan") {
      const rows = await getExplainPlan(sql, controller.signal);

      if (resultSql.value !== sql)
        jsonPlan.value = null;

      planRows.value = rows;
    } else {
      const plan = parseExplainJson(await getExplainPlanJson(sql, controller.signal));

      if (resultSql.value !== sql)
        planRows.value = null;

      jsonPlan.value = plan;
      showRaw.value = false;
    }

    resultSql.value = sql;
  } catch (cause) {
    if (!controller.signal.aborted)
      error.value = cause instanceof Error ? cause.message : t('ui.failedToLoadTheExecutionPlan', "读取执行计划失败");
  } finally {
    loading.value = null;
  }
}

onBeforeUnmount(() => controller.abort());
</script>

<style scoped lang="less">
h1 { margin: 0 0 8px; color: #17233d; font-size: 24px; font-weight: 500; }
.description { margin-bottom: 24px; color: #808695; }
.editor-label { margin-bottom: 10px; }
.actions { display: flex; gap: 12px; margin: 16px 0; }
.result-card { margin-top: 18px; }
.result-sql { margin-top: 18px; color: #808695; overflow-wrap: anywhere; }
.count { margin-left: 12px; color: #808695; font-size: 12px; font-weight: normal; }
.plan-tree { overflow: auto; }
.plan-tree :deep(.ivu-tree-title) { white-space: normal; overflow-wrap: anywhere; }
.raw-toggle { margin-top: 16px; }
.raw-json { margin-top: 12px; }
</style>
