import { createApp } from "vue";
import { createPinia } from "pinia";
import ViewUIPlus from "view-ui-plus";
import "view-ui-plus/dist/styles/viewuiplus.css";
import "./style.less";
import App from "./App.vue";

createApp(App).use(createPinia()).use(ViewUIPlus).mount("#app");
