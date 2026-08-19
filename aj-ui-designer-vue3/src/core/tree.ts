import type { DesignerNode, WidgetDefinition } from "./types";

export function createNode(definition: WidgetDefinition): DesignerNode {
  return {
    id: crypto.randomUUID(),
    type: definition.type,
    props: structuredClone(definition.defaultProps ?? {}),
    text: definition.text,
    children: []
  };
}

/**
 * 将外部传入的设计数据转为运行时统一结构。
 * 这里是兼容旧版 JSON 的唯一入口，避免兼容判断散落在渲染组件中。
 */
export function normalizeNodes(value: unknown): DesignerNode[] {
  if (!Array.isArray(value))
    throw new Error("设计数据根节点必须是数组");

  return value.map((item: unknown): DesignerNode => normalizeNode(item));
}

/**
 * 递归清洗单个节点：补齐 id、隔离 props 引用，并转换 Vue 2 时期的类型别名。
 */
function normalizeNode(value: unknown): DesignerNode {
  if (!value || typeof value !== "object")
    throw new Error("组件节点必须是对象");

  const raw: Record<string, unknown> = value as Record<string, unknown>;
  if (typeof raw.type !== "string")
    throw new Error("组件节点缺少 type");

  const props: Record<string, unknown> = typeof raw.props === "object" && raw.props ? structuredClone(raw.props as Record<string, unknown>) : {};
  if (raw.type === "input_textarea")
    props.type = "textarea";

  if (raw.type === "input_password")
    props.type = "password";

  return {
    id: typeof raw.id === "string" ? raw.id : crypto.randomUUID(),
    type: normalizeType(raw.type),
    props,
    text: typeof raw.text === "string" ? raw.text : undefined,
    children: Array.isArray(raw.children) ? raw.children.map(normalizeNode) : []
  };
}

/**
 * 保持旧版序列化数据可导入，同时让新画布只处理统一的 View UI Plus 类型。
 */
function normalizeType(type: string): DesignerNode["type"] {
  const aliases: Record<string, DesignerNode["type"]> = {
    input_text: "Input",
    input_textarea: "Input",
    input_password: "Input",
    text: "Text",
    div: "Div",
    divider: "Divider",
    "card-container": "Card"
  };
  const normalized: DesignerNode["type"] = aliases[type] ?? type as DesignerNode["type"];
  if (type === "input_textarea")
    return normalized;

  return normalized;
}

/**
 * 深度优先查找节点；属性面板和选中状态都以稳定 id 而非对象引用定位。
 */
export function findNode(nodes: DesignerNode[], id: string): DesignerNode | undefined {
  for (const node of nodes) {
    if (node.id === id)
      return node;

    const found: DesignerNode | undefined = findNode(node.children, id);
    if (found)
      return found;
  }
}

/**
 * 从任意层级删除指定节点，并返回被删除的节点以便调用方扩展恢复或剪贴板功能。
 */
export function removeNode(nodes: DesignerNode[], id: string): DesignerNode | undefined {
  const index: number = nodes.findIndex((node: DesignerNode): boolean => node.id === id);
  if (index >= 0)
    return nodes.splice(index, 1)[0];

  for (const node of nodes) {
    const removed: DesignerNode | undefined = removeNode(node.children, id);
    if (removed)
      return removed;
  }
}
