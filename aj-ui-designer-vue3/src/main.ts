import { createApp } from "vue";
import ViewUIPlus from "view-ui-plus";
import "view-ui-plus/dist/styles/viewuiplus.css";
import "./style.less";
import App from "./App.vue";

createApp(App).use(ViewUIPlus).mount("#app");
