import { install as installViewUIPlus } from "view-ui-plus";
import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { i18n } from "./i18n";
import "view-ui-plus/dist/styles/viewuiplus.css";
import "./styles/main.less";

/** Vue 3 应用实例；在挂载前集中注册 iView Plus 全局组件与服务。 */
const app = createApp(App);
app.use(i18n);
app.use({ install: installViewUIPlus }, { i18n });
app.use(router);
app.mount("#app");
