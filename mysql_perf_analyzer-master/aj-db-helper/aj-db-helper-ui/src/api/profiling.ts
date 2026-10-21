import { t } from "../i18n";
export type ExplainRow = Record<string, string | number | boolean | null>;

/** Spring @RequestParam 接收表单字段，而不是 JSON 请求体。 */
async function requestPlan<T>(endpoint: string, sql: string, signal?: AbortSignal): Promise<T> {
  const root = (import.meta.env.VITE_DB_API_ROOT || "/api_v1").replace(/\/+$/, "");
  const response = await fetch(`${root}/profiling/${endpoint}`, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
    body: new URLSearchParams({ sql }),
    signal,
  });

  if (!response.headers.get("content-type")?.includes("application/json"))
    throw new Error(response.ok ? t('ui.theAPIDidNotReturnJSONCheckTheBackendURLAnd', "接口未返回 JSON，请检查后端地址及代理配置") : `读取分析结果失败（HTTP ${response.status}）`);

  const result = await response.json() as { status: number; message?: string; data: T };

  if (!response.ok || result.status !== 1 || result.data == null)
    throw new Error(result.message || t('ui.failedToLoadAnalysisResults', "读取分析结果失败"));

  return result.data;
}

export async function getExplainPlan(sql: string, signal?: AbortSignal): Promise<ExplainRow[]> {
  const rows = await requestPlan<ExplainRow[]>("explain_plan", sql, signal);

  if (!Array.isArray(rows) || rows.some((row) => !row || typeof row !== "object" || Array.isArray(row)))
    throw new Error(t('ui.invalidExecutionPlanTableData', "执行计划表格数据格式错误"));

  // 兼容 JDBC 原始列名和 SQLMan 转换后的 selectType、possibleKeys、keyLen。
  return rows.map((row) => Object.fromEntries(Object.entries(row).map(([key, value]) => [
    key.replace(/([a-z0-9])([A-Z])/g, "$1_$2").toUpperCase(), value,
  ])));
}

export function getExplainPlanJson(sql: string, signal?: AbortSignal): Promise<Record<string, unknown>> {
  return requestPlan("explain_plan_json", sql, signal);
}

export interface SessionStat {
  VARIABLE_NAME: string;
  VARIABLE_VALUE: string;
}

export interface ProfilingResult {
  sessionStats: SessionStat[];
  profileInfo: Record<string, string | null>[];
  profileAvailable: boolean;
  profileMessage?: string | null;
}

export async function analyzeQuery(sql: string, signal?: AbortSignal): Promise<ProfilingResult> {
  const result = await requestPlan<ProfilingResult>("analyze", sql, signal);

  if (!Array.isArray(result.sessionStats) || result.sessionStats.some((row) => typeof row?.VARIABLE_NAME !== "string" || typeof row?.VARIABLE_VALUE !== "string")
    || !Array.isArray(result.profileInfo) || result.profileInfo.some((row) => !row || typeof row !== "object" || Array.isArray(row))
    || typeof result.profileAvailable !== "boolean")
    throw new Error(t('ui.invalidProfilingData', "性能剖析数据格式错误"));

  return result;
}

export async function getSessionStats(sql: string, signal?: AbortSignal): Promise<SessionStat[]> {
  const rows = await requestPlan<SessionStat[]>("session_stats", sql, signal);

  if (!Array.isArray(rows) || rows.some((row) => typeof row?.VARIABLE_NAME !== "string" || typeof row?.VARIABLE_VALUE !== "string"))
    throw new Error(t('ui.invalidSessionStatisticsData', "会话统计数据格式错误"));

  return rows;
}
