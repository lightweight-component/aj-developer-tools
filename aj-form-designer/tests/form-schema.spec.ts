import { describe, expect, it } from "vitest";
import { createFormItem, normalizeFormSchema } from "../src/core/form-schema";

describe("表单 schema", (): void => {
  it("创建的栅格行默认带有两个等宽列", (): void => {
    const row = createFormItem({ kind: "row", name: "两列栅格", icon: "md-grid" });

    expect(row.kind).toBe("row");
    if (row.kind === "row")
      expect(row.columns.map((column) => column.span)).toEqual([12, 12]);
  });

  it("拒绝非法字段类型与重复节点 id", (): void => {
    expect(() => normalizeFormSchema({ version: 1, items: [{ id: "same", kind: "field", widget: { type: "Unknown" } }] })).toThrow("不支持的表单字段");
    expect(() => normalizeFormSchema({ version: 1, items: [
      { id: "same", kind: "field", widget: { type: "Input" } },
      { id: "same", kind: "field", widget: { type: "Input" } }
    ] })).toThrow("重复节点 id");
  });

  it("拒绝重复字段名", (): void => {
    expect(() => normalizeFormSchema({ version: 1, items: [
      { id: "name", kind: "field", field: "value", widget: { type: "Input" } },
      { id: "email", kind: "field", field: "value", widget: { type: "Input" } }
    ] })).toThrow("重复字段名");
  });

  it("拒绝不是两列或列宽总和错误的栅格", (): void => {
    expect(() => normalizeFormSchema({ version: 1, items: [{ id: "row", kind: "row", columns: [{ id: "column", span: 24, fields: [] }] }] })).toThrow("必须包含两个列");
    expect(() => normalizeFormSchema({ version: 1, items: [{ id: "row", kind: "row", columns: [{ id: "left", span: 10, fields: [] }, { id: "right", span: 10, fields: [] }] }] })).toThrow("列宽总和必须为 24");
  });

  it("只允许 Button 配置安全的动作", (): void => {
    expect(normalizeFormSchema({ version: 1, items: [{ id: "save", kind: "field", field: "save", widget: { type: "Button", action: { type: "emit", event: "save" } } }] }).items[0]).toMatchObject({ widget: { action: { type: "emit", event: "save" } } });
    expect(() => normalizeFormSchema({ version: 1, items: [{ id: "name", kind: "field", field: "name", widget: { type: "Input", action: { type: "none" } } }] })).toThrow("只有 Button");
    expect(() => normalizeFormSchema({ version: 1, items: [{ id: "save", kind: "field", field: "save", widget: { type: "Button", action: { type: "emit", event: "bad event" } } }] })).toThrow("事件名");
  });
});
