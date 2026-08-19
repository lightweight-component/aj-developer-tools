<template>
  <UiDesigner :initial-metadata="demoMetadata" />
</template>

<script setup lang="ts">
import UiDesigner from "./components/designer/UiDesigner.vue";
import { demoMetadata } from "./demo/metadata";
</script>
