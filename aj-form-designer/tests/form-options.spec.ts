import { describe, expect, it } from "vitest";
import { formatFormOptions, parseFormOptions } from "../src/core/form-options";

describe("选择控件选项", (): void => {
  it("编辑文本往返后保留字符串、数值和布尔值类型", (): void => {
    const source = [
      { label: "文本", value: "001" },
      { label: "数量", value: 12 },
      { label: "启用", value: true }
    ] as const;
    const value: string = formatFormOptions([...source]);

    expect(value).toBe("文本=001\n数量=number:12\n启用=boolean:true");
    expect(parseFormOptions(value)).toEqual(source);
  });

  it("拒绝无效的类型前缀", (): void => {
    expect(() => parseFormOptions("数量=number:abc")).toThrow("有限数字");
    expect(() => parseFormOptions("启用=boolean:yes")).toThrow("true 或 false");
  });
});
