import type { FormOption } from "./form-schema";

/** 将选择控件选项写为可逆的“标签=值”编辑文本。 */
export function formatFormOptions(options: FormOption[]): string {
  return options.map((option: FormOption): string => `${option.label}=${formatOptionValue(option.value)}`).join("\n");
}

/** 解析选项文本；无前缀的值保持字符串，数值和布尔值必须显式标记。 */
export function parseFormOptions(value: string): FormOption[] {
  const rows: string[] = value.split("\n").map((row: string): string => row.trim()).filter(Boolean);

  return rows.map((row: string): FormOption => {
    const index: number = row.indexOf("=");
    if (index <= 0 || index === row.length - 1)
      throw new Error("选项格式应为：标签=值");

    return {
      label: row.slice(0, index).trim(),
      value: parseOptionValue(row.slice(index + 1).trim())
    };
  });
}

function formatOptionValue(value: FormOption["value"]): string {
  if (typeof value === "number")
    return `number:${value}`;

  if (typeof value === "boolean")
    return `boolean:${value}`;

  return value;
}

function parseOptionValue(value: string): FormOption["value"] {
  if (value.startsWith("number:")) {
    const numberValue: number = Number(value.slice("number:".length));
    if (!Number.isFinite(numberValue))
      throw new Error("number: 后必须是有限数字");

    return numberValue;
  }

  if (value.startsWith("boolean:")) {
    const booleanValue: string = value.slice("boolean:".length);
    if (booleanValue !== "true" && booleanValue !== "false")
      throw new Error("boolean: 后只能是 true 或 false");

    return booleanValue === "true";
  }

  if (value.startsWith("string:"))
    return value.slice("string:".length);

  return value;
}
