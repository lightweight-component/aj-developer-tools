/** 与后端 `HttpMethod` 枚举保持一致的 HTTP 请求方法。 */
export type HttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "HEAD" | "OPTIONS" | "PATCH" | "TRACE" | "CONNECT";
export type ActionType = "VALUE" | "INFO" | "LIST" | "PAGE_LIST" | "CREATE" | "UPDATE" | "DELETE";

/** `ds_endpoint` 表及后端 `EndpointEntity` 对应的前端模型。 */
export interface EndpointEntity {
  id?: number;
  groupId: number;
  method: HttpMethod;
  url: string;
  sql?: string;
  name: string;
  content?: string;
  actionType: ActionType;
  isAutoSql: boolean;
  tableName?: string;
  isAutoIns: boolean;
  idField?: string;
  stat?: number;
  creator?: string;
  creatorId?: number;
  createDate?: string;
  updater?: string;
  updaterId?: number;
  updateDate?: string;
}

/** 后端通用响应信封。 */
export interface ApiResponse<T> {
  /** 业务操作是否成功。 */
  status: boolean;
  /** 业务失败时供用户展示的错误消息。 */
  message?: string;
  /** 成功响应中的实际业务数据。 */
  data: T;
  /** 可选的业务错误码。 */
  code?: number;
}

/** URL 分组树节点；根节点的 {@link parentId} 固定为 -1。 */
export interface UrlGroup {
  /** 分组主键；新建时为空。 */
  id?: number;
  /** 分组名称。 */
  name: string;
  /** 当前分组在最终端点 URL 中使用的路径片段，不包含首尾斜杠。 */
  url: string;
  /** 父分组主键，根节点为 -1。 */
  parentId: number;
  /** 可选排序号。 */
  sortNo?: number;
  /** 后端为 iView Tree 补充的展示标题。 */
  title?: string;
  /** 子分组。 */
  children?: UrlGroup[];
}

/**
 * 工作区中一个已打开服务编辑器的状态。
 */
export interface WorkspaceTab {
  /** 工作区标签稳定键。 */
  key: string;
  /** 标签页显示名称。 */
  label: string;
  /** 当前端点所属的 URL 分组。 */
  group: UrlGroup;
  /** 分组树拼接得到的完整 URL 前缀。 */
  urlPrefix: string;
  /** 当前工作区内维护的端点列表。 */
  endpoints: EndpointEntity[];
  /** 当前在端点列表中选中的端点下标。 */
  activeEndpointIndex: number;
  /** 是否是尚未提交到后端的新服务批次。 */
  isNew: boolean;
}
