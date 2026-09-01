import { describe, expect, it } from "vitest";
import { migrateLegacyDesignerMetadata, parseImportedFormSchema } from "../src/core/legacy-migration";

describe("旧版设计数据迁移", (): void => {
  it("迁移 FormItem 中的 Input 和 Button", (): void => {
    const result = migrateLegacyDesignerMetadata([{
      id: "form", type: "Form", props: { labelWidth: 120 }, children: [
        { id: "name-item", type: "FormItem", props: { label: "名称", prop: "name", required: true }, children: [{ id: "name-input", type: "Input", props: { placeholder: "请输入名称" }, children: [] }] },
        { id: "save-item", type: "FormItem", props: { label: "操作" }, children: [{ id: "save-button", type: "Button", props: { type: "primary" }, text: "保存", children: [] }] }
      ]
    }]);

    expect(result.migrated).toBe(true);
    expect(result.schema.props.labelWidth).toBe(120);
    expect(result.schema.items).toMatchObject([
      { kind: "field", field: "name", label: "名称", required: true, widget: { type: "Input" } },
      { kind: "field", field: "button", label: "保存", widget: { type: "Button" } }
    ]);
  });

  it("保留新版 schema，不再重复迁移", (): void => {
    const result = parseImportedFormSchema({ version: 1, props: {}, items: [] });

    expect(result.migrated).toBe(false);
    expect(result.schema.items).toEqual([]);
  });

  it("旧版 Row 只迁移前两列并给出提示", (): void => {
    const result = migrateLegacyDesignerMetadata([{
      type: "Form", children: [{
        type: "Row", children: [
          { type: "Col", children: [{ type: "FormItem", props: { label: "甲" }, children: [{ type: "Input", children: [] }] }] },
          { type: "Col", children: [{ type: "FormItem", props: { label: "乙" }, children: [{ type: "Input", children: [] }] }] },
          { type: "Col", children: [{ type: "FormItem", props: { label: "丙" }, children: [{ type: "Input", children: [] }] }] }
        ]
      }]
    }]);
    const row = result.schema.items[0];

    expect(row.kind).toBe("row");
    if (row.kind === "row")
      expect(row.columns).toHaveLength(2);

    expect(result.warnings).toContain("旧版 Row 超过两列，已仅迁移前两列");
  });
});
