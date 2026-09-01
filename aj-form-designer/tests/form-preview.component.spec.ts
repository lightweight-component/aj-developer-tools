// @vitest-environment jsdom
import { defineComponent, h } from "vue";
import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import FormPreview from "../src/components/designer/FormPreview.vue";
import type { FormSchema } from "../src/core/form-schema";

const schema: FormSchema = { version: 1, props: { labelPosition: "right", labelWidth: 100 }, items: [] };
const ButtonStub = defineComponent({
  emits: ["click"],
  setup(_, { emit, slots }): () => ReturnType<typeof h> {
    return (): ReturnType<typeof h> => h("button", { onClick: (): void => emit("click") }, slots.default?.());
  }
});

describe("表单预览组件", (): void => {
  it("校验通过后展示提交数据，重置时清空结果", async (): Promise<void> => {
    const validate = vi.fn<() => Promise<boolean>>().mockResolvedValue(true);
    const reset = vi.fn<() => void>();
    const CanvasStub = defineComponent({
      setup(_, { expose }): () => ReturnType<typeof h> {
        expose({ validate, reset, getFormData: (): Record<string, unknown> => ({ name: "机器人" }) });

        return (): ReturnType<typeof h> => h("div", "表单画布");
      }
    });
    const wrapper = mount(FormPreview, {
      props: { schema },
      global: { stubs: { DesignerCanvas: CanvasStub, Button: ButtonStub, Alert: { template: "<div><slot /></div>" } } }
    });

    await wrapper.get("button:last-of-type").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("表单校验通过");
    expect(wrapper.text()).toContain('"name": "机器人"');
    expect(validate).toHaveBeenCalledOnce();

    await wrapper.get("button:first-of-type").trigger("click");

    expect(wrapper.text()).not.toContain("表单校验通过");
    expect(reset).toHaveBeenCalledOnce();
  });
});
