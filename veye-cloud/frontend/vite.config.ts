import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const target = env.VITE_API_PROXY_TARGET || "http://127.0.0.1:6006";
  const devPort = Number.parseInt(env.VITE_DEV_PORT || "5173", 10);
  const devHost = env.VITE_DEV_HOST || "0.0.0.0";
  const amapKey = env.VITE_AMAP_KEY?.trim();
  const amapCode = env.VITE_AMAP_SECURITY_CODE?.trim();
  if (amapKey && amapCode) {
    console.log("[vite] 高德 JS API Key 已加载，地图将走官方 SDK");
  } else if (mode === "development") {
    console.warn("[vite] 未配置 VITE_AMAP_KEY / VITE_AMAP_SECURITY_CODE，地图将走 Leaflet 瓦片代理");
  }
  // 经 AutoDL / seetacloud 等反代访问时，Host 与 localhost 不一致，须放行（见 README）
  const allowedHostsRaw = env.VITE_ALLOWED_HOSTS?.trim();
  const allowedHosts =
    allowedHostsRaw && allowedHostsRaw !== "all"
      ? allowedHostsRaw.split(",").map((s) => s.trim()).filter(Boolean)
      : true;
  return {
    plugins: [react()],
    server: {
      host: devHost,
      port: devPort,
      strictPort: true,
      allowedHosts,
      proxy: {
        "/api": {
          target,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, "") || "/",
        },
      },
    },
    build: {
      outDir: "../app/static/web",
      emptyOutDir: true,
    },
  };
});
