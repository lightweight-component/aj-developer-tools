<template>
  <section>
    <h1>{{ $t('performanceSchema.memory', '内存') }}</h1>
    <Tabs v-model="activeTab" :animated="false">
      <TabPane :label="$t('memory.global', '全局内存')" name="global">
        <MemoryStatsPanel scope="global" />
      </TabPane>
      <TabPane :label="$t('memory.thread', '线程内存')" name="thread">
        <MemoryStatsPanel v-if="threadOpened" scope="thread" />
      </TabPane>
    </Tabs>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import MemoryStatsPanel from '../components/MemoryStatsPanel.vue';
const activeTab = ref('global');
const threadOpened = ref(false);
watch(activeTab, tab => {
  if (tab === 'thread')
    threadOpened.value = true;
});
</script>

<style scoped>
h1 { margin-bottom: 16px; }
</style>
