import { createRouter, createWebHashHistory } from "vue-router";
import { allPanels, overviewPanels, performanceSchemaPanels, profilingPanels, realtimePanels } from "./panels";
import { watchEffect } from "vue";

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", redirect: "/top" },
    {
      path: '/top',
      component: () => import('../views/TopView.vue'),
      children: [
        { path: '', redirect: '/top/health' },
        ...overviewPanels.map(panel => ({
          path: panel.path.slice('/top/'.length),
          component: () => import('../components/AnalysisPanel.vue'),
          props: { path: panel.path },
        })),
      ],
    },
    {
      path: "/realtime",
      component: () => import("../views/RealtimeView.vue"),
      children: [
        { path: "", redirect: "/realtime/processes" },
        ...realtimePanels.map((panel) => ({
          path: panel.path.slice("/realtime/".length),
          component: panel.path === "/realtime/processes"
            ? () => import("../views/realtime/ProcessListView.vue")
            : panel.path === "/realtime/global-status"
              ? () => import("../views/realtime/GlobalStatusView.vue")
            : panel.path === "/realtime/global-variables"
              ? () => import("../views/realtime/GlobalVariablesView.vue")
            : () => import("../components/AnalysisPanel.vue"),
          props: ["/realtime/processes", "/realtime/global-status", "/realtime/global-variables"].includes(panel.path) ? undefined : { path: panel.path },
        })),
      ],
    },
    { path: "/charts", component: () => import("../views/ChartsView.vue") },
    { path: "/meta", component: () => import("../views/MetaView.vue") },
    {
      path: "/profiling",
      component: () => import("../views/ProfilingView.vue"),
      children: [
        { path: "", redirect: "/profiling/explain-plan" },
        ...profilingPanels.map((panel) => ({
          path: panel.path.slice("/profiling/".length),
          component: panel.path === "/profiling/explain-plan"
            ? () => import("../views/profiling/ExplainPlanView.vue")
            : () => import("../views/profiling/SessionStatsView.vue"),
        })),
      ],
    },
    {
      path: "/performance-schema",
      component: () => import("../views/PerformanceSchemaView.vue"),
      children: [
        { path: "", redirect: "/performance-schema/setup" },
        ...performanceSchemaPanels.map((panel) => ({
          path: panel.path.slice("/performance-schema/".length),
          component: panel.path === "/performance-schema/memory"
            ? () => import("../views/MemoryView.vue")
            : panel.path === "/performance-schema/metadata-locks"
              ? () => import("../views/MetadataLocksView.vue")
            : panel.path === "/performance-schema/top-queries"
              ? () => import("../views/TopQueriesView.vue")
            : () => import("../components/AnalysisPanel.vue"),
          props: ["/performance-schema/memory", "/performance-schema/metadata-locks", "/performance-schema/top-queries"].includes(panel.path) ? undefined : { path: panel.path },
        })),
      ],
    },
    { path: "/:pathMatch(.*)*", redirect: "/top" },
  ],
});

watchEffect(() => {
  const route = router.currentRoute.value;
  const panel = allPanels.find((item) => item.path === route.path);
  if (typeof document !== "undefined")
    document.title = panel ? `${panel.title} · DB Helper` : "DB Helper";
});

export default router;
