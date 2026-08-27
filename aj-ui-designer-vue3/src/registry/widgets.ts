import type { WidgetDefinition } from "../core/types";

export const widgetGroups: { name: string; widgets: WidgetDefinition[] }[] = [
  { name: "基础", widgets: [
    { type: "Text", name: "文字", icon: "md-text", text: "请替换文字" },
    { type: "Button", name: "按钮", icon: "md-radio-button-on", text: "按钮", defaultProps: { type: "primary" } },
    { type: "Divider", name: "分割线", icon: "md-remove" }
  ] },
  { name: "表单", widgets: [
    { type: "Input", name: "文本框", icon: "md-create", defaultProps: { placeholder: "请输入" } },
    { type: "Select", name: "下拉选择", icon: "md-arrow-dropdown" },
    { type: "DatePicker", name: "日期选择", icon: "md-calendar" },
    { type: "TimePicker", name: "时间选择", icon: "md-time" },
    { type: "Switch", name: "开关", icon: "md-switch" },
    { type: "Slider", name: "滑块", icon: "md-remove" },
    { type: "ColorPicker", name: "颜色选择", icon: "md-color-palette" },
    { type: "Rate", name: "评分", icon: "md-star" },
    { type: "AutoComplete", name: "自动完成", icon: "md-arrow-dropdown" }
  ] },
  { name: "布局", widgets: [
    { type: "Form", name: "表单", icon: "md-list-box", container: true, defaultProps: { labelWidth: 120 } },
    { type: "FormItem", name: "表单项", icon: "md-list", container: true, defaultProps: { label: "未命名" } },
    { type: "Row", name: "栅格行", icon: "md-grid", container: true },
    { type: "Col", name: "栅格列", icon: "md-square", container: true, defaultProps: { span: 12 } },
    { type: "Card", name: "卡片", icon: "md-albums", container: true, defaultProps: { title: "卡片标题" } },
    { type: "Tabs", name: "标签页", icon: "md-folder", container: true },
    { type: "TabPane", name: "标签项", icon: "md-document", container: true, defaultProps: { label: "标签页" } },
    { type: "Div", name: "Div 容器", icon: "md-square-outline", container: true }
  ] },
  { name: "高级", widgets: [
    { type: "Table", name: "表格", icon: "md-grid" },
    { type: "Tree", name: "树", icon: "md-git-branch" },
    { type: "Carousel", name: "走马灯", icon: "md-images" },
    { type: "Cascader", name: "级联选择", icon: "md-list" },
    { type: "List", name: "列表", icon: "md-list-box" },
    { type: "Modal", name: "对话框", icon: "md-browsers" }
  ] }
];

export const containerTypes: Set<string> = new Set(["Form", "FormItem", "Row", "Col", "Card", "Tabs", "TabPane", "Div"]);
