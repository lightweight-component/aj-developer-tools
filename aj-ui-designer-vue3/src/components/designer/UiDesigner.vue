<template>
  <div class="ui-designer">
    <header class="toolbar">
      <strong>UI Designer · Vue 3</strong>
      <Space>
        <Button :disabled="!history.canUndo.value" icon="md-undo" @click="history.undo()">撤销</Button>
        <Button :disabled="!history.canRedo.value" icon="md-redo" @click="history.redo()">重做</Button>
        <Button :disabled="!selected" icon="md-copy" @click="copySelected">复制</Button>
        <Button :disabled="!selected" icon="md-trash" @click="deleteSelected">删除</Button>
        <Button icon="md-code" @click="showCode = true">JSON</Button>
        <Button icon="md-eye" @click="showPreview = true">预览</Button>
        <Button type="primary" icon="md-download" @click="download">导出</Button>
      </Space>
    </header>
    <main class="designer-body">
      <aside>
        <PalettePanel />
      </aside>
      <section class="stage">
        <DesignerCanvas :nodes="metadata" :selected-id="selectedId" root @select="selectedId = $event"
          @before-change="history.snapshot" @changed="onChanged" />
      </section>
      <aside>
        <PropertyPanel :node="selected" @before-change="history.snapshot" @changed="onChanged" />
      </aside>
    </main>
    <Modal v-model="showCode" title="设计数据（JSON）" width="900" @on-ok="applyJson">
      <Codemirror v-model="jsonText" :extensions="jsonExtensions" class="code-editor" />
      <Alert v-if="jsonError" type="error" show-icon>{{ jsonError }}</Alert>
    </Modal>
    <Modal v-model="showPreview" title="预览" fullscreen footer-hide>
      <DesignerCanvas :nodes="metadata" root readonly />
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Codemirror } from "vue-codemirror";
import { json } from "@codemirror/lang-json";
import type { DesignerNode } from "../../core/types";
import { findNode, normalizeNodes, removeNode } from "../../core/tree";
import { useHistory } from "../../core/useHistory";
import DesignerCanvas from "./DesignerCanvas.vue";
import PalettePanel from "./PalettePanel.vue";
import PropertyPanel from "./PropertyPanel.vue";

const props = defineProps<{ initialMetadata: DesignerNode[] }>();
const history = useHistory<DesignerNode[]>(normalizeNodes(props.initialMetadata));
const metadata = computed<DesignerNode[]>((): DesignerNode[] => history.current.value);
const selectedId = ref<string>();
const selected = computed<DesignerNode | undefined>((): DesignerNode | undefined => selectedId.value ? findNode(metadata.value, selectedId.value) : undefined);
const showCode = ref<boolean>(false);
const showPreview = ref<boolean>(false);
const jsonText = ref<string>("");
const jsonError = ref<string>("");
const jsonExtensions = [json()];

watch(showCode, (visible: boolean): void => {
  if (visible)
    jsonText.value = JSON.stringify(metadata.value, null, 2);
});

/**
 * 拖拽或属性修改后清理已不存在的选中节点，避免属性面板保留失效引用。
 */
function onChanged(): void {
  selectedId.value = selectedId.value && findNode(metadata.value, selectedId.value) ? selectedId.value : undefined;
}

/**
 * 深拷贝当前选中节点及其子树；所有 id 必须重建，避免 draggable 的 key 冲突。
 */
function copySelected(): void {
  if (!selected.value)
    return;

  history.snapshot();
  const clone: DesignerNode = structuredClone(selected.value);
  assignIds(clone);
  metadata.value.push(clone);
  selectedId.value = clone.id;
}

/** 从元数据树删除当前节点，并同步取消选中状态。 */
function deleteSelected(): void {
  if (!selectedId.value)
    return;

  history.snapshot();
  removeNode(metadata.value, selectedId.value);
  selectedId.value = undefined;
}

/**
 * 校验用户编辑的 JSON，只有完整通过规范化后才会替换画布数据。
 */
function applyJson(): void {
  try {
    history.replace(normalizeNodes(JSON.parse(jsonText.value)));
    selectedId.value = undefined;
    jsonError.value = "";
  } catch (caught: unknown) {
    jsonError.value = caught instanceof Error ? caught.message : "JSON 数据无效";
    showCode.value = true;
  }
}

/** 以独立 JSON 文件下载当前设计数据，不依赖后端接口。 */
function download(): void {
  const file: Blob = new Blob([JSON.stringify(metadata.value, null, 2)], { type: "application/json" });
  const url: string = URL.createObjectURL(file);
  const link: HTMLAnchorElement = document.createElement("a");
  link.href = url;
  link.download = "ui-designer.json";
  link.click();
  URL.revokeObjectURL(url);
}

/** 递归重新分配复制子树的 id，保证整棵设计树的节点标识唯一。 */
function assignIds(node: DesignerNode): void {
  node.id = crypto.randomUUID();
  for (const child of node.children)
    assignIds(child);
}
</script>

<style scoped lang="less">
.ui-designer {
  background: #f5f7f9;
  height: 100vh;
  min-width: 1100px;
}

.toolbar {
  align-items: center;
  background: white;
  box-shadow: 0 1px 5px #dcdee2;
  display: flex;
  height: 60px;
  justify-content: space-between;
  padding: 0 20px;
  position: relative;
  z-index: 3;
}

.designer-body {
  display: grid;
  grid-template-columns: 250px minmax(500px, 1fr) 300px;
  height: calc(100vh - 60px);
}

aside,
.stage {
  background: white;
  overflow: auto;
}

aside:first-child {
  border-right: 1px solid #dcdee2;
}

aside:last-child {
  border-left: 1px solid #dcdee2;
}

.stage {
  background: #f5f7f9;
  padding: 16px;
}

.stage> :deep(.designer-canvas) {
  background: white;
  box-shadow: 0 1px 4px #dcdee2;
  min-height: 100%;
}

.code-editor {
  border: 1px solid #dcdee2;
  min-height: 450px;
}
</style>
