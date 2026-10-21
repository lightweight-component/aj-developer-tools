import { joinUrl, runtimeConfig } from "../config/runtime";
import type { EndpointEntity, UrlGroup } from "../types/dataservice";
import { request } from "./http";

/** Java boolean 的 Jackson 属性名与前端模型之间的兼容结构。 */
type EndpointWire = EndpointEntity & { autoSql?: boolean; autoIns?: boolean };

function fromEndpointWire(endpoint: EndpointWire): EndpointEntity {
  return {
    ...endpoint,
    isAutoSql: endpoint.isAutoSql ?? endpoint.autoSql ?? false,
    isAutoIns: endpoint.isAutoIns ?? endpoint.autoIns ?? false,
  };
}

function toEndpointWire(endpoint: EndpointEntity): EndpointWire {
  return { ...endpoint, autoSql: endpoint.isAutoSql, autoIns: endpoint.isAutoIns };
}

/**
 * 构造管理端接口地址。
 *
 * @param path 管理端相对路径。
 * @returns 完整管理端接口 URL。
 */
const admin = (path: string): string =>
  joinUrl(runtimeConfig.adminApiRoot, path);

/** 数据服务管理端与运行端的请求集合。 */
export const dataServiceApi = {
  /** @returns 从根节点或指定节点开始加载的 URL 分组树。 */
  listUrlGroups: (parentId = -1) =>
    request<UrlGroup[] | null>(admin(`ds_tree/list/${parentId}`)),
  /** @param group 待创建的 URL 分组。 */
  createUrlGroup: (group: UrlGroup) =>
    request<boolean>(admin("ds_tree/"), { method: "POST", body: group }),
  /** @param id 待更新的分组主键。@param group 更新后的分组数据。 */
  updateUrlGroup: (id: number, group: UrlGroup) =>
    request<boolean>(admin(`ds_tree/${id}`), { method: "PUT", body: group }),
  /** @param id 待删除的分组主键。@param includeChildren 是否递归删除子分组。 */
  deleteUrlGroup: (id: number, includeChildren = false) =>
    request<boolean>(admin(`ds_tree/${id}?isDelSubNode=${includeChildren}`), { method: "DELETE" }),
  /** @returns 指定 URL 分组下的所有端点。 */
  listEndpoints: (groupId: number) =>
    request<EndpointWire[] | null>(admin(`ds_endpoint/group/${groupId}`)).then((items) => (items ?? []).map(fromEndpointWire)),
  /** @returns 新建端点的主键。 */
  createEndpoint: (endpoint: EndpointEntity) =>
    request<number>(admin("ds_endpoint"), { method: "POST", body: toEndpointWire(endpoint) }),
  /** 更新指定端点。 */
  updateEndpoint: (endpoint: EndpointEntity) =>
    request<boolean>(admin(`ds_endpoint/${endpoint.id}`), { method: "PUT", body: toEndpointWire(endpoint) }),
  /** 删除指定端点。 */
  deleteEndpoint: (id: number) =>
    request<boolean>(admin(`ds_endpoint/${id}`), { method: "DELETE" }),
};
