<template>
  <div ref="host" class="query-editor" />
</template>

<script setup lang="ts">
import { t, i18n } from "../i18n";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { basicSetup } from "codemirror";
import { MySQL, sql } from "@codemirror/lang-sql";
import { EditorView, placeholder } from "@codemirror/view";
import { Compartment } from "@codemirror/state";

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const host = ref<HTMLDivElement>();
let view: EditorView | undefined;
const languageSettings = new Compartment();
const translatedSettings = () => [
  placeholder(t('ui.enterASELECTQueryEGSELECTFROMRobotWHEREId', '请输入 SELECT 查询，例如：SELECT * FROM robot WHERE id < 10')),
  EditorView.contentAttributes.of({ 'aria-label': t('ui.sqlQuery', 'SQL 查询') }),
];

onMounted(() => {
  view = new EditorView({
    parent: host.value,
    doc: props.modelValue,
    extensions: [
      basicSetup,
      sql({ dialect: MySQL }),
      EditorView.lineWrapping,
      languageSettings.of(translatedSettings()),
      EditorView.updateListener.of((update) => {
        if (update.docChanged)
          emit("update:modelValue", update.state.doc.toString());
      }),
      EditorView.theme({
        "&": { fontSize: "13px" },
        ".cm-content": { minHeight: "160px" },
        ".cm-scroller": { maxHeight: "360px", overflow: "auto", fontFamily: "Consolas, monospace" },
        "&.cm-focused": { outline: "none" },
      }),
    ],
  });
});

watch(() => props.modelValue, (value) => {
  if (view && view.state.doc.toString() !== value)
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } });
});
watch(() => i18n.global.locale.value, () => {
  view?.dispatch({ effects: languageSettings.reconfigure(translatedSettings()) });
});
onBeforeUnmount(() => view?.destroy());
</script>

<style scoped>
.query-editor { overflow: hidden; border: 1px solid #dcdee2; border-radius: 4px; }
.query-editor:focus-within { border-color: #2d8cf0; }
</style>
