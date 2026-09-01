import type { FormField, FormItem, FormSchema } from "./form-schema";

/** 返回根字段和栅格列字段，供预览、校验和数据同步共用。 */
export function getFormFields(schema: FormSchema): FormField[] {
  return schema.items.flatMap((item: FormItem): FormField[] => item.kind === "field" ? [item] : item.columns.flatMap((column): FormField[] => column.fields));
}

/** 删除 schema 已不存在的字段值，避免预览提交过期数据。 */
export function synchronizeFormData(formData: Record<string, unknown>, schema: FormSchema): void {
  const validFields = new Set<string>(getFormFields(schema).map((field: FormField): string => field.field));

  for (const fieldName of Object.keys(formData)) {
    if (!validFields.has(fieldName))
      delete formData[fieldName];
  }
}
