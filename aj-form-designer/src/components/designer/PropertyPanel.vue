<template>
  <section class="properties" @focusin="emit('beforeChange')" @focusout="emit('changed')">
    <template v-if="selectedField">
      <h3>{{ selectedField.widget.type }} 字段</h3>
      <Form :label-width="82">
        <FormItem label="字段名">
          <Input v-model="fieldName" @on-focus="emit('beforeChange')" @on-blur="emit('changed')" />
        </FormItem>
        <FormItem label="标签">
          <Input v-model="fieldLabel" @on-focus="emit('beforeChange')" @on-blur="emit('changed')" />
        </FormItem>
        <FormItem label="必填">
          <span class="switch-wrap" @click="emit('beforeChange')"><i-switch v-model="required" @on-change="emit('changed')" /></span>
        </FormItem>
        <template v-if="isInputField">
          <FormItem label="输入类型">
            <Select v-model="inputType" @on-focus="emit('beforeChange')" @on-change="emit('changed')">
              <Option value="text">单行文本</Option>
              <Option value="textarea">多行文本</Option>
              <Option value="password">密码</Option>
            </Select>
          </FormItem>
          <FormItem label="占位提示">
            <Input v-model="placeholder" @on-focus="emit('beforeChange')" @on-blur="emit('changed')" />
          </FormItem>
        </template>
        <template v-if="isDateField">
          <FormItem label="选择类型">
            <Select v-model="pickerType" @on-focus="emit('beforeChange')" @on-change="emit('changed')">
              <Option value="date">日期</Option>
              <Option value="daterange">日期范围</Option>
              <Option value="datetime">日期时间</Option>
            </Select>
          </FormItem>
          <FormItem label="显示格式">
            <Input v-model="dateFormat" @on-focus="emit('beforeChange')" @on-blur="emit('changed')" />
          </FormItem>
        </template>
        <template v-if="isButtonField">
          <FormItem label="点击行为">
            <Select v-model="buttonActionType" @on-change="emit('changed')">
              <Option value="none">普通按钮</Option>
              <Option value="emit">触发事件</Option>
              <Option value="code">自定义 JS</Option>
            </Select>
          </FormItem>
          <FormItem v-if="buttonActionType === 'emit'" label="事件名">
            <Input v-model="buttonEvent" placeholder="例如：save" @on-focus="emit('beforeChange')" @on-blur="emit('changed')" />
          </FormItem>
          <template v-if="buttonActionType === 'code'">
            <FormItem label="JS 代码">
              <Input v-model="buttonCode" type="textarea" :rows="8" placeholder="例如：emit('save', formData);"
                @on-focus="emit('beforeChange')" @on-blur="emit('changed')" />
            </FormItem>
            <p class="hint">生成组件中可使用 formData、emit 和 event；预览不会执行该代码。</p>
          </template>
        </template>
        <template v-if="supportsOptions">
          <FormItem label="选项">
            <span @focusout="applyOptions"><Input v-model="optionsText" type="textarea" :rows="7" placeholder="每行：标签=值"
              @on-focus="emit('beforeChange')" /></span>
          </FormItem>
          <p class="hint">每行一个选项；数字用 number:12，布尔值用 boolean:true。</p>
        </template>
        <FormItem label="高级属性">
          <span @focusout="applyProps"><Input v-model="propsText" type="textarea" :rows="6" @on-focus="emit('beforeChange')" /></span>
        </FormItem>
      </Form>
      <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    </template>
    <template v-else-if="selected?.kind === 'row'">
      <h3>两列栅格</h3>
      <p class="hint">栅格行固定包含两个等宽列；字段只能拖入列内。</p>
    </template>
    <template v-else>
      <h3>表单配置</h3>
      <Form :label-width="82">
        <FormItem label="标签位置">
          <Select v-model="labelPosition" @on-focus="emit('beforeChange')" @on-change="emit('changed')">
            <Option value="right">右侧</Option>
            <Option value="left">左侧</Option>
            <Option value="top">顶部</Option>
          </Select>
        </FormItem>
        <FormItem label="标签宽度">
          <InputNumber v-model="labelWidth" :min="40" :max="300" @on-focus="emit('beforeChange')" @on-change="emit('changed')" />
        </FormItem>
      </Form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { formatFormOptions, parseFormOptions } from "../../core/form-options";
import type { ButtonAction, FormField, FormSchema, SelectableFormNode } from "../../core/form-schema";
import { useDesignerStore } from "../../stores/designer";

