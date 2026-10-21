<template>
  <div ref="host" class="json-code-viewer" />
</template>

<script setup lang="ts">
import { t, i18n } from "../i18n";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { basicSetup } from "codemirror";
import { json } from "@codemirror/lang-json";
import { Compartment, EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";

const props = defineProps<{ value: string }>();
const host = ref<HTMLDivElement>();
let view: EditorView | undefined;
const languageSettings = new Compartment();
const translatedSettings = () => EditorView.contentAttributes.of({
  'aria-label': t('ui.rawJSONReadOnly', '原始 JSON（只读）'), tabindex: '0',
});

onMounted(() => {
  view = new EditorView({
    parent: host.value,
    doc: props.value,
    extensions: [
      basicSetup,
      json(),
      EditorState.readOnly.of(true),
      EditorView.editable.of(false),
      EditorView.lineWrapping,
      languageSettings.of(translatedSettings()),
      EditorView.theme({
        "&": { fontSize: "13px" },
        ".cm-scroller": { maxHeight: "420px", overflow: "auto", fontFamily: "Consolas, monospace" },
        "&.cm-focused": { outline: "none" },
      }),
    ],
  });
});

watch(() => props.value, (value) => {
  if (view && view.state.doc.toString() !== value)
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } });
});
watch(() => i18n.global.locale.value, () => {
  view?.dispatch({ effects: languageSettings.reconfigure(translatedSettings()) });
});
onBeforeUnmount(() => view?.destroy());
</script>

<style scoped>
.json-code-viewer { overflow: hidden; border: 1px solid #dcdee2; border-radius: 4px; }
</style>
