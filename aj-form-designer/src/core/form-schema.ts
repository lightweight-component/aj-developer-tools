/** 第一轮支持的 View UI Plus 表单字段。 */
export type FieldType =
  | "Input"
  | "InputNumber"
  | "Select"
  | "RadioGroup"
  | "CheckboxGroup"
  | "DatePicker"
  | "TimePicker"
  | "Switch"
  | "Slider"
  | "Rate"
  | "ColorPicker"
  | "Button";

export interface FormOption {
  label: string;
  value: string | number | boolean;
}

export interface ButtonAction {
  type: "none" | "emit" | "code";
  event?: string;
  code?: string;
}

export interface FormField {
  id: string;
  kind: "field";
  field: string;
  label: string;
  required: boolean;
  widget: {
    type: FieldType;
    props: Record<string, unknown>;
    options?: FormOption[];
    action?: ButtonAction;
  };
}

export interface FormColumn {
  id: string;
  span: number;
  fields: FormField[];
}

export interface FormRow {
  id: string;
  kind: "row";
  columns: FormColumn[];
}

export type FormItem = FormField | FormRow;
export type SelectableFormNode = FormField | FormRow;

export interface FormSchema {
  version: 1;
  props: {
    labelPosition: "left" | "right" | "top";
    labelWidth: number;
  };
  items: FormItem[];
}

export interface FieldDefinition {
  kind: "field";
  type: FieldType;
  name: string;
  icon: string;
  defaultProps?: Record<string, unknown>;
  options?: FormOption[];
}

export interface RowDefinition {
  kind: "row";
  name: string;
  icon: string;
}

export type PaletteDefinition = FieldDefinition | RowDefinition;

/** 从字段定义创建可直接放入画布的表单项。 */
export function createFormItem(definition: PaletteDefinition): FormItem {
  if (definition.kind === "row")
    return createRow();

  const id: string = crypto.randomUUID();
  return {
    id,
    kind: "field",
    field: `${definition.type.toLowerCase()}_${id.slice(0, 8)}`,
    label: definition.name,
    required: false,
    widget: {
      type: definition.type,
      props: structuredClone(definition.defaultProps ?? {}),
      options: definition.options ? structuredClone(definition.options) : undefined,
      action: definition.type === "Button" ? { type: "none" } : undefined
    }
  };
}

/** 新增栅格行默认产生两个等宽列，字段只能拖入列内。 */
export function createRow(): FormRow {
  return {
    id: crypto.randomUUID(),
    kind: "row",
    columns: [
      { id: crypto.randomUUID(), span: 12, fields: [] },
      { id: crypto.randomUUID(), span: 12, fields: [] }
    ]
  };
}

/**
 * 校验并规范化导入的第一版表单 schema。
 * 只接受受限结构，避免把旧通用组件树静默当成表单数据处理。
 */
export function normalizeFormSchema(value: unknown): FormSchema {
  if (!value || typeof value !== "object")
    throw new Error("表单数据必须是对象");

  const raw: Record<string, unknown> = value as Record<string, unknown>;
  if (raw.version !== 1 || !Array.isArray(raw.items))
    throw new Error("仅支持 version 为 1 的表单 schema");

  const props: Record<string, unknown> = raw.props && typeof raw.props === "object" ? raw.props as Record<string, unknown> : {};
  const usedIds = new Set<string>();
  const schema: FormSchema = {
    version: 1,
    props: {
      labelPosition: normalizeLabelPosition(props.labelPosition),
      labelWidth: typeof props.labelWidth === "number" ? props.labelWidth : 100
    },
    items: raw.items.map((item: unknown): FormItem => normalizeItem(item, usedIds))
  };

  assertUniqueFieldNames(schema);

  return schema;
}

/** 根据 id 找可选节点；列只作为布局容器，不单独暴露给属性面板。 */
export function findSelectableNode(schema: FormSchema, id: string): SelectableFormNode | undefined {
  for (const item of schema.items) {
    if (item.id === id)
      return item;

    if (item.kind === "row") {
      for (const column of item.columns) {
        const field: FormField | undefined = column.fields.find((value: FormField): boolean => value.id === id);
        if (field)
          return field;
      }
    }
  }
}

/** 返回节点所在数组，让复制和删除保留原有布局位置。 */
export function findItemContainer(schema: FormSchema, id: string): FormItem[] | FormField[] | undefined {
  if (schema.items.some((item: FormItem): boolean => item.id === id))
    return schema.items;

  for (const item of schema.items) {
    if (item.kind !== "row")
      continue;

    for (const column of item.columns) {
      if (column.fields.some((field: FormField): boolean => field.id === id))
        return column.fields;
    }
  }
}

