import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { findItemContainer, findSelectableNode, normalizeFormSchema } from "../core/form-schema";
import type { ButtonAction, FormField, FormSchema, SelectableFormNode } from "../core/form-schema";

const HISTORY_LIMIT: number = 50;

/** 管理单个表单文档及其编辑历史。 */
export const useDesignerStore = defineStore("formDesigner", () => {
  const schema = ref<FormSchema>(emptySchema());
  const selectedId = ref<string>();
  const past = ref<string[]>([]);
  const future = ref<string[]>([]);
  const pendingSnapshot = ref<string>();
  const isCodeOpen = ref<boolean>(false);
  const isPreviewOpen = ref<boolean>(false);
  const selected = computed<SelectableFormNode | undefined>((): SelectableFormNode | undefined => selectedId.value ? findSelectableNode(schema.value, selectedId.value) : undefined);
  const canUndo = computed<boolean>((): boolean => {
    const hasPendingChange: boolean = Boolean(pendingSnapshot.value && pendingSnapshot.value !== JSON.stringify(schema.value));

    return past.value.length > 0 || hasPendingChange;
  });
  const canRedo = computed<boolean>((): boolean => future.value.length > 0);

  /** 用新的受限 schema 初始化表单，不生成可撤销记录。 */
  function initialize(value: FormSchema): void {
    schema.value = normalizeFormSchema(value);
    selectedId.value = undefined;
    past.value = [];
    future.value = [];
    pendingSnapshot.value = undefined;
  }

  /**
   * 开始一次编辑事务。同一输入框的连续输入、一次拖拽仅记录一个快照。
   */
  function beginChange(): void {
    if (pendingSnapshot.value)
      return;

    pendingSnapshot.value = JSON.stringify(schema.value);
  }

  /**
   * 提交事务；若编辑前后内容没有变化，不污染撤销栈。
   */
  function commitChange(): void {
    const before: string | undefined = pendingSnapshot.value;
    pendingSnapshot.value = undefined;
    if (!before || before === JSON.stringify(schema.value))
      return;

    past.value.push(before);
    if (past.value.length > HISTORY_LIMIT)
      past.value.shift();

    future.value = [];
  }

  /** 为复制、删除、导入等立即完成的操作写入当前快照。 */
  function pushHistory(): void {
    past.value.push(JSON.stringify(schema.value));
    if (past.value.length > HISTORY_LIMIT)
      past.value.shift();

    future.value = [];
  }

  /** 拖拽或属性写入结束后保证所选节点仍在当前表单内。 */
  function finishChange(): void {
    if (selectedId.value && !findSelectableNode(schema.value, selectedId.value))
      selectedId.value = undefined;

    commitChange();
  }

  /** JSON 导入必须通过 schema 校验，避免非法树进入运行态。 */
  function replaceSchema(value: unknown): void {
    const normalized: FormSchema = normalizeFormSchema(value);
    pushHistory();
    schema.value = normalized;
    selectedId.value = undefined;
  }

  /** 复制节点到它原本所在的根列表或列列表之后。 */
  function copySelected(): void {
    if (!selectedId.value || !selected.value)
      return;

    const container = findItemContainer(schema.value, selectedId.value);
    if (!container)
      return;

    pushHistory();
    const index: number = container.findIndex((item): boolean => item.id === selectedId.value);
    const clone: SelectableFormNode = JSON.parse(JSON.stringify(selected.value)) as SelectableFormNode;
    assignIds(clone, getFieldNames(schema.value));
    container.splice(index + 1, 0, clone);
    selectedId.value = clone.id;
  }

  /** 删除字段或整行；列不能被独立删除，保证栅格结构稳定。 */
  function deleteSelected(): void {
    if (!selectedId.value)
      return;

    const container = findItemContainer(schema.value, selectedId.value);
    if (!container)
      return;

    pushHistory();
    const index: number = container.findIndex((item): boolean => item.id === selectedId.value);
    container.splice(index, 1);
    selectedId.value = undefined;
  }

  /** 字段的业务名称和标签作为表单专用属性管理。 */
  function updateField(id: string, patch: Pick<FormField, "field" | "label" | "required">): boolean {
    const node: SelectableFormNode | undefined = findSelectableNode(schema.value, id);
    if (node?.kind !== "field")
      return false;

    if (hasDuplicatedFieldName(schema.value, id, patch.field))
      return false;

    node.field = patch.field;
    node.label = patch.label;
    node.required = patch.required;

    return true;
  }

  /** 只写入当前字段的 View UI Plus props。 */
  function updateWidgetProps(id: string, props: Record<string, unknown>): void {
    const node: SelectableFormNode | undefined = findSelectableNode(schema.value, id);
    if (node?.kind !== "field")
      return;

    node.widget.props = props;
  }

  /** 写入选择控件的选项列表。 */
  function updateWidgetOptions(id: string, options: FormField["widget"]["options"]): void {
    const node: SelectableFormNode | undefined = findSelectableNode(schema.value, id);
    if (node?.kind !== "field")
      return;

    node.widget.options = options;
  }

  /** Button 动作与组件 props 分离，生成器不会把内部动作透传给 View UI Plus。 */
  function updateButtonAction(id: string, action: ButtonAction): boolean {
    const node: SelectableFormNode | undefined = findSelectableNode(schema.value, id);
    if (node?.kind !== "field" || node.widget.type !== "Button")
      return false;

    if (!isSafeButtonAction(action))
      return false;

    node.widget.action = action;

    return true;
  }

  /** 写入 Form 的标签布局配置。 */
  function updateFormProps(props: FormSchema["props"]): void {
    schema.value.props = props;
  }

  function undo(): void {
    commitChange();
    const value: string | undefined = past.value.pop();
    if (!value)
      return;

    future.value.push(JSON.stringify(schema.value));
    schema.value = normalizeFormSchema(JSON.parse(value));
    finishChange();
  }

  function redo(): void {
    commitChange();
    const value: string | undefined = future.value.pop();
    if (!value)
      return;

    past.value.push(JSON.stringify(schema.value));
    schema.value = normalizeFormSchema(JSON.parse(value));
    finishChange();
  }

  return {
    schema, selectedId, selected, canUndo, canRedo, isCodeOpen, isPreviewOpen,
    initialize, beginChange, commitChange, finishChange, replaceSchema, copySelected, deleteSelected,
    updateField, updateWidgetProps, updateWidgetOptions, updateButtonAction, updateFormProps, undo, redo
  };
});

