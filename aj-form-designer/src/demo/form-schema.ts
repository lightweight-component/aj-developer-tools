import type { FormSchema } from "../core/form-schema";

export const demoFormSchema: FormSchema = {
  version: 1,
  props: { labelPosition: "right", labelWidth: 100 },
  items: [
    {
      id: "name", kind: "field", field: "name", label: "名称", required: true,
      widget: { type: "Input", props: { placeholder: "请输入名称" } }
    },
    {
      id: "status", kind: "field", field: "status", label: "状态", required: false,
      widget: { type: "Select", props: {}, options: [{ label: "启用", value: "enabled" }, { label: "停用", value: "disabled" }] }
    }
  ]
};
