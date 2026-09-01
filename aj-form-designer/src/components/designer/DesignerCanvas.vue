<template>
  <Form ref="form" class="form-canvas" :model="formData" :rules="rules" v-bind="schema.props">
    <VueDraggable v-model="items" class="designer-canvas" :class="{ 'designer-canvas--readonly': readonly }"
      :group="rootGroup" :animation="180" :disabled="readonly" :on-move="canMoveToRoot"
      ghost-class="designer-ghost" chosen-class="designer-chosen" @start="emit('beforeChange')" @end="emit('changed')">
      <template v-for="item in items" :key="item.id">
        <FormFieldNode v-if="item.kind === 'field'" :field="item" :selected-id="selectedId" :readonly="readonly"
          :form-data="formData" @select="emit('select', $event)" />
        <Row v-else class="form-row" :gutter="12">
          <Col v-for="column in item.columns" :key="column.id" :span="column.span">
            <VueDraggable v-model="column.fields" class="form-column" :group="columnGroup" :animation="180"
              :disabled="readonly" :on-move="canMoveToColumn" ghost-class="designer-ghost" chosen-class="designer-chosen"
              @start="emit('beforeChange')" @end="emit('changed')">
              <FormFieldNode v-for="field in column.fields" :key="field.id" :field="field" :selected-id="selectedId"
                :readonly="readonly" :form-data="formData" @select="emit('select', $event)" />
              <template #footer>
                <div v-if="column.fields.length === 0" class="drop-placeholder">拖入字段</div>
              </template>
            </VueDraggable>
          </Col>
        </Row>
      </template>
      <template #footer>
        <div v-if="items.length === 0" class="drop-placeholder">拖入字段或两列栅格</div>
      </template>
    </VueDraggable>
  </Form>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { VueDraggable } from "vue-draggable-plus";
import { getFormFields, synchronizeFormData } from "../../core/form-data";
import type { FormField, FormItem, FormSchema } from "../../core/form-schema";
import FormFieldNode from "./FormFieldNode.vue";

interface ViewUiForm {
  resetFields: () => void;
  validate: (callback: (valid: boolean) => void) => void;
}

const props = defineProps<{ schema: FormSchema; selectedId?: string; readonly?: boolean }>();
const emit = defineEmits<{ select: [id: string]; changed: []; beforeChange: [] }>();
const form = ref<ViewUiForm>();
const formData = reactive<Record<string, unknown>>({});
watch(() => props.schema, (): void => synchronizeFormData(formData, props.schema), { deep: true, immediate: true });
const items = computed<FormItem[]>({
  get: (): FormItem[] => props.schema.items,
  set: (value: FormItem[]): void => {
    props.schema.items.splice(0, props.schema.items.length, ...value);
    emit("changed");
  }
});
const rootGroup = { name: "form-root", pull: true, put: ["form-palette", "form-root", "form-column"] };
const columnGroup = { name: "form-column", pull: true, put: ["form-palette", "form-root", "form-column"] };
const rules = computed<Record<string, { required: true; message: string; trigger: "change" | "blur" }[]>>((): Record<string, { required: true; message: string; trigger: "change" | "blur" }[]> => {
  const fields: FormField[] = getFormFields(props.schema);

  return Object.fromEntries(fields.filter((field: FormField): boolean => field.required).map((field: FormField): [string, { required: true; message: string; trigger: "change" }[]] => [field.field, [{ required: true, message: `请填写${field.label}`, trigger: "change" }]]));
});

defineExpose({ validate, reset, getFormData });

/** 根画布只接受字段或完整栅格行。 */
function canMoveToRoot(event: unknown): boolean {
  const element: unknown = getDraggedElement(event);
  return isFormItem(element);
}

/** 栅格列只接受字段，不能将行或另一列嵌套进去。 */
function canMoveToColumn(event: unknown): boolean {
  const element: unknown = getDraggedElement(event);
  return isField(element);
}

function getDraggedElement(event: unknown): unknown {
  return (event as { draggedContext?: { element?: unknown } }).draggedContext?.element;
}

function isFormItem(value: unknown): value is FormItem {
  return Boolean(value && typeof value === "object" && ((value as FormItem).kind === "field" || (value as FormItem).kind === "row"));
}

function isField(value: unknown): value is FormField {
  return Boolean(value && typeof value === "object" && (value as FormField).kind === "field");
}

/** 对预览表单进行校验，设计模式下也可被父组件安全调用。 */
function validate(): Promise<boolean> {
  return new Promise((resolve: (valid: boolean) => void): void => {
    if (!form.value) {
      resolve(false);

      return;
    }

    form.value.validate((valid: boolean): void => resolve(valid));
  });
}

function reset(): void {
  form.value?.resetFields();
}

function getFormData(): Record<string, unknown> {
  return JSON.parse(JSON.stringify(formData)) as Record<string, unknown>;
}

</script>

<style scoped lang="less">
.form-canvas { min-height: 100%; }
.designer-canvas { min-height: 100%; padding: 16px; }
.form-row { border: 1px dashed #c5c8ce; margin: 8px 0; padding: 8px; }
.form-column { min-height: 44px; padding: 4px; }
.designer-canvas--readonly { padding: 0; }
.designer-canvas--readonly .form-row { border: 0; padding: 0; }
.drop-placeholder { border: 1px dashed #c5c8ce; border-radius: 4px; color: #808695; padding: 12px; text-align: center; }
</style>
