/// <reference types="@amap/amap-jsapi-types" />

interface ImportMetaEnv {
  readonly VITE_AMAP_KEY?: string;
  readonly VITE_AMAP_SECURITY_CODE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