const props = defineProps<{ schema: FormSchema; selected?: SelectableFormNode }>();
const emit = defineEmits<{ changed: []; beforeChange: [] }>();
const designer = useDesignerStore();
const propsText = ref<string>("");
const optionsText = ref<string>("");
const error = ref<string>("");
const selectedField = computed<FormField | undefined>((): FormField | undefined => props.selected?.kind === "field" ? props.selected : undefined);
const isInputField = computed<boolean>((): boolean => selectedField.value?.widget.type === "Input");
const isDateField = computed<boolean>((): boolean => selectedField.value?.widget.type === "DatePicker");
const isButtonField = computed<boolean>((): boolean => selectedField.value?.widget.type === "Button");
const supportsOptions = computed<boolean>((): boolean => ["Select", "RadioGroup", "CheckboxGroup"].includes(selectedField.value?.widget.type ?? ""));
const fieldName = computed<string>({ get: (): string => selectedField.value?.field ?? "", set: (value: string): void => updateField({ field: value }) });
const fieldLabel = computed<string>({ get: (): string => selectedField.value?.label ?? "", set: (value: string): void => updateField({ label: value }) });
const required = computed<boolean>({ get: (): boolean => selectedField.value?.required ?? false, set: (value: boolean): void => updateField({ required: value }) });
const inputType = computed<string>({ get: (): string => getWidgetProp("type", "text"), set: (value: string): void => setWidgetProp("type", value) });
const placeholder = computed<string>({ get: (): string => getWidgetProp("placeholder", ""), set: (value: string): void => setWidgetProp("placeholder", value) });
const pickerType = computed<string>({ get: (): string => getWidgetProp("type", "date"), set: (value: string): void => setWidgetProp("type", value) });
const dateFormat = computed<string>({ get: (): string => getWidgetProp("format", "yyyy-MM-dd"), set: (value: string): void => setWidgetProp("format", value) });
const buttonActionType = computed<ButtonAction["type"]>({ get: (): ButtonAction["type"] => getButtonAction().type, set: (value: ButtonAction["type"]): void => setButtonAction({ ...getButtonAction(), type: value, event: value === "emit" ? getButtonAction().event ?? "click" : getButtonAction().event }) });
const buttonEvent = computed<string>({ get: (): string => getButtonAction().event ?? "", set: (value: string): void => setButtonAction({ ...getButtonAction(), event: value }) });
const buttonCode = computed<string>({ get: (): string => getButtonAction().code ?? "", set: (value: string): void => setButtonAction({ ...getButtonAction(), code: value }) });
const labelPosition = computed<FormSchema["props"]["labelPosition"]>({
  get: (): FormSchema["props"]["labelPosition"] => props.schema.props.labelPosition,
  set: (value: FormSchema["props"]["labelPosition"]): void => updateFormProps({ labelPosition: value })
});
const labelWidth = computed<number>({
  get: (): number => props.schema.props.labelWidth,
  set: (value: number): void => updateFormProps({ labelWidth: value })
});

watch(selectedField, (field: FormField | undefined): void => {
  propsText.value = JSON.stringify(field?.widget.props ?? {}, null, 2);
  optionsText.value = formatFormOptions(field?.widget.options ?? []);
  error.value = "";
}, { immediate: true });

function updateField(patch: Partial<Pick<FormField, "field" | "label" | "required">>): void {
  if (!selectedField.value)
    return;

  designer.beginChange();
  const wasUpdated: boolean = designer.updateField(selectedField.value.id, {
    field: patch.field ?? selectedField.value.field,
    label: patch.label ?? selectedField.value.label,
    required: patch.required ?? selectedField.value.required
  });

  error.value = wasUpdated ? "" : "字段名已被使用，请换一个名称";
}

function getWidgetProp(name: string, defaultValue: string): string {
  const value: unknown = selectedField.value?.widget.props[name];
  return typeof value === "string" ? value : defaultValue;
}

function setWidgetProp(name: string, value: string): void {
  if (!selectedField.value)
    return;

  designer.beginChange();
  designer.updateWidgetProps(selectedField.value.id, { ...selectedField.value.widget.props, [name]: value });
}

function getButtonAction(): ButtonAction {
  return selectedField.value?.widget.action ?? { type: "none" };
}

function setButtonAction(action: ButtonAction): void {
  if (selectedField.value?.widget.type !== "Button")
    return;

  designer.beginChange();
  const wasUpdated: boolean = designer.updateButtonAction(selectedField.value.id, action);
  error.value = wasUpdated ? "" : "事件名无效，或代码中不能包含 </script";
}

function updateFormProps(patch: Partial<FormSchema["props"]>): void {
  designer.beginChange();
  designer.updateFormProps({ ...props.schema.props, ...patch });
}

/** 高级属性只接受 JSON 对象，失败时保留上一次有效值。 */
function applyProps(): void {
  if (!selectedField.value)
    return;

  try {
    const value: unknown = JSON.parse(propsText.value);
    if (!value || Array.isArray(value) || typeof value !== "object")
      throw new Error("高级属性必须是 JSON 对象");

    designer.beginChange();
    designer.updateWidgetProps(selectedField.value.id, value as Record<string, unknown>);
    error.value = "";
    emit("changed");
  } catch (caught: unknown) {
    error.value = caught instanceof Error ? caught.message : "高级属性 JSON 无效";
  }
}

/** 将“标签=值”文本转换为 Select、Radio、Checkbox 所需的选项结构。 */
function applyOptions(): void {
  if (!selectedField.value)
    return;

  try {
    const options = parseFormOptions(optionsText.value);

    designer.beginChange();
    designer.updateWidgetOptions(selectedField.value.id, options);
    error.value = "";
    emit("changed");
  } catch (caught: unknown) {
    error.value = caught instanceof Error ? caught.message : "选项格式无效";
  }
}
</script>

<style scoped lang="less">
.properties { padding: 16px; }
h3 { margin-top: 0; }
.switch-wrap { display: inline-block; }
.hint { color: #808695; font-size: 12px; margin: -10px 0 12px 82px; }
</style>
