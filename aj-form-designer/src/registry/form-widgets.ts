import type { PaletteDefinition } from "../core/form-schema";

const options = [
  { label: "选项一", value: "option_1" },
  { label: "选项二", value: "option_2" }
];

/** 仅收录有明确表单语义的组件；复杂展示组件不进入设计器。 */
export const formWidgetGroups: { name: string; widgets: PaletteDefinition[] }[] = [
  {
    name: "文本与数值",
    widgets: [
      { kind: "field", type: "Input", name: "文本框", icon: "md-create", defaultProps: { placeholder: "请输入" } },
      { kind: "field", type: "InputNumber", name: "数字框", icon: "md-calculator" },
      { kind: "field", type: "Slider", name: "滑块", icon: "md-remove" },
      { kind: "field", type: "Rate", name: "评分", icon: "md-star" }
    ]
  },
  {
    name: "选择",
    widgets: [
      { kind: "field", type: "Select", name: "下拉选择", icon: "md-arrow-dropdown", options },
      { kind: "field", type: "RadioGroup", name: "单选组", icon: "md-radio-button-on", options },
      { kind: "field", type: "CheckboxGroup", name: "多选组", icon: "md-checkbox", options },
      { kind: "field", type: "Switch", name: "开关", icon: "md-switch" }
    ]
  },
  {
    name: "时间与颜色",
    widgets: [
      { kind: "field", type: "DatePicker", name: "日期选择", icon: "md-calendar" },
      { kind: "field", type: "TimePicker", name: "时间选择", icon: "md-time" },
      { kind: "field", type: "ColorPicker", name: "颜色选择", icon: "md-color-palette" }
    ]
  },
  {
    name: "布局与操作",
    widgets: [
      { kind: "row", name: "两列栅格", icon: "md-grid" },
      { kind: "field", type: "Button", name: "提交按钮", icon: "md-checkmark", defaultProps: { type: "primary" } }
    ]
  }
];
