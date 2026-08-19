export type WidgetKind =
  | "Button" | "Input" | "Select" | "DatePicker" | "Switch" | "Slider"
  | "Form" | "FormItem" | "Row" | "Col" | "Card" | "Tabs" | "TabPane"
  | "Divider" | "Text" | "Div" | "AutoComplete" | "TimePicker" | "ColorPicker"
  | "Rate" | "Table" | "Tree" | "Carousel" | "Cascader" | "List" | "Modal";

export interface DesignerNode {
  id: string;
  type: WidgetKind;
  props: Record<string, unknown>;
  text?: string;
  children: DesignerNode[];
}

export interface WidgetDefinition {
  type: WidgetKind;
  name: string;
  icon: string;
  container?: boolean;
  defaultProps?: Record<string, unknown>;
  text?: string;
}
