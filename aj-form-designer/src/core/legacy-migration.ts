import { normalizeFormSchema } from "./form-schema";
import type { FieldType, FormField, FormItem, FormRow, FormSchema } from "./form-schema";

interface LegacyNode {
  id?: string;
  type?: string;
  props?: Record<string, unknown>;
  text?: string;
  children?: LegacyNode[];
}

export interface ImportedFormSchema {
  schema: FormSchema;
  warnings: string[];
  migrated: boolean;
}

const supportedFieldTypes = new Set<FieldType>([
  "Input", "InputNumber", "Select", "RadioGroup", "CheckboxGroup", "DatePicker",
  "TimePicker", "Switch", "Slider", "Rate", "ColorPicker", "Button"
]);

/** 导入时优先识别新版 schema，其次转换旧版通用组件树。 */
export function parseImportedFormSchema(value: unknown): ImportedFormSchema {
  if (isNewSchema(value))
    return { schema: normalizeFormSchema(value), warnings: [], migrated: false };

  return migrateLegacyDesignerMetadata(value);
}

/**
 * 旧版 DesignerNode 只迁移可明确表达为表单的节点。
 * FormItem、Row、Col 与支持的字段会保留；其它展示或容器组件会产生警告后跳过。
 */
export function migrateLegacyDesignerMetadata(value: unknown): ImportedFormSchema {
  const roots: LegacyNode[] = readLegacyRoots(value);
  const form: LegacyNode | undefined = roots.find((node: LegacyNode): boolean => node.type === "Form");
  if (!form)
    throw new Error("导入数据既不是新版表单 schema，也不包含旧版 Form 节点");

  const warnings: string[] = [];
  const fieldNames = new Set<string>();
  const items: FormItem[] = [];
  const formProps: Record<string, unknown> = form.props ?? {};

  for (const node of form.children ?? []) {
    if (node.type === "Row") {
      const row: FormRow | undefined = migrateRow(node, fieldNames, warnings);
      if (row)
        items.push(row);

      continue;
    }

    const field: FormField | undefined = migrateFieldContainer(node, fieldNames, warnings);
    if (field)
      items.push(field);
  }

  if (items.length === 0)
    throw new Error("旧版 Form 中没有可迁移的表单字段");

  return {
    schema: normalizeFormSchema({
      version: 1,
      props: { labelPosition: formProps.labelPosition, labelWidth: formProps.labelWidth },
      items
    }),
    warnings,
    migrated: true
  };
}

function isNewSchema(value: unknown): boolean {
  return Boolean(value && typeof value === "object" && !Array.isArray(value) && (value as { version?: unknown }).version === 1);
}

function readLegacyRoots(value: unknown): LegacyNode[] {
  if (Array.isArray(value))
    return value.map(readLegacyNode);

  return [readLegacyNode(value)];
}

function readLegacyNode(value: unknown): LegacyNode {
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new Error("旧版节点必须是对象");

  const raw: Record<string, unknown> = value as Record<string, unknown>;
  return {
    id: typeof raw.id === "string" ? raw.id : undefined,
    type: typeof raw.type === "string" ? raw.type : undefined,
    props: raw.props && typeof raw.props === "object" && !Array.isArray(raw.props) ? raw.props as Record<string, unknown> : {},
    text: typeof raw.text === "string" ? raw.text : undefined,
    children: Array.isArray(raw.children) ? raw.children.map(readLegacyNode) : []
  };
}

function migrateRow(node: LegacyNode, fieldNames: Set<string>, warnings: string[]): FormRow | undefined {
  const columns: LegacyNode[] = (node.children ?? []).filter((child: LegacyNode): boolean => child.type === "Col");
  if (columns.length === 0) {
    warnings.push("已跳过一个没有 Col 子项的旧版 Row");

    return undefined;
  }

  if (columns.length > 2)
    warnings.push("旧版 Row 超过两列，已仅迁移前两列");

  const selectedColumns: LegacyNode[] = columns.slice(0, 2);
  const migratedColumns = selectedColumns.map((column: LegacyNode): { id: string; span: number; fields: FormField[] } => ({
    id: crypto.randomUUID(),
    span: 12,
    fields: (column.children ?? []).map((child: LegacyNode): FormField | undefined => migrateFieldContainer(child, fieldNames, warnings)).filter((field: FormField | undefined): field is FormField => Boolean(field))
  }));

  while (migratedColumns.length < 2)
    migratedColumns.push({ id: crypto.randomUUID(), span: 12, fields: [] });

  return { id: crypto.randomUUID(), kind: "row", columns: migratedColumns };
}

function migrateFieldContainer(node: LegacyNode, fieldNames: Set<string>, warnings: string[]): FormField | undefined {
  const widget: LegacyNode | undefined = node.type === "FormItem" ? (node.children ?? []).find((child: LegacyNode): boolean => isSupportedField(child.type)) : node;
  if (!widget || !isSupportedField(widget.type)) {
    warnings.push(`已跳过旧版组件：${node.type ?? "未命名组件"}`);

    return undefined;
  }

  const containerProps: Record<string, unknown> = node.props ?? {};
  const widgetProps: Record<string, unknown> = widget.props ?? {};
  const label: string = widget.type === "Button" ? widget.text ?? "提交" : getText(containerProps.label) ?? getText(widgetProps.placeholder) ?? widget.type;
  const suggestedFieldName: string = getText(containerProps.prop) ?? getText(widgetProps.name) ?? widget.type;

  return {
    id: crypto.randomUUID(),
    kind: "field",
    field: createFieldName(suggestedFieldName, fieldNames),
    label,
    required: containerProps.required === true,
    widget: {
      type: widget.type,
      props: structuredClone(widgetProps)
    }
  };
}

function isSupportedField(value: unknown): value is FieldType {
  return typeof value === "string" && supportedFieldTypes.has(value as FieldType);
}

function getText(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function createFieldName(value: string, fieldNames: Set<string>): string {
  const normalized: string = value.replace(/[^a-zA-Z0-9_$]+/g, "_").replace(/^_+|_+$/g, "").toLowerCase() || "field";
  let fieldName: string = normalized;
  let index: number = 2;

  while (fieldNames.has(fieldName)) {
    fieldName = `${normalized}_${index}`;
    index += 1;
  }

  fieldNames.add(fieldName);

  return fieldName;
}
