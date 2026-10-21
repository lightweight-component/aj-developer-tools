import { t } from "../i18n";
export interface ProcessListEntry {
  ID: number;
  USER: string;
  HOST: string;
  DB: string | null;
  COMMAND: string;
  TIME: number;
  STATE: string | null;
  INFO: string | null;
}

export interface GlobalStatusEntry {
  VARIABLE_NAME: string;
  VARIABLE_VALUE: string | null;
}

export interface GlobalStatusSnapshot {
  sampledAt: number;
  uptime: number | null;
  variables: GlobalStatusEntry[];
}

/** 全局变量只在首次打开或手动刷新时请求，关键词筛选在前端完成。 */
export async function getGlobalVariables(signal?: AbortSignal): Promise<GlobalStatusEntry[]> {
  const root = (import.meta.env.VITE_DB_API_ROOT || "/api_v1").replace(/\/+$/, "");
  const response = await fetch(`${root}/realtime/global_variables`, {
    headers: { Accept: "application/json" }, signal,
  });

  if (!response.ok)
    throw new Error(`读取全局变量失败（HTTP ${response.status}）`);

  if (!response.headers.get("content-type")?.includes("application/json"))
    throw new Error(t('ui.theAPIDidNotReturnJSONCheckThatTheServiceHas', "接口未返回 JSON，请检查服务是否已更新并重启"));

  const result = await response.json() as { status: number; message?: string; data: GlobalStatusEntry[] };

  if (result.status !== 1)
    throw new Error(result.message || t('ui.failedToLoadGlobalVariables', "读取全局变量失败"));

  if (!Array.isArray(result.data))
    throw new Error(t('ui.invalidGlobalVariablesData', "全局变量数据格式错误"));

  return result.data;
}

export async function getGlobalStatus(keyword: string, signal?: AbortSignal): Promise<GlobalStatusSnapshot> {
  const root = (import.meta.env.VITE_DB_API_ROOT || "/api_v1").replace(/\/+$/, "");
  const query = new URLSearchParams({ keyword: keyword.trim() });
  const response = await fetch(`${root}/realtime/global_status?${query}`, {
    headers: { Accept: "application/json" }, signal,
  });

  if (!response.ok)
    throw new Error(`读取全局状态失败（HTTP ${response.status}）`);

  if (!response.headers.get("content-type")?.includes("application/json"))
    throw new Error(t('ui.theAPIDidNotReturnJSONCheckThatTheServiceHas', "接口未返回 JSON，请检查服务是否已更新并重启"));

  const result = await response.json() as { status: number; message?: string; data: GlobalStatusSnapshot };

  if (result.status !== 1)
    throw new Error(result.message || t('ui.failedToLoadGlobalStatus', "读取全局状态失败"));

  if (!result.data || !Array.isArray(result.data.variables) || !Number.isFinite(result.data.sampledAt))
    throw new Error(t('ui.invalidGlobalStatusSnapshot', "全局状态快照格式错误"));

  return result.data;
}

interface ProcessListResponse {
  data: ProcessListEntry[] | null;
  status: number;
  errorCode: string | number | null;
  message: string | null;
  traceId: string;
}

/** 读取 MySQL 进程列表，校验 HTTP 状态与接口业务状态。 */
export async function getProcessList(isAll = false, signal?: AbortSignal): Promise<ProcessListEntry[]> {
  const root = (import.meta.env.VITE_DB_API_ROOT || "/api_v1").replace(/\/+$/, "");
  const query = isAll ? "?isAll=true" : "";
  const response = await fetch(`${root}/realtime/process_list${query}`, {
    headers: { Accept: "application/json" },
    signal,
  });

  if (!response.ok)
    throw new Error(`读取进程列表失败（HTTP ${response.status}）`);

  if (!response.headers.get("content-type")?.includes("application/json"))
    throw new Error(t('ui.theAPIDidNotReturnJSONCheckTheDBHelperAPI', "接口未返回 JSON，请检查数据库助手的接口地址与代理配置"));

  const result = await response.json() as ProcessListResponse;

  if (result.status !== 1)
    throw new Error(result.message || t('ui.failedToLoadTheProcessList', "读取进程列表失败"));

  if (!Array.isArray(result.data))
    throw new Error(t('ui.invalidProcessListDataDataMustBeAnArray', "进程列表数据格式错误，data 应为数组"));

  return result.data;
}
