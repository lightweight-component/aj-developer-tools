<template>
  <div class="form-preview">
    <DesignerCanvas ref="canvas" :schema="schema" readonly />
    <footer class="preview-actions">
      <Button @click="reset">重置</Button>
      <Button type="primary" @click="submit">校验并查看数据</Button>
    </footer>
    <Alert v-if="message" :type="isValid ? 'success' : 'error'" show-icon>{{ message }}</Alert>
    <pre v-if="isValid" class="preview-result">{{ resultText }}</pre>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import type { FormSchema } from "../../core/form-schema";
import DesignerCanvas from "./DesignerCanvas.vue";

interface PreviewCanvas {
  reset: () => void;
  validate: () => Promise<boolean>;
  getFormData: () => Record<string, unknown>;
}

defineProps<{ schema: FormSchema }>();
const canvas = ref<PreviewCanvas>();
const message = ref<string>("");
const resultText = ref<string>("");
const isValid = ref<boolean>(false);

/** 通过 View UI Plus Form 规则校验，再展示用户实际填写的数据。 */
async function submit(): Promise<void> {
  const valid: boolean = await canvas.value?.validate() ?? false;
  isValid.value = valid;
  message.value = valid ? "表单校验通过" : "请先填写所有必填字段";
  resultText.value = valid ? JSON.stringify(canvas.value?.getFormData() ?? {}, null, 2) : "";
}

function reset(): void {
  canvas.value?.reset();
  message.value = "";
  resultText.value = "";
  isValid.value = false;
}
</script>

<style scoped lang="less">
.preview-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
.preview-result { background: #f8f8f9; border-radius: 4px; margin-top: 12px; padding: 12px; }
</style>
