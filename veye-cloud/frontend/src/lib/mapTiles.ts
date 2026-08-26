import type { MapBaseLayer } from "./types";

export type MapTileSpec = {
  label: string;
  provider: string;
  maxNativeZoom: number;
  maxZoom: number;
  attribution: string;
};

/** 经 FastAPI 同源代理，避免浏览器直连 OSM/OpenTopo 失败导致灰屏 */
export function mapTileUrl(provider: string): string {
  const prefix = import.meta.env.DEV ? "/api/map/tiles" : "/map/tiles";
  return `${prefix}/${provider}/{z}/{x}/{y}.png`;
}

export function isAmapLayer(layer: MapBaseLayer): boolean {
  return layer === "amap" || layer === "amap_satellite";
}

export const MAP_TILE_LAYERS: Record<MapBaseLayer, MapTileSpec> = {
  amap: {
    label: "高德",
    provider: "amap",
    maxNativeZoom: 18,
    maxZoom: 22,
    attribution: "&copy; 高德地图 · via V-Eye",
  },
  amap_satellite: {
    label: "高德卫星",
    provider: "amap_satellite",
    maxNativeZoom: 18,
    maxZoom: 22,
    attribution: "&copy; 高德地图 · via V-Eye",
  },
  street: {
    label: "街道",
    provider: "street",
    maxNativeZoom: 19,
    maxZoom: 22,
    attribution: "Tiles &copy; Esri · via V-Eye",
  },
  topo: {
    label: "地形",
    provider: "topo",
    maxNativeZoom: 17,
    maxZoom: 22,
    attribution: "&copy; OSM · OpenTopoMap · via V-Eye",
  },
  satellite: {
    label: "卫星",
    provider: "satellite",
    maxNativeZoom: 19,
    maxZoom: 22,
    attribution: "Tiles &copy; Esri · via V-Eye",
  },
  osm: {
    label: "OSM",
    provider: "osm",
    maxNativeZoom: 19,
    maxZoom: 22,
    attribution: "&copy; OpenStreetMap · via V-Eye",
  },
};

/** 工具栏底图 chip 显示顺序 */
export const MAP_LAYER_CHIP_ORDER: MapBaseLayer[] = [
  "street",
  "satellite",
  "amap",
  "amap_satellite",
  "osm",
  "topo",
];

/** 瓦片加载失败时的回退顺序（Esri 优先，速度最稳） */
export const MAP_LAYER_FALLBACK_ORDER: MapBaseLayer[] = [
  "street",
  "satellite",
  "amap",
  "amap_satellite",
  "osm",
  "topo",
];

/** 累计 N 个瓦片失败时提示（不切换底图） */
export const MAP_TILE_ERROR_WARN = 4;
/** 累计 N 个瓦片失败时触发底图回退 */
export const MAP_TILE_ERROR_FALLBACK = 10;

/** 高德 JS API 模式底图选项 */
export const MAP_SDK_LAYER_ORDER: MapBaseLayer[] = ["amap", "amap_satellite"];

export const MAP_SDK_LAYER_LABELS: Record<"amap" | "amap_satellite", string> = {
  amap: "高德标准",
  amap_satellite: "高德卫星",
};
