<template>
  <div class="stage-node" :class="{ 'stage-node--selected': node.id === selectedId }"
    @click.stop="emit('select', node.id)">
    <component :is="tag" v-bind="node.props" class="stage-component">
      <template v-if="node.type === 'Button' || node.type === 'Text'">{{ node.text }}</template>
      <DesignerCanvas v-else-if="isContainer" :nodes="node.children" :selected-id="selectedId" :readonly="readonly"
        @select="emit('select', $event)" @changed="emit('changed')" @before-change="emit('beforeChange')" />
    </component>
    <span class="node-label">{{ node.type }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Component } from "vue";
import type { DesignerNode } from "../../core/types";
import { containerTypes } from "../../registry/widgets";
import DesignerCanvas from "./DesignerCanvas.vue";

const props = defineProps<{ node: DesignerNode; selectedId?: string; readonly?: boolean }>();
const emit = defineEmits<{ select: [id: string]; changed: []; beforeChange: [] }>();
const tag = computed<string | Component>((): string | Component => props.node.type === "Text" ? "span" : props.node.type === "Div" ? "div" : props.node.type);
const isContainer = computed<boolean>((): boolean => containerTypes.has(props.node.type));
</script>

<style scoped lang="less">
.stage-node {
  border: 1px solid transparent;
  margin: 4px 0;
  min-height: 28px;
  position: relative;
}

.stage-node:hover {
  border-color: #2d8cf0;
}

.stage-node--selected {
  border-color: #19be6b;
  box-shadow: 0 0 0 1px #19be6b;
}

.stage-component {
  min-height: 28px;
}

.node-label {
  background: #2d8cf0;
  color: white;
  display: none;
  font-size: 10px;
  line-height: 16px;
  padding: 0 4px;
  position: absolute;
  right: 0;
  top: -16px;
  z-index: 2;
}

.stage-node:hover>.node-label,
.stage-node--selected>.node-label {
  display: block;
}
</style>
