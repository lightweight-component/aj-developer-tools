<template>
  <Tabs
    class="workspace-tabs"
    type="card"
    :model-value="route.path"
    :animated="false"
    :closable="tabs.length > 1"
    @on-click="selectTab"
    @on-tab-remove="closeTab"
  >
    <TabPane v-for="tab in tabs" :key="tab.path" :name="tab.path" :label="allPanels.find(panel => panel.path === tab.path)?.title || tab.title">
      <div class="panel-body">
        <RouterView :route="tab.route" />
      </div>
    </TabPane>
  </Tabs>
</template>

<script setup lang="ts">
import { shallowReactive, watch } from "vue";
import { RouterView, useRoute, useRouter, type RouteLocationNormalizedLoaded } from "vue-router";
import { allPanels } from "../router/panels";

interface WorkspaceTab {
  path: string;
  title: string;
  route: RouteLocationNormalizedLoaded;
}

const route = useRoute();
const router = useRouter();
const tabs = shallowReactive<WorkspaceTab[]>([]);

// 以路由路径去重；菜单、浏览器前进后退、直接访问均从这里同步标签。
watch(() => route.fullPath, () => {
  const panel = allPanels.find((item) => item.path === route.path);

  if (!panel)
    return;

  const snapshot = { ...route, matched: [...route.matched] };
  const index = tabs.findIndex((tab) => tab.path === route.path);

  if (index >= 0)
    tabs[index] = { ...tabs[index]!, route: snapshot };
  else
    tabs.push({ path: route.path, title: panel.title, route: snapshot });
}, { immediate: true });

function selectTab(path: string): void {
  if (path !== route.path)
    void router.push(path);
}

function closeTab(path: string): void {
  const index = tabs.findIndex((tab) => tab.path === path);

  if (index < 0 || tabs.length === 1)
    return;

  tabs.splice(index, 1);

  if (route.path === path)
    void router.replace(tabs[index]?.path ?? tabs[index - 1]!.path);
}
</script>

<style scoped lang="less">
.workspace-tabs {
  display: flex;
  height: 100%;
  flex-direction: column;
}

.workspace-tabs > :deep(.ivu-tabs-bar) {
  flex-shrink: 0;
  margin: 0;
  padding: 12px 16px 0;
  background: #fff;
}

.workspace-tabs > :deep(.ivu-tabs-content) {
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.workspace-tabs > :deep(.ivu-tabs-content > .ivu-tabs-tabpane) {
  height: 100%;
}

.panel-body {
  height: 100%;
  overflow: auto;
  padding: 28px;
}
</style>
