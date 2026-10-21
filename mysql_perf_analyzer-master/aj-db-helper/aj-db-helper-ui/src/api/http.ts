import { t } from "../i18n";
import type { ApiResponse } from "../types/dataservice";

interface RequestOptions extends Omit<RequestInit, "body"> {
  /** 由客户端序列化为 JSON 的请求体。 */
  body?: unknown;
}

/**
 * 调用数据服务后端，并统一处理 HTTP、JSON 与业务状态错误。
 *
 * @typeParam T 成功响应中 `data` 字段的类型。
 * @param url 要请求的完整接口地址。
 * @param options Fetch 请求选项；`body` 会自动 JSON 序列化。
 * @returns 服务端响应中的业务数据。
 * @throws 当网络、HTTP 状态、响应格式或业务状态异常时抛出错误。
 */
export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers, ...init } = options;
  const response = await fetch(url, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
    ...init,
  });

  if (!response.ok)
    throw new Error(`请求失败（${response.status}）`);

  const contentType = response.headers.get("content-type") ?? "";

  if (!contentType.includes("application/json")) {
    throw new Error(
      t('ui.theAPIDidNotReturnJSONCheckVITEDSAPIROOT', "接口未返回 JSON；请检查 VITE_DS_API_ROOT 或 VITE_DEV_PROXY_TARGET 配置"),
    );
  }

  const result = (await response.json()) as ApiResponse<T>;

  if (!result.status)
    throw new Error(result.message || t('ui.theServerCouldNotCompleteTheRequest', "服务端未完成请求"));

  return result.data;
}
