<template>
  <UiDesigner :initial-schema="demoFormSchema" />
</template>

<script setup lang="ts">
import UiDesigner from "./components/designer/UiDesigner.vue";
import { demoFormSchema } from "./demo/form-schema";
</script>
