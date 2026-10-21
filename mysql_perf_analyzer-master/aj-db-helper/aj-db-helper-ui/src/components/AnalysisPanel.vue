<template>
  <section>
    <h1>{{ panel.title }}</h1>
    <p class="description">{{ panel.description }}</p>
    <Card :bordered="false" class="panel-content">
      <div class="empty-state">
        <Icon :type="panel.icon" size="48" color="#c5c8ce" />
        <h2>{{ $t('ui.noDatabaseData', '暂无数据库数据') }}</h2>
        <p>{{ $t('ui.connectToADatabaseToViewHere', '连接数据库后，可在此查看{panel}。', { named: { panel: panel.title } }) }}</p>
      </div>
    </Card>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { allPanels } from "../router/panels";

const props = defineProps<{ path: (typeof allPanels)[number]["path"] }>();
const panel = computed(() => allPanels.find((item) => item.path === props.path)!);
</script>

<style scoped lang="less">
h1 {
  margin: 0 0 8px;
  color: #17233d;
  font-size: 24px;
  font-weight: 500;
}

.description {
  margin: 0 0 24px;
  color: #808695;
}

.panel-content {
  min-height: 320px;
}

.empty-state {
  padding: 72px 20px;
  text-align: center;

  h2 {
    margin: 16px 0 8px;
    font-size: 16px;
    font-weight: 400;
  }

  p {
    color: #808695;
  }
}
</style>
