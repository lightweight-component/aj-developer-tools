<template>
  <section class="palette">
    <Input v-model="keyword" clearable placeholder="搜索组件" prefix="ios-search" />
    <Collapse v-model="opened" accordion>
      <Panel v-for="group in visibleGroups" :key="group.name" :name="group.name">
        {{ group.name }}
        <template #content>
          <VueDraggable :model-value="group.widgets" :group="paletteGroup" :sort="false" :clone="createNode">
            <button v-for="widget in group.widgets" :key="widget.type" class="palette-item" type="button">
              <Icon :type="widget.icon" />{{ widget.name }}
            </button>
          </VueDraggable>
        </template>
      </Panel>
    </Collapse>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { VueDraggable } from "vue-draggable-plus";
import { createNode } from "../../core/tree";
import { widgetGroups } from "../../registry/widgets";

const keyword = ref<string>("");
const opened = ref<string>("基础");
const paletteGroup = { name: "ui-designer", pull: "clone", put: false } as const;
const visibleGroups = computed(() => widgetGroups.map((group) => ({ ...group, widgets: group.widgets.filter((widget) => widget.name.includes(keyword.value)) })).filter((group) => group.widgets.length > 0));
</script>

<style scoped lang="less">
.palette {
  padding: 12px;
}

.palette-item {
  background: #f8f8f9;
  border: 1px solid #e8eaec;
  border-radius: 4px;
  cursor: grab;
  margin: 4px;
  padding: 8px;
  text-align: left;
  width: calc(50% - 8px);
}

.palette-item:hover {
  border-color: #2d8cf0;
  color: #2d8cf0;
}

.palette-item :deep(.ivu-icon) {
  margin-right: 4px;
}
</style>
