import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { MAP_TILE_LAYERS, MAP_TILE_ERROR_FALLBACK, MAP_TILE_ERROR_WARN, isAmapLayer, mapTileUrl } from "../lib/mapTiles";
import { wgs84ToGcj02 } from "../lib/gcj02";
import type { MapBaseLayer, MapDemoPoint, MapLayersResponse, MapTrajectory } from "../lib/types";
import { formatGpsCoords, formatTs, memberMapPopupHtml, riskLabel } from "../lib/types";
import { sosEventLabel } from "../lib/sos";

type LayerVisibility = {
  identify: boolean;
  sos: boolean;
  members: boolean;
  trajectories: boolean;
  demoPoints?: boolean;
};

type Props = {
  data: MapLayersResponse;
  trajectories?: MapTrajectory[];
  baseLayer: MapBaseLayer;
  visibility: LayerVisibility;
  demoPoints?: MapDemoPoint[];
  focusRequestId?: string | null;
  onTileLayerWarning?: () => void;
  onTileLayerError?: (layer: MapBaseLayer) => void;
};

function markerIcon(color: string, glyph: string): L.DivIcon {
  return L.divIcon({
    className: "obs-map-marker-wrap",
    html: `<span class="obs-map-marker" style="--marker-color:${color}">${glyph}</span>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}

const ICONS = {
  identify: markerIcon("#40916c", "识"),
  sos: sosStarIcon(),
  member: markerIcon("#2d6a4f", "队"),
};

const TRAJECTORY_COLORS = ["#1d3557", "#e63946", "#2a9d8f", "#f4a261", "#7209b7", "#457b9d"];

function sosStarIcon(): L.DivIcon {
  return L.divIcon({
    className: "obs-map-marker-wrap",
    html: `<span class="obs-map-marker obs-map-marker--sos-star" aria-hidden="true">★</span>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });
}

function mapCoords(baseLayer: MapBaseLayer, lat: number, lng: number): [number, number] {
  return isAmapLayer(baseLayer) ? wgs84ToGcj02(lat, lng) : [lat, lng];
}

function attachTileErrorHandler(
  tile: L.TileLayer,
  layer: MapBaseLayer,
  onWarning: Props["onTileLayerWarning"],
  onError: Props["onTileLayerError"],
  counter: { value: number; warned: boolean },
): void {
  tile.on("tileload", () => {
    if (counter.value > 0) counter.value = Math.max(0, counter.value - 1);
  });
  tile.on("tileerror", () => {
    counter.value += 1;
    if (!counter.warned && counter.value >= MAP_TILE_ERROR_WARN) {
      counter.warned = true;
      onWarning?.();
    }
    if (counter.value >= MAP_TILE_ERROR_FALLBACK) {
      onError?.(layer);
      counter.value = 0;
      counter.warned = false;
    }
  });
}

function createTileLayer(
  layer: MapBaseLayer,
  onWarning: Props["onTileLayerWarning"],
  onError: Props["onTileLayerError"],
  counter: { value: number; warned: boolean },
): L.TileLayer {
  const spec = MAP_TILE_LAYERS[layer];
  const tile = L.tileLayer(mapTileUrl(spec.provider), {
    maxNativeZoom: spec.maxNativeZoom,
    maxZoom: spec.maxZoom,
    attribution: spec.attribution,
    updateWhenIdle: true,
    updateWhenZooming: false,
    keepBuffer: 8,
  });
  attachTileErrorHandler(tile, layer, onWarning, onError, counter);
  return tile;
}

