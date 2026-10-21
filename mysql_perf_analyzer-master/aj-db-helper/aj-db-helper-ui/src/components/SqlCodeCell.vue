<template>
  <div ref="container" class="sql-code-cell">
    <code v-if="!ready">{{ value }}</code>
    <div ref="editorHost" />
  </div>
</template>

<script setup lang="ts">
import { t, i18n } from "../i18n";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { minimalSetup } from "codemirror";
import { MySQL, sql } from "@codemirror/lang-sql";
import { Compartment, EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";

const props = defineProps<{ value: string }>();
const container = ref<HTMLDivElement>();
const editorHost = ref<HTMLDivElement>();
const ready = ref(false);
let view: EditorView | undefined;
let observer: IntersectionObserver | undefined;
const languageSettings = new Compartment();
const translatedSettings = () => EditorView.contentAttributes.of({ 'aria-label': t('ui.sqlStatementReadOnly', 'SQL 语句（只读）') });

function mountEditor(): void {
  if (view || !editorHost.value)
    return;

  view = new EditorView({
    parent: editorHost.value,
    doc: props.value,
    extensions: [
      minimalSetup,
      sql({ dialect: MySQL }),
      EditorState.readOnly.of(true),
      EditorView.editable.of(false),
      EditorView.lineWrapping,
      languageSettings.of(translatedSettings()),
      EditorView.theme({
        "&": { backgroundColor: "transparent", fontSize: "12px" },
        ".cm-content": { padding: "0", minHeight: "0" },
        ".cm-line": { padding: "0" },
        ".cm-scroller": { fontFamily: "Consolas, monospace", overflow: "auto", maxHeight: "180px" },
        ".cm-activeLine": { backgroundColor: "transparent" },
        "&.cm-focused": { outline: "none" },
      }),
    ],
  });
  ready.value = true;
  observer?.disconnect();
}

// 仅在 SQL 单元格进入可视区域后创建编辑器，避免大量进程行同时初始化。
onMounted(() => {
  observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting))
      mountEditor();
  });
  observer.observe(container.value!);
});

watch(() => props.value, (value) => {
  if (view && view.state.doc.toString() !== value)
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } });
});
watch(() => i18n.global.locale.value, () => {
  view?.dispatch({ effects: languageSettings.reconfigure(translatedSettings()) });
});

onBeforeUnmount(() => {
  observer?.disconnect();
  view?.destroy();
});
</script>

<style scoped lang="less">
.sql-code-cell {
  min-width: 0;
  padding: 8px 0;
  text-align: left;

  code {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    font-family: Consolas, monospace;
    font-size: 12px;
  }
}
</style>
