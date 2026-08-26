import AMapLoader from "@amap/amap-jsapi-loader";

declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string };
  }
}

export function isAmapSdkConfigured(): boolean {
  const key = import.meta.env.VITE_AMAP_KEY?.trim();
  const code = import.meta.env.VITE_AMAP_SECURITY_CODE?.trim();
  return Boolean(key && code);
}

export function getAmapCredentials(): { key: string; securityCode: string } {
  return {
    key: import.meta.env.VITE_AMAP_KEY?.trim() ?? "",
    securityCode: import.meta.env.VITE_AMAP_SECURITY_CODE?.trim() ?? "",
  };
}

let loadPromise: Promise<typeof AMap> | null = null;

export function loadAmap(): Promise<typeof AMap> {
  if (loadPromise) return loadPromise;
  const { key, securityCode } = getAmapCredentials();
  if (!key || !securityCode) {
    return Promise.reject(new Error("amap_key_missing"));
  }
  window._AMapSecurityConfig = { securityJsCode: securityCode };
  loadPromise = AMapLoader.load({
    key,
    version: "2.0",
    plugins: ["AMap.Scale", "AMap.ToolBar", "AMap.InfoWindow"],
  }) as Promise<typeof AMap>;
  return loadPromise;
}

export function resetAmapLoaderForTests(): void {
  loadPromise = null;
}
