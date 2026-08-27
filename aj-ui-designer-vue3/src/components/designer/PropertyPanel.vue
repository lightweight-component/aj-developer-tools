<template>
  <section class="properties">
    <template v-if="node">
      <h3>{{ node.type }} 属性</h3>
      <Form :label-width="65">
        <FormItem label="文本" v-if="supportsText">
          <Input v-model="node.text" @on-focus="emit('beforeChange')" @on-blur="emit('changed')" />
        </FormItem>
        <FormItem label="属性">
          <Input v-model="propsText" type="textarea" :rows="16" @on-focus="emit('beforeChange')"
            @on-blur="applyProps" />
        </FormItem>
      </Form>
      <Alert v-if="error" type="error" show-icon>{{ error }}</Alert>
    </template>
    <div v-else class="empty-state">请选择画布中的组件</div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { DesignerNode } from "../../core/types";

const props = defineProps<{ node?: DesignerNode }>();
const emit = defineEmits<{ changed: []; beforeChange: [] }>();
const propsText = ref<string>("");
const error = ref<string>("");
const supportsText = computed<boolean>((): boolean => props.node?.type === "Button" || props.node?.type === "Text");

watch(() => props.node, (node?: DesignerNode): void => { propsText.value = JSON.stringify(node?.props ?? {}, null, 2); error.value = ""; }, { immediate: true });

/**
 * 将属性编辑框中的 JSON 原子写回节点。
 * 解析失败时保留原 props，避免用户输入一半时破坏画布。
 */
function applyProps(): void {
  if (!props.node)
    return;

  try {
    const value: unknown = JSON.parse(propsText.value);
    if (!value || Array.isArray(value) || typeof value !== "object")
      throw new Error("属性必须是 JSON 对象");

    props.node.props = value as Record<string, unknown>;
    error.value = "";
    emit("changed");
  } catch (caught: unknown) {
    error.value = caught instanceof Error ? caught.message : "属性 JSON 无效";
  }
}
</script>

<style scoped lang="less">
.properties {
  padding: 16px;
}

h3 {
  margin-top: 0;
}

.empty-state {
  color: #808695;
  padding: 40px 0;
  text-align: center;
}
</style>
