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

export function normalizeNodes(value: unknown): DesignerNode[] {
  if (!Array.isArray(value))
    throw new Error("设计数据根节点必须是数组");

  return value.map((item: unknown): DesignerNode => normalizeNode(item));
}

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

export function findNode(nodes: DesignerNode[], id: string): DesignerNode | undefined {
  for (const node of nodes) {
    if (node.id === id)
      return node;

    const found: DesignerNode | undefined = findNode(node.children, id);
    if (found)
      return found;
  }
}

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
