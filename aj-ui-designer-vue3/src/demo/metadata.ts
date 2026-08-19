import type { DesignerNode } from "../core/types";

export const demoMetadata: DesignerNode[] = [{
  id: "form-demo", type: "Form", props: { labelWidth: 100 }, children: [{
    id: "item-name", type: "FormItem", props: { label: "名称" }, children: [
      { id: "input-name", type: "Input", props: { placeholder: "请输入名称" }, children: [] }
    ]
  }, {
    id: "item-save", type: "FormItem", props: { label: "操作" }, children: [
      { id: "button-save", type: "Button", props: { type: "primary" }, text: "保存", children: [] }
    ]
  }]
}];
