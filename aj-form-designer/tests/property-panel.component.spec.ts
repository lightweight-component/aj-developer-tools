// @vitest-environment jsdom
import { defineComponent, h } from "vue";
import { createPinia, setActivePinia } from "pinia";
import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it } from "vitest";
import PropertyPanel from "../src/components/designer/PropertyPanel.vue";
import type { FormSchema } from "../src/core/form-schema";
import { useDesignerStore } from "../src/stores/designer";

const InputStub = defineComponent({
  props: { modelValue: { type: String, default: "" }, type: { type: String, default: "text" } },
  emits: ["update:modelValue", "on-focus"],
  setup(props, { emit }): () => ReturnType<typeof h> {
    return (): ReturnType<typeof h> => h(props.type === "textarea" ? "textarea" : "input", {
      value: props.modelValue,
      onInput: (event: Event): void => emit("update:modelValue", (event.target as HTMLInputElement).value)
    });
  }
});

const LayoutStub = defineComponent({ setup(_, { slots }): () => ReturnType<typeof h> { return (): ReturnType<typeof h> => h("div", slots.default?.()); } });

const schema: FormSchema = {
  version: 1,
  props: { labelPosition: "right", labelWidth: 100 },
  items: [{ id: "status", kind: "field", field: "status", label: "状态", required: false, widget: { type: "Select", props: {}, options: [{ label: "数量", value: 1 }] } }]
};

describe("属性面板组件", (): void => {
  beforeEach((): void => {
    setActivePinia(createPinia());
  });

  it("失焦后将带类型标记的选项写回 Pinia schema", async (): Promise<void> => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const store = useDesignerStore();
    store.initialize(schema);
    store.selectedId = "status";
    const wrapper = mount(PropertyPanel, {
      props: { schema: store.schema, selected: store.selected },
      global: {
        plugins: [pinia],
        stubs: {
          Input: InputStub,
          Form: LayoutStub,
          FormItem: LayoutStub,
          Select: LayoutStub,
          Option: LayoutStub,
          InputNumber: InputStub,
          "i-switch": LayoutStub,
          Alert: LayoutStub
        }
      }
    });
    const optionEditor = wrapper.find("textarea");

    await optionEditor.setValue("数量=number:12\n启用=boolean:true");
    await optionEditor.trigger("focusout");

    expect(store.schema.items[0]).toMatchObject({
      widget: { options: [{ label: "数量", value: 12 }, { label: "启用", value: true }] }
    });
  });
});
