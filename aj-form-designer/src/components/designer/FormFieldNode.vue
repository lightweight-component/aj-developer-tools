<template>
  <div class="field-node" :class="{ 'field-node--selected': field.id === selectedId }" @click.stop="emit('select', field.id)">
    <FormItem :label="field.widget.type === 'Button' ? '' : field.label" :prop="field.field" :required="field.required">
      <Button v-if="field.widget.type === 'Button'" v-bind="field.widget.props">{{ field.label }}</Button>
      <Select v-else-if="field.widget.type === 'Select'" v-model="formData[field.field]" v-bind="field.widget.props">
        <Option v-for="option in field.widget.options" :key="String(option.value)" :value="option.value">{{ option.label }}</Option>
      </Select>
      <RadioGroup v-else-if="field.widget.type === 'RadioGroup'" v-model="formData[field.field]" v-bind="field.widget.props">
        <Radio v-for="option in field.widget.options" :key="String(option.value)" :label="option.value">{{ option.label }}</Radio>
      </RadioGroup>
      <CheckboxGroup v-else-if="field.widget.type === 'CheckboxGroup'" v-model="formData[field.field]" v-bind="field.widget.props">
        <Checkbox v-for="option in field.widget.options" :key="String(option.value)" :label="option.value">{{ option.label }}</Checkbox>
      </CheckboxGroup>
      <component v-else :is="field.widget.type" v-model="formData[field.field]" v-bind="field.widget.props" />
    </FormItem>
    <span v-if="!readonly" class="field-label">{{ field.widget.type }}</span>
  </div>
</template>

<script setup lang="ts">
import type { FormField } from "../../core/form-schema";

defineProps<{ field: FormField; selectedId?: string; readonly?: boolean; formData: Record<string, unknown> }>();
const emit = defineEmits<{ select: [id: string] }>();
</script>

<style scoped lang="less">
.field-node { border: 1px solid transparent; margin: 4px 0; min-height: 32px; position: relative; }
.field-node:hover { border-color: #2d8cf0; }
.field-node--selected { border-color: #19be6b; box-shadow: 0 0 0 1px #19be6b; }
.field-label { background: #2d8cf0; color: white; display: none; font-size: 10px; padding: 0 4px; position: absolute; right: 0; top: -16px; }
.field-node:hover .field-label, .field-node--selected .field-label { display: block; }
</style>
