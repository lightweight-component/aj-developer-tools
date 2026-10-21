import { t } from "../i18n";
export interface PlanTreeNode {
  title: string;
  expand: boolean;
  children?: PlanTreeNode[];
}

/** 兼容 JDBC 返回的 EXPLAIN 字符串列，以及已解析的 JSON 对象。 */
export function parseExplainJson(response: Record<string, unknown>): Record<string, unknown> {
  if (!response || typeof response !== "object" || Array.isArray(response))
    throw new Error(t('ui.invalidJSONExecutionPlan', "JSON 执行计划格式错误"));

  const explain = Object.entries(response).find(([key]) => key.toUpperCase() === "EXPLAIN");
  const value = explain ? explain[1] : response;
  let parsed: unknown;

  try {
    parsed = typeof value === "string" ? JSON.parse(value) : value;
  } catch {
    throw new Error(t('ui.theServerEXPLAINColumnDoesNotContainValidJSON', "服务端 EXPLAIN 列不是有效 JSON"));
  }

  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
    throw new Error(t('ui.theJSONExecutionPlanMustBeAnObject', "JSON 执行计划应为对象"));

  return parsed as Record<string, unknown>;
}

const labels: Record<string, string> = {
  get query_block() { return t('ui.queryBlock', "查询块"); }, get nested_loop() { return t('ui.nestedLoopJoin', "嵌套循环连接"); }, get table() { return t('ui.table', "数据表"); },
  get cost_info() { return t('ui.costInformation', "成本信息"); }, get ordering_operation() { return t('ui.orderingOperation', "排序操作"); }, get grouping_operation() { return t('ui.groupingOperation', "分组操作"); },
  get duplicates_removal() { return t('ui.duplicateRemoval', "去重"); }, get attached_subqueries() { return t('ui.attachedSubqueries', "附加子查询"); }, get materialized_from_subquery() { return t('ui.materializedSubquery', "物化子查询"); },
  get possible_keys() { return t('ui.possibleIndexes', "候选索引"); }, get used_key_parts() { return t('ui.usedIndexColumns', "使用的索引字段"); }, get used_columns() { return t('ui.usedColumns', "使用列"); },
  get attached_condition() { return t('ui.attachedCondition', "附加条件"); }, get access_type() { return t('ui.accessMethod', "访问方式"); }, get table_name() { return t('ui.tableName', "表名"); },
  get rows_examined_per_scan() { return t('ui.rowsExaminedPerScan', "每次扫描行数"); }, get rows_produced_per_join() { return t('ui.rowsProducedPerJoin', "连接输出行数"); },
  get filtered() { return t('ui.filteredPercentage', "过滤比例"); }, get key() { return t('ui.selectedIndex', "使用索引"); }, get key_length() { return t('ui.indexLength', "索引长度"); },
};

export function buildPlanTree(plan: Record<string, unknown>): PlanTreeNode[] {
  function node(key: string, value: unknown, depth: number): PlanTreeNode {
    const label = labels[key] ? `${key}（${labels[key]}）` : key;

    if (value !== null && typeof value === "object") {
      if (Array.isArray(value) && value.every((item) => item === null || typeof item !== "object"))
        return { title: `${label}: ${JSON.stringify(value)}`, expand: false };

      const children = Object.entries(value).map(([childKey, child]) => node(childKey, child, depth + 1));
      const table = !Array.isArray(value) ? value as Record<string, unknown> : null;
      const summary = key === "table" && table?.table_name
        ? t('ui.accessRowsPerScan', '：{table}（访问方式：{access}，每次扫描行数：{rows}）', {
            named: { table: String(table.table_name), access: String(table.access_type ?? '—'), rows: String(table.rows_examined_per_scan ?? '—') },
          })
        : "";
      return { title: label + summary, expand: depth < 3, children };
    }

    return { title: `${label}: ${value === null ? "null" : String(value)}`, expand: false };
  }

  return [{ get title() { return t('ui.executionPlan', "执行计划"); }, expand: true, children: Object.entries(plan).map(([key, value]) => node(key, value, 0)) }];
}
