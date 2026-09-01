import { describe, expect, it } from "vitest";
import { compileScript, compileTemplate, parse } from "@vue/compiler-sfc";
import { generateVueSfc } from "../src/core/vue-sfc-generator";
import type { FormSchema } from "../src/core/form-schema";

const schema: FormSchema = {
  version: 1,
  props: { labelPosition: "top", labelWidth: 120 },
  items: [
    { id: "name", kind: "field", field: "name", label: "名称", required: true, widget: { type: "Input", props: { placeholder: "请输入名称" } } },
    { id: "status", kind: "field", field: "status-code", label: "状态", required: false, widget: { type: "Select", props: {}, options: [{ label: "启用", value: "enabled" }] } },
    { id: "row", kind: "row", columns: [
      { id: "left", span: 12, fields: [{ id: "date", kind: "field", field: "date", label: "日期", required: false, widget: { type: "DatePicker", props: {} } }] },
      { id: "right", span: 12, fields: [] }
    ] }
  ]
};

describe("Vue SFC 生成器", (): void => {
  it("生成带校验、选项控件和栅格布局的 Vue 组件", (): void => {
    const code: string = generateVueSfc(schema);

    expect(code).toContain('<Form :model="formData" v-bind="formProps">');
    expect(code).toContain("formData[fieldKeys.field1]");
    expect(code).toContain("optionSets[fieldKeys.field1]");
    expect(code).toContain('<Row :gutter="12">');
    expect(code).toContain(':required="true"');
    expect(code).not.toContain('<Button @click="reset">重置</Button>');
    expect(code).not.toContain('defineEmits');
  });

  it("只输出画布中设计的 Button，不附加固定操作按钮", (): void => {
    const code: string = generateVueSfc({ ...schema, items: [{ id: "save", kind: "field", field: "save", label: "保存", required: false, widget: { type: "Button", props: { type: "primary" } } }] });

    expect(code).toContain('<FormItem><Button v-bind="widgetProps[fieldKeys.field0]">{{ fieldLabels[fieldKeys.field0] }}</Button></FormItem>');
    expect((code.match(/<Button/g) ?? [])).toHaveLength(1);
  });

  it("为事件与自定义代码 Button 生成独立点击处理函数", (): void => {
    const code: string = generateVueSfc({ ...schema, items: [
      { id: "save", kind: "field", field: "save", label: "保存", required: false, widget: { type: "Button", props: {}, action: { type: "emit", event: "save" } } },
      { id: "log", kind: "field", field: "log", label: "记录", required: false, widget: { type: "Button", props: {}, action: { type: "code", code: "console.log(formData, event);" } } }
    ] });

    expect(code).toContain('@click="onButtonField0"');
    expect(code).toContain('emit("save", JSON.parse(JSON.stringify(formData)) as Record<string, unknown>);');
    expect(code).toContain("console.log(formData, event);");

    const parsed = parse(code, { filename: "GeneratedButtonForm.vue" });
    const compiledScript = compileScript(parsed.descriptor, { id: "generated-button-form" });
    const compiledTemplate = compileTemplate({ source: parsed.descriptor.template?.content ?? "", filename: "GeneratedButtonForm.vue", id: "generated-button-form", compilerOptions: { bindingMetadata: compiledScript.bindings } });

    expect(parsed.errors).toEqual([]);
    expect(compiledTemplate.errors).toEqual([]);
  });

  it("转义用户配置中的 HTML，避免注入 script 标签", (): void => {
    const code: string = generateVueSfc({ ...schema, items: [{ id: "unsafe", kind: "field", field: "unsafe", label: "危险", required: false, widget: { type: "Input", props: { placeholder: "</script><script>alert(1)</script>" } } }] });

    expect(code).not.toContain("</script><script>alert(1)</script>");
    expect(code).toContain("\\u003c/script\\u003e");
  });

  it("生成结果可以被 Vue SFC 编译器解析", (): void => {
    const code: string = generateVueSfc(schema);
    const parsed = parse(code, { filename: "GeneratedForm.vue" });
    const descriptor = parsed.descriptor;
    const compiledScript = compileScript(descriptor, { id: "generated-form" });
    const compiledTemplate = compileTemplate({
      source: descriptor.template?.content ?? "",
      filename: "GeneratedForm.vue",
      id: "generated-form",
      compilerOptions: { bindingMetadata: compiledScript.bindings }
    });

    expect(parsed.errors).toEqual([]);
    expect(compiledTemplate.errors).toEqual([]);
  });
});
