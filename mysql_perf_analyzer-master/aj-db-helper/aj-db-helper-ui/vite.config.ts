import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const target = env.VITE_DEV_PROXY_TARGET;
  const databaseTarget = env.VITE_DB_PROXY_TARGET || "http://localhost:8088";

  return {
    base: "./",
    plugins: [vue()],
    server: {
      proxy: {
        "^/api_v1(?:/|$)": {
          target: databaseTarget,
          changeOrigin: true,
        },
        ...(target ? {
          "^/api(?:/|$)": {
            target,
            changeOrigin: true,
            rewrite: (path: string) => path.replace(/^\/api/, ""),
          },
        } : {}),
      },
    },
  };
});