export default function ObservatoryMapLeaflet({
  data,
  trajectories = [],
  baseLayer,
  visibility,
  demoPoints = [],
  focusRequestId,
  onTileLayerWarning,
  onTileLayerError,
}: Props) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const tileRef = useRef<L.TileLayer | null>(null);
  const overlayRef = useRef<L.LayerGroup | null>(null);
  const tileErrorsRef = useRef({ value: 0, warned: false });
  const onTileWarningRef = useRef(onTileLayerWarning);
  const onTileErrorRef = useRef(onTileLayerError);
  onTileWarningRef.current = onTileLayerWarning;
  onTileErrorRef.current = onTileLayerError;

  useEffect(() => {
    if (!rootRef.current || mapRef.current) return;

    const map = L.map(rootRef.current, {
      center: [30.5928, 114.3055],
      zoom: 12,
      maxZoom: 22,
      zoomControl: true,
      attributionControl: true,
    });
    mapRef.current = map;
    overlayRef.current = L.layerGroup().addTo(map);

    const resize = () => map.invalidateSize({ animate: false });
    window.setTimeout(resize, 0);
    window.setTimeout(resize, 300);
    window.addEventListener("resize", resize);

    return () => {
      window.removeEventListener("resize", resize);
      map.remove();
      mapRef.current = null;
      tileRef.current = null;
      overlayRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    if (tileRef.current) map.removeLayer(tileRef.current);
    tileErrorsRef.current = { value: 0, warned: false };
    const tile = createTileLayer(
      baseLayer,
      () => onTileWarningRef.current?.(),
      (layer) => onTileErrorRef.current?.(layer),
      tileErrorsRef.current,
    );
    tile.addTo(map);
    tileRef.current = tile;
    window.setTimeout(() => map.invalidateSize({ animate: false }), 0);
  }, [baseLayer]);

  useEffect(() => {
    const map = mapRef.current;
    const overlay = overlayRef.current;
    if (!map || !overlay) return;

    overlay.clearLayers();
    const bounds = L.latLngBounds([]);

    if (visibility.trajectories) {
      trajectories.forEach((track, index) => {
        if (track.points.length < 2) return;
        const latlngs = track.points.map((p) => mapCoords(baseLayer, p.gps_lat, p.gps_lng));
        latlngs.forEach((ll) => bounds.extend(ll));
        const color = TRAJECTORY_COLORS[index % TRAJECTORY_COLORS.length];
        L.polyline(latlngs, {
          color,
          weight: 4,
          opacity: 0.85,
          dashArray: track.points.length > 30 ? undefined : "6 8",
        })
          .bindTooltip(`${track.display_name} 轨迹 (${track.points.length} 点)`, { sticky: true })
          .addTo(overlay);
      });
    }

    if (visibility.identify) {
      for (const item of data.identify) {
        const latlng: L.LatLngExpression = mapCoords(baseLayer, item.gps_lat, item.gps_lng);
        bounds.extend(latlng);
        const marker = L.marker(latlng, { icon: ICONS.identify });
        marker.bindPopup(
          `<strong>${item.label_main}</strong><br/>${riskLabel(item.risk_level)} · ${item.scene}<br/>${formatTs(item.server_ts)}${
            item.summary ? `<br/><span class="muted">${item.summary.slice(0, 120)}</span>` : ""
          }`,
        );
        if (item.accuracy_m && item.accuracy_m > 0) {
          L.circle(latlng, {
            radius: item.accuracy_m,
            color: "#40916c",
            weight: 1,
            fillColor: "#52b788",
            fillOpacity: 0.12,
          }).addTo(overlay);
        }
        marker.addTo(overlay);
      }
    }

    if (visibility.sos) {
      for (const item of data.sos) {
        const latlng: L.LatLngExpression = mapCoords(baseLayer, item.gps_lat, item.gps_lng);
        bounds.extend(latlng);
        const marker = L.marker(latlng, { icon: ICONS.sos });
        const coords = formatGpsCoords(item.gps_lat, item.gps_lng, item.accuracy_m);
        marker.bindPopup(
          `<strong>SOS · ${sosEventLabel(item.event_type)}</strong><br/>${
            item.display_name ? `${item.display_name}<br/>` : ""
          }${coords ? `${coords}<br/>` : ""}${formatTs(item.server_ts)}${
            item.device_id ? `<br/>${item.device_id}` : ""
          }`,
        );
        if (item.accuracy_m && item.accuracy_m > 0) {
          L.circle(latlng, {
            radius: item.accuracy_m,
            color: "#e76f51",
            weight: 1,
            fillColor: "#e76f51",
            fillOpacity: 0.14,
          }).addTo(overlay);
        }
        marker.addTo(overlay);
      }
    }

    if (visibility.members) {
      for (const item of data.members) {
        const latlng: L.LatLngExpression = mapCoords(baseLayer, item.gps_lat, item.gps_lng);
        bounds.extend(latlng);
        const marker = L.marker(latlng, { icon: ICONS.member });
        marker.bindPopup(memberMapPopupHtml(item));
        if (item.accuracy_m && item.accuracy_m > 0) {
          L.circle(latlng, {
            radius: item.accuracy_m,
            color: "#1b4332",
            weight: 1,
            fillColor: "#2d6a4f",
            fillOpacity: 0.12,
          }).addTo(overlay);
        }
        marker.addTo(overlay);
      }
    }

    if (visibility.demoPoints !== false && demoPoints.length > 0) {
      for (const item of demoPoints) {
        const latlng: L.LatLngExpression = mapCoords(baseLayer, item.gps_lat, item.gps_lng);
        bounds.extend(latlng);
        const color = item.color ?? (item.kind === "start" ? "#6c757d" : "#40916c");
        const extraClass = item.kind === "task" ? "obs-map-marker--task" : "obs-map-marker--start";
        const marker = L.marker(latlng, {
          icon: L.divIcon({
            className: "obs-map-marker-wrap",
            html: `<span class="obs-map-marker ${extraClass}" style="--marker-color:${color}">${item.glyph}</span>`,
            iconSize: [28, 28],
            iconAnchor: [14, 14],
          }),
        });
        const coords = formatGpsCoords(item.gps_lat, item.gps_lng);
        marker.bindPopup(
          `<strong>${item.label}</strong>${item.member ? `<br/>@${item.member}` : ""}${
            coords ? `<br/>${coords}` : ""
          }`,
        );
        marker.addTo(overlay);
      }
    }

    if (focusRequestId) {
      const target = data.identify.find((x) => x.request_id === focusRequestId);
      if (target) {
        const [lat, lng] = mapCoords(baseLayer, target.gps_lat, target.gps_lng);
        map.setView([lat, lng], Math.max(map.getZoom(), 18), { animate: true });
      }
    } else if (bounds.isValid()) {
      map.fitBounds(bounds.pad(0.18), { maxZoom: 18 });
    }
  }, [data, trajectories, visibility, demoPoints, focusRequestId, baseLayer]);

  return <div ref={rootRef} className="obs-map-canvas" aria-label="观测地图" />;
}
