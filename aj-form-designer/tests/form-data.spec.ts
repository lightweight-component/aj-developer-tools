import { describe, expect, it } from "vitest";
import { synchronizeFormData } from "../src/core/form-data";
import type { FormSchema } from "../src/core/form-schema";

const schema: FormSchema = {
  version: 1,
  props: { labelPosition: "right", labelWidth: 100 },
  items: [{ id: "name", kind: "field", field: "name", label: "名称", required: false, widget: { type: "Input", props: {} } }]
};

describe("预览表单数据", (): void => {
  it("删除 schema 已不存在的字段值", (): void => {
    const formData: Record<string, unknown> = { name: "机器人", removed: "过期数据" };

    synchronizeFormData(formData, schema);

    expect(formData).toEqual({ name: "机器人" });
  });
});
