import type { FormField, FormItem, FormOption, FormSchema } from "./form-schema";

/** 将受限 FormSchema 转换为独立可用的 Vue 3 + View UI Plus 单文件组件。 */
export function generateVueSfc(schema: FormSchema): string {
  const fields: FormField[] = getFields(schema);
  const fieldSymbols = new Map<string, string>(fields.map((field: FormField, index: number): [string, string] => [field.id, `field${index}`]));
  const template: string = schema.items.map((item: FormItem): string => generateItem(item, fieldSymbols)).join("\n");
  const fieldKeys: Record<string, string> = Object.fromEntries(fields.map((field: FormField): [string, string] => [fieldSymbols.get(field.id) as string, field.field]));
  const fieldLabels: Record<string, string> = Object.fromEntries(fields.map((field: FormField): [string, string] => [field.field, field.label]));
  const widgetProps: Record<string, Record<string, unknown>> = Object.fromEntries(fields.map((field: FormField): [string, Record<string, unknown>] => [field.field, field.widget.props]));
  const optionSets: Record<string, FormOption[]> = Object.fromEntries(fields.filter((field: FormField): boolean => Boolean(field.widget.options)).map((field: FormField): [string, FormOption[]] => [field.field, field.widget.options ?? []]));
  const rules: Record<string, { required: true; message: string; trigger: "change" }[]> = Object.fromEntries(fields.filter((field: FormField): boolean => field.required && field.widget.type !== "Button").map((field: FormField): [string, { required: true; message: string; trigger: "change" }[]] => [field.field, [{ required: true, message: `请填写${field.label}`, trigger: "change" }]]));

  return `<template>
  <Form ref="formRef" :model="formData" :rules="rules" v-bind="formProps">
${indent(template, 4)}
    <FormItem>
      <Button @click="reset">重置</Button>
      <Button type="primary" @click="submit">提交</Button>
    </FormItem>
  </Form>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";

interface ViewUiForm {
  resetFields: () => void;
  validate: (callback: (valid: boolean) => void) => void;
}

const emit = defineEmits<{ submit: [data: Record<string, unknown>] }>();
const formRef = ref<ViewUiForm>();
const formData = reactive<Record<string, unknown>>({});
const formProps = ${safeJson(schema.props)};
const fieldKeys = ${safeJson(fieldKeys)};
const fieldLabels = ${safeJson(fieldLabels)};
const widgetProps = ${safeJson(widgetProps)};
const optionSets = ${safeJson(optionSets)};
const rules = ${safeJson(rules)};

/** 校验成功后把填写结果交给父组件处理。 */
function submit(): void {
  formRef.value?.validate((valid: boolean): void => {
    if (!valid)
      return;

    emit("submit", JSON.parse(JSON.stringify(formData)) as Record<string, unknown>);
  });
}

function reset(): void {
  formRef.value?.resetFields();
}
</script>
`;
}

function generateItem(item: FormItem, fieldSymbols: Map<string, string>): string {
  if (item.kind === "field")
    return generateField(item, fieldSymbols);

  const columns: string = item.columns.map((column): string => `  <Col :span="${column.span}">
${indent(column.fields.map((field: FormField): string => generateField(field, fieldSymbols)).join("\n"), 4)}
  </Col>`).join("\n");

  return `<Row :gutter="12">
${columns}
</Row>`;
}

function generateField(field: FormField, fieldSymbols: Map<string, string>): string {
  const fieldSymbol: string = fieldSymbols.get(field.id) as string;
  const fieldKey: string = `fieldKeys.${fieldSymbol}`;
  const label: string = `fieldLabels[${fieldKey}]`;
  const model: string = `formData[${fieldKey}]`;
  const props: string = `widgetProps[${fieldKey}]`;

  if (field.widget.type === "Button")
    return `<FormItem><Button v-bind="${props}">{{ ${label} }}</Button></FormItem>`;

  const control: string = generateControl(field, model, props, fieldKey);
  return `<FormItem :label="${label}" :prop="${fieldKey}" :required="${field.required}">
  ${control}
</FormItem>`;
}

function generateControl(field: FormField, model: string, props: string, fieldKey: string): string {
  if (field.widget.type === "Select")
    return `<Select v-model="${model}" v-bind="${props}">
  <Option v-for="option in optionSets[${fieldKey}]" :key="String(option.value)" :value="option.value">{{ option.label }}</Option>
</Select>`;

  if (field.widget.type === "RadioGroup")
    return `<RadioGroup v-model="${model}" v-bind="${props}">
  <Radio v-for="option in optionSets[${fieldKey}]" :key="String(option.value)" :label="option.value">{{ option.label }}</Radio>
</RadioGroup>`;

  if (field.widget.type === "CheckboxGroup")
    return `<CheckboxGroup v-model="${model}" v-bind="${props}">
  <Checkbox v-for="option in optionSets[${fieldKey}]" :key="String(option.value)" :label="option.value">{{ option.label }}</Checkbox>
</CheckboxGroup>`;

  return `<${field.widget.type} v-model="${model}" v-bind="${props}" />`;
}

function getFields(schema: FormSchema): FormField[] {
  return schema.items.flatMap((item: FormItem): FormField[] => item.kind === "field" ? [item] : item.columns.flatMap((column): FormField[] => column.fields));
}

function safeJson(value: unknown): string {
  return JSON.stringify(value, null, 2).replace(/</g, "\\u003c").replace(/>/g, "\\u003e").replace(/&/g, "\\u0026");
}

function indent(value: string, size: number): string {
  const padding: string = " ".repeat(size);

  return value.split("\n").map((line: string): string => line ? `${padding}${line}` : line).join("\n");
}