function normalizeItem(value: unknown, usedIds: Set<string>): FormItem {
  const raw: Record<string, unknown> = readObject(value, "表单项必须是对象");
  if (raw.kind === "field")
    return normalizeField(raw, usedIds);

  if (raw.kind !== "row" || !Array.isArray(raw.columns))
    throw new Error("表单项必须是字段或栅格行");

  if (raw.columns.length !== 2)
    throw new Error("两列栅格必须包含两个列");

  const columns: FormColumn[] = raw.columns.map((column: unknown): FormColumn => normalizeColumn(column, usedIds));
  if (columns[0].span + columns[1].span !== 24)
    throw new Error("两列栅格的列宽总和必须为 24");

  return {
    id: normalizeId(raw.id, usedIds),
    kind: "row",
    columns
  };
}

function normalizeColumn(value: unknown, usedIds: Set<string>): FormColumn {
  const raw: Record<string, unknown> = readObject(value, "栅格列必须是对象");
  if (!Array.isArray(raw.fields))
    throw new Error("栅格列缺少 fields 数组");

  return {
    id: normalizeId(raw.id, usedIds),
    span: typeof raw.span === "number" && raw.span > 0 && raw.span <= 24 ? raw.span : 12,
    fields: raw.fields.map((field: unknown): FormField => normalizeField(readObject(field, "字段必须是对象"), usedIds))
  };
}

function normalizeField(raw: Record<string, unknown>, usedIds: Set<string>): FormField {
  const widget: Record<string, unknown> = readObject(raw.widget, "字段缺少 widget 配置");
  if (!isFieldType(widget.type))
    throw new Error(`不支持的表单字段：${String(widget.type)}`);

  const widgetProps: Record<string, unknown> = widget.props && typeof widget.props === "object" ? structuredClone(widget.props as Record<string, unknown>) : {};
  const action: ButtonAction | undefined = normalizeButtonAction(widget.action, widget.type);
  return {
    id: normalizeId(raw.id, usedIds),
    kind: "field",
    field: typeof raw.field === "string" && raw.field ? raw.field : `field_${crypto.randomUUID().slice(0, 8)}`,
    label: typeof raw.label === "string" ? raw.label : "未命名字段",
    required: raw.required === true,
    widget: {
      type: widget.type,
      props: widgetProps,
      options: Array.isArray(widget.options) ? structuredClone(widget.options as FormOption[]) : undefined,
      action
    }
  };
}

function normalizeId(value: unknown, usedIds: Set<string>): string {
  const id: string = typeof value === "string" && value ? value : crypto.randomUUID();
  if (usedIds.has(id))
    throw new Error(`存在重复节点 id：${id}`);

  usedIds.add(id);
  return id;
}

function normalizeLabelPosition(value: unknown): FormSchema["props"]["labelPosition"] {
  if (value === "left" || value === "right" || value === "top")
    return value;

  return "right";
}

function readObject(value: unknown, errorMessage: string): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new Error(errorMessage);

  return value as Record<string, unknown>;
}

function isFieldType(value: unknown): value is FieldType {
  return typeof value === "string" && ["Input", "InputNumber", "Select", "RadioGroup", "CheckboxGroup", "DatePicker", "TimePicker", "Switch", "Slider", "Rate", "ColorPicker", "Button"].includes(value);
}

function normalizeButtonAction(value: unknown, fieldType: FieldType): ButtonAction | undefined {
  if (value === undefined)
    return fieldType === "Button" ? { type: "none" } : undefined;

  if (fieldType !== "Button")
    throw new Error("只有 Button 字段可以配置动作");

  const raw: Record<string, unknown> = readObject(value, "Button 动作必须是对象");
  if (raw.type !== "none" && raw.type !== "emit" && raw.type !== "code")
    throw new Error("Button 动作类型无效");

  const event: string | undefined = typeof raw.event === "string" && raw.event.trim() ? raw.event.trim() : undefined;
  if (raw.type === "emit" && !event)
    throw new Error("事件动作必须填写事件名");

  if (event && !/^[a-zA-Z][a-zA-Z0-9:_-]*$/.test(event))
    throw new Error("事件名只能包含字母、数字、冒号、下划线和连字符");

  const code: string | undefined = typeof raw.code === "string" ? raw.code : undefined;
  if (code?.toLowerCase().includes("</script"))
    throw new Error("自定义代码不能包含 </script");

  return { type: raw.type, event, code };
}

/** 表单提交时字段名是对象键，导入数据不能包含重复名称。 */
function assertUniqueFieldNames(schema: FormSchema): void {
  const usedNames = new Set<string>();
  const fields: FormField[] = schema.items.flatMap((item: FormItem): FormField[] => item.kind === "field" ? [item] : item.columns.flatMap((column: FormColumn): FormField[] => column.fields));

  for (const field of fields) {
    if (usedNames.has(field.field))
      throw new Error(`存在重复字段名：${field.field}`);

    usedNames.add(field.field);
  }
}