function emptySchema(): FormSchema {
  return { version: 1, props: { labelPosition: "right", labelWidth: 100 }, items: [] };
}

/** 复制时同时生成新 id 和不冲突的表单字段名。 */
function assignIds(node: SelectableFormNode, usedFieldNames: Set<string>): void {
  node.id = crypto.randomUUID();
  if (node.kind !== "row") {
    node.field = createCopyFieldName(node.field, usedFieldNames);

    return;
  }

  for (const column of node.columns) {
    column.id = crypto.randomUUID();
    for (const field of column.fields)
      assignIds(field, usedFieldNames);
  }
}

/** 编辑字段名时排除自身，避免表单值对象出现冲突的键。 */
function hasDuplicatedFieldName(schema: FormSchema, excludedId: string, fieldName: string): boolean {
  const fields: FormField[] = getFields(schema);

  return fields.some((field: FormField): boolean => field.id !== excludedId && field.field === fieldName);
}

function getFieldNames(schema: FormSchema): Set<string> {
  return new Set<string>(getFields(schema).map((field: FormField): string => field.field));
}

function getFields(schema: FormSchema): FormField[] {
  return schema.items.flatMap((item: FormSchema["items"][number]): FormField[] => item.kind === "field" ? [item] : item.columns.flatMap((column): FormField[] => column.fields));
}

function createCopyFieldName(originalName: string, usedFieldNames: Set<string>): string {
  const baseName: string = `${originalName}_copy`;
  let copyName: string = baseName;
  let index: number = 2;

  while (usedFieldNames.has(copyName)) {
    copyName = `${baseName}${index}`;
    index += 1;
  }

  usedFieldNames.add(copyName);

  return copyName;
}

function isSafeButtonAction(action: ButtonAction): boolean {
  if (action.type === "none")
    return true;

  if (action.type === "emit" && (!action.event || !/^[a-zA-Z][a-zA-Z0-9:_-]*$/.test(action.event)))
    return false;

  return !action.code?.toLowerCase().includes("</script");
}
