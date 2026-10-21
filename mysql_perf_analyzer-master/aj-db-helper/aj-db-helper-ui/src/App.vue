<template>
  <main class="app-shell">
    <header class="app-header">
      <strong>DB Helper</strong>
      <span>{{ $t('ui.databasePerformanceAnalysis', '数据库性能分析') }}</span>
      <select v-model="language" class="language-select" :aria-label="$t('ui.language', '语言')">
        <option value="zh-CN">简体中文</option>
        <option value="zh-TW">正體中文</option>
        <option value="en-US">English</option>
      </select>
    </header>
    <Split v-model="split" :min="0.14" :max="0.7" class="workspace-layout">
      <template #left>
        <aside class="sidebar" :aria-label="$t('ui.navigation', '功能导航')">
          <div class="sidebar-title">{{ $t('ui.databaseAnalysis', '数据库分析') }}</div>
          <Menu :active-name="route.path" :open-names="['/top', '/realtime', '/profiling', '/performance-schema']" width="100%">
            <template v-for="panel in panels" :key="panel.path">
              <Submenu v-if="'children' in panel" :name="panel.path">
                <template #title>
                  <Icon :type="panel.icon" />
                  {{ panel.title }}
                </template>
                <MenuItem v-for="child in panel.children" :key="child.path" :name="child.path" :to="child.path" :title="child.description">
                  {{ child.title }}
                </MenuItem>
              </Submenu>
              <MenuItem v-else :name="panel.path" :to="panel.path" :title="panel.description">
                <Icon :type="panel.icon" />
                {{ panel.title }}
              </MenuItem>
            </template>
          </Menu>
        </aside>
      </template>
      <template #right>
        <section class="main-workspace" :aria-label="$t('ui.analysisPanels', '分析面板')">
          <WorkspaceTabs />
        </section>
      </template>
    </Split>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watchEffect } from "vue";
import { useRoute } from "vue-router";
import WorkspaceTabs from "./components/WorkspaceTabs.vue";
import { panels } from "./router/panels";
import { i18n, setLocale, type AppLocale } from "./i18n";

const split = ref(0.2);
const route = useRoute();
const language = computed({
  get: () => i18n.global.locale.value,
  set: (value: AppLocale) => setLocale(value),
});
watchEffect(() => { document.documentElement.lang = language.value; });
</script>

<style scoped lang="less">
.app-shell {
  display: flex;
  height: 100%;
  flex-direction: column;
  background: white;
}

.app-header {
  display: flex;
  min-height: 54px;
  align-items: center;
  gap: 16px;
  padding: 0 22px;
  border-bottom: 1px solid #e8eaec;

  strong {
    color: #2d8cf0;
    font-size: 20px;
  }

  span {
    color: #808695;
    font-size: 13px;
  }
}

.workspace-layout {
  min-height: 0;
  flex: 1;
}

.language-select {
  margin-left: auto;
  padding: 5px 8px;
  color: #515a6e;
  background: white;
  border: 1px solid #dcdee2;
  border-radius: 4px;
}

.sidebar {
  height: 100%;
  overflow: auto;
  padding-right: 3px;
}

.sidebar-title {
  padding: 22px 24px 12px;
  color: #808695;
  font-size: 12px;
  letter-spacing: 1px;
}

.main-workspace {
  height: 100%;
  min-width: 0;
  overflow: hidden;
  background: #f5f7f9;
}
</style>
