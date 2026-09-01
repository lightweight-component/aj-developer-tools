<template>
  <div class="ui-designer">
    <header class="toolbar">
      <strong>表单设计器 · Vue 3</strong>
      <Space>
        <Button :disabled="!canUndo" icon="md-undo" @click="designer.undo">撤销</Button>
        <Button :disabled="!canRedo" icon="md-redo" @click="designer.redo">重做</Button>
        <Button :disabled="!selected" icon="md-copy" @click="designer.copySelected">复制</Button>
        <Button :disabled="!selected" icon="md-trash" @click="designer.deleteSelected">删除</Button>
        <Button icon="md-cloud-upload" @click="fileInput?.click()">导入</Button>
        <Button icon="md-code" @click="isCodeOpen = true">Schema</Button>
        <Button icon="md-document" @click="isSfcOpen = true">生成 SFC</Button>
        <Button icon="md-eye" @click="isPreviewOpen = true">预览</Button>
        <Button type="primary" icon="md-download" @click="downloadSchema">导出 Schema</Button>
      </Space>
    </header>
    <main class="designer-body">
      <aside><PalettePanel /></aside>
      <section class="stage">
        <DesignerCanvas :schema="schema" :selected-id="selectedId" @select="selectedId = $event"
          @before-change="designer.beginChange" @changed="designer.finishChange" />
      </section>
      <aside><PropertyPanel :schema="schema" :selected="selected" @before-change="designer.beginChange" @changed="designer.finishChange" /></aside>
    </main>
    <Modal v-model="isCodeOpen" title="表单 Schema（JSON）" width="900" @on-ok="applySchema">
      <Codemirror v-model="schemaText" :extensions="jsonExtensions" class="code-editor" />
      <Alert v-if="schemaError" type="error" show-icon>{{ schemaError }}</Alert>
      <Alert v-if="schemaNotice" type="warning" show-icon>{{ schemaNotice }}</Alert>
    </Modal>
    <Modal v-model="isPreviewOpen" title="表单预览" fullscreen footer-hide>
      <FormPreview :schema="schema" />
    </Modal>
    <Modal v-model="isSfcOpen" title="Vue 3 单文件组件" width="900" footer-hide>
      <Input :model-value="sfcCode" type="textarea" :rows="24" readonly class="sfc-editor" />
      <footer class="sfc-actions"><Button type="primary" icon="md-download" @click="downloadSfc">下载 .vue 文件</Button></footer>
    </Modal>
    <input ref="fileInput" class="file-input" type="file" accept="application/json,.json" @change="importFile" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { Codemirror } from "vue-codemirror";
import { json } from "@codemirror/lang-json";
import { parseImportedFormSchema } from "../../core/legacy-migration";
import { generateVueSfc } from "../../core/vue-sfc-generator";
import type { FormSchema } from "../../core/form-schema";
import { useDesignerStore } from "../../stores/designer";
import DesignerCanvas from "./DesignerCanvas.vue";
import FormPreview from "./FormPreview.vue";
import PalettePanel from "./PalettePanel.vue";
import PropertyPanel from "./PropertyPanel.vue";

const props = defineProps<{ initialSchema: FormSchema }>();
const designer = useDesignerStore();
const { schema, selectedId, selected, canUndo, canRedo, isCodeOpen, isPreviewOpen } = storeToRefs(designer);
designer.initialize(props.initialSchema);
const schemaText = ref<string>("");
const schemaError = ref<string>("");
const schemaNotice = ref<string>("");
const fileInput = ref<HTMLInputElement>();
const isSfcOpen = ref<boolean>(false);
const sfcCode = computed<string>((): string => generateVueSfc(schema.value));
const jsonExtensions = [json()];

watch(isCodeOpen, (visible: boolean): void => {
  if (visible)
    schemaText.value = JSON.stringify(schema.value, null, 2);

  schemaError.value = "";
});

/** 只有完整通过表单 schema 校验的 JSON 才会替换当前设计。 */
function applySchema(): void {
  try {
    replaceImportedSchema(JSON.parse(schemaText.value));
    schemaError.value = "";
  } catch (caught: unknown) {
    schemaError.value = caught instanceof Error ? caught.message : "表单 Schema 无效";
    isCodeOpen.value = true;
  }
}

/** 从本地 JSON 文件读取新版 schema，或迁移旧版通用组件树。 */
async function importFile(event: Event): Promise<void> {
  const input: HTMLInputElement = event.target as HTMLInputElement;
  const file: File | undefined = input.files?.[0];
  if (!file)
    return;

  try {
    replaceImportedSchema(JSON.parse(await file.text()));
  } catch (caught: unknown) {
    schemaError.value = caught instanceof Error ? caught.message : "导入文件无效";
    isCodeOpen.value = true;
  } finally {
    input.value = "";
  }
}

function replaceImportedSchema(value: unknown): void {
  const imported = parseImportedFormSchema(value);
  designer.replaceSchema(imported.schema);
  schemaNotice.value = imported.migrated ? `已迁移旧版设计数据。${imported.warnings.join("；") || ""}` : "";
}

/** 导出的内容是稳定的表单 schema，可再次导入继续编辑。 */
function downloadSchema(): void {
  const file: Blob = new Blob([JSON.stringify(schema.value, null, 2)], { type: "application/json" });
  downloadFile(file, "form-schema.json");
}

/** 下载生成的 Vue SFC，宿主工程可直接以组件方式引用。 */
function downloadSfc(): void {
  const file: Blob = new Blob([sfcCode.value], { type: "text/x-vue" });
  downloadFile(file, "GeneratedForm.vue");
}

function downloadFile(file: Blob, filename: string): void {
  const url: string = URL.createObjectURL(file);
  const link: HTMLAnchorElement = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
</script>

<style scoped lang="less">
.ui-designer { background: #f5f7f9; height: 100vh; min-width: 1100px; }
.toolbar { align-items: center; background: white; box-shadow: 0 1px 5px #dcdee2; display: flex; height: 60px; justify-content: space-between; padding: 0 20px; position: relative; z-index: 3; }
.designer-body { display: grid; grid-template-columns: 250px minmax(500px, 1fr) 300px; height: calc(100vh - 60px); }
aside, .stage { background: white; overflow: auto; }
aside:first-child { border-right: 1px solid #dcdee2; }
aside:last-child { border-left: 1px solid #dcdee2; }
.stage { background: #f5f7f9; padding: 16px; }
.stage > :deep(.form-canvas) { background: white; box-shadow: 0 1px 4px #dcdee2; min-height: 100%; }
.code-editor { border: 1px solid #dcdee2; min-height: 450px; }
.file-input { display: none; }
.sfc-editor { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.sfc-actions { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
