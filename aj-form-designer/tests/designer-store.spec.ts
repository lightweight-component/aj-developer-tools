import { beforeEach, describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useDesignerStore } from "../src/stores/designer";
import type { FormSchema } from "../src/core/form-schema";

const schema: FormSchema = {
  version: 1,
  props: { labelPosition: "right", labelWidth: 100 },
  items: [{ id: "name", kind: "field", field: "name", label: "名称", required: false, widget: { type: "Input", props: {} } }]
};

describe("表单设计器状态仓库", (): void => {
  beforeEach((): void => {
    setActivePinia(createPinia());
  });

  it("字段配置能够撤销和重做", (): void => {
    const store = useDesignerStore();
    store.initialize(schema);
    store.beginChange();
    store.updateField("name", { field: "displayName", label: "显示名称", required: true });
    store.commitChange();
    store.undo();

    expect(store.schema.items[0]).toMatchObject({ field: "name", label: "名称", required: false });
    store.redo();
    expect(store.schema.items[0]).toMatchObject({ field: "displayName", label: "显示名称", required: true });
  });

  it("连续字段编辑只产生一个撤销步骤", (): void => {
    const store = useDesignerStore();
    store.initialize(schema);
    store.beginChange();
    store.updateField("name", { field: "displayName", label: "名称", required: false });
    store.updateField("name", { field: "displayName", label: "显示名称", required: true });
    store.commitChange();
    store.undo();

    expect(store.schema.items[0]).toMatchObject({ field: "name", label: "名称", required: false });
    expect(store.canUndo).toBe(false);
  });

  it("拒绝把字段名改为已有名称", (): void => {
    const store = useDesignerStore();
    store.initialize({ ...schema, items: [...schema.items, { id: "email", kind: "field", field: "email", label: "邮箱", required: false, widget: { type: "Input", props: {} } }] });

    expect(store.updateField("email", { field: "name", label: "邮箱", required: false })).toBe(false);
    expect(store.schema.items[1]).toMatchObject({ field: "email" });
  });

  it("复制字段保留所在容器并生成新 id", (): void => {
    const store = useDesignerStore();
    store.initialize(schema);
    store.selectedId = "name";
    store.copySelected();

    expect(store.schema.items).toHaveLength(2);
    expect(store.schema.items[1].id).not.toBe("name");
    expect(store.schema.items[1]).toMatchObject({ field: "name_copy" });
  });

  it("只允许 Button 写入安全的动作", (): void => {
    const store = useDesignerStore();
    store.initialize({ ...schema, items: [{ id: "save", kind: "field", field: "save", label: "保存", required: false, widget: { type: "Button", props: {} } }] });

    expect(store.updateButtonAction("save", { type: "emit", event: "save" })).toBe(true);
    expect(store.updateButtonAction("save", { type: "emit", event: "bad event" })).toBe(false);
    expect(store.updateButtonAction("save", { type: "code", code: "</script" })).toBe(false);
  });
});
