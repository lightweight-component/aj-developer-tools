<template>
  <VueDraggable v-model="canvasNodes" class="designer-canvas" :class="{ 'designer-canvas--root': root }"
    :group="dragGroup" :animation="180" :disabled="readonly" ghost-class="designer-ghost" chosen-class="designer-chosen"
    @start="emit('beforeChange')" @end="emit('changed')">
    <StageNode v-for="node in canvasNodes" :key="node.id" :node="node" :selected-id="selectedId" :readonly="readonly"
      @select="emit('select', $event)" @changed="emit('changed')" />
    <template #footer>
      <div v-if="nodes.length === 0" class="drop-placeholder">拖入组件到这里</div>
    </template>
  </VueDraggable>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { VueDraggable } from "vue-draggable-plus";
import type { DesignerNode } from "../../core/types";
import StageNode from "./StageNode.vue";

const props = defineProps<{ nodes: DesignerNode[]; selectedId?: string; root?: boolean; readonly?: boolean }>();
const emit = defineEmits<{ select: [id: string]; changed: []; beforeChange: [] }>();
/**
 * vue-draggable-plus 会通过 v-model 回传整个列表。
 * 保持原数组引用可使父级节点、属性面板和历史记录观察到同一棵响应式树。
 */
const canvasNodes = computed<DesignerNode[]>({
  get: (): DesignerNode[] => props.nodes,
  set: (value: DesignerNode[]): void => {
    props.nodes.splice(0, props.nodes.length, ...value);
    emit("changed");
  }
});

const dragGroup = { name: "ui-designer", pull: true, put: true };
</script>

<style scoped lang="less">
.designer-canvas {
  min-height: 38px;
  padding: 8px;
}

.designer-canvas--root {
  min-height: 100%;
  padding: 16px;
}

.drop-placeholder {
  border: 1px dashed #c5c8ce;
  border-radius: 4px;
  color: #808695;
  padding: 12px;
  text-align: center;
}
</style>
