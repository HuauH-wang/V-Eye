import { useEffect, useRef, useState } from "react";
import { loadAmap } from "../lib/amapSdk";
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
  onMapError?: (message: string) => void;
};

const DEFAULT_CENTER: [number, number] = [114.3055, 30.5928];

function markerHtml(color: string, glyph: string): string {
  return `<span class="obs-map-marker" style="--marker-color:${color}">${glyph}</span>`;
}

function toGcj(lng: number, lat: number): [number, number] {
  const [gLat, gLng] = wgs84ToGcj02(lat, lng);
  return [gLng, gLat];
}

function sosStarHtml(): string {
  return `<span class="obs-map-marker obs-map-marker--sos-star" aria-hidden="true">★</span>`;
}

const TRAJECTORY_COLORS = ["#1d3557", "#e63946", "#2a9d8f", "#f4a261", "#7209b7", "#457b9d"];

export default function ObservatoryMapAmap({
  data,
  trajectories = [],
  baseLayer,
  visibility,
  demoPoints = [],
  focusRequestId,
  onMapError,
}: Props) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<AMap.Map | null>(null);
  const overlaysRef = useRef<AMap.Marker[]>([]);
  const circlesRef = useRef<AMap.Circle[]>([]);
  const polylinesRef = useRef<AMap.Polyline[]>([]);
  const amapNsRef = useRef<typeof AMap | null>(null);
  const satLayerRef = useRef<AMap.TileLayer | null>(null);
  const roadLayerRef = useRef<AMap.TileLayer | null>(null);
  const [ready, setReady] = useState(false);
  const onErrorRef = useRef(onMapError);
  onErrorRef.current = onMapError;

  useEffect(() => {
    let cancelled = false;
    let map: AMap.Map | null = null;

    loadAmap()
      .then((AMapNS) => {
        if (cancelled || !rootRef.current) return;
        amapNsRef.current = AMapNS;
        map = new AMapNS.Map(rootRef.current, {
          zoom: 12,
          center: DEFAULT_CENTER,
          viewMode: "2D",
          resizeEnable: true,
        });
        map.addControl(new AMapNS.Scale());
        map.addControl(new AMapNS.ToolBar({ position: "RB" }));
        satLayerRef.current = new AMapNS.TileLayer.Satellite();
        roadLayerRef.current = new AMapNS.TileLayer.RoadNet();
        mapRef.current = map;
        setReady(true);
      })
      .catch((e) => {
        onErrorRef.current?.(String(e));
      });

    return () => {
      cancelled = true;
      overlaysRef.current = [];
      circlesRef.current = [];
      satLayerRef.current = null;
      roadLayerRef.current = null;
      map?.destroy();
      mapRef.current = null;
      setReady(false);
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const AMapNS = amapNsRef.current;
    if (!map || !ready || !AMapNS) return;

    if (baseLayer === "amap_satellite") {
      const sat = satLayerRef.current;
      const road = roadLayerRef.current;
      if (sat && road) map.setLayers([sat, road]);
    } else {
      map.setLayers([new AMapNS.TileLayer()]);
    }
  }, [baseLayer, ready]);

  useEffect(() => {
    const map = mapRef.current;
    const AMapNS = amapNsRef.current;
    if (!map || !ready || !AMapNS) return;

    for (const m of overlaysRef.current) {
      map.remove(m);
    }
    for (const c of circlesRef.current) {
      map.remove(c);
    }
    for (const p of polylinesRef.current) {
      map.remove(p);
    }
    overlaysRef.current = [];
    circlesRef.current = [];
    polylinesRef.current = [];

    const markers: AMap.Marker[] = [];
    const circles: AMap.Circle[] = [];

    const addMarker = (
      lng: number,
      lat: number,
      html: string,
      popup: string,
      circle?: { color: string; fill: string; opacity: number },
      accuracy?: number | null,
    ) => {
      const marker = new AMapNS.Marker({
        position: [lng, lat],
        content: html,
        offset: new AMapNS.Pixel(-14, -14),
        anchor: "center",
      });
      const info = new AMapNS.InfoWindow({ content: popup, offset: new AMapNS.Pixel(0, -18) });
      marker.on("click", () => info.open(map, marker.getPosition()));
      map.add(marker);
      markers.push(marker);

      if (accuracy && accuracy > 0 && circle) {
        const ring = new AMapNS.Circle({
          center: [lng, lat],
          radius: accuracy,
          strokeColor: circle.color,
          strokeWeight: 1,
          fillColor: circle.fill,
          fillOpacity: circle.opacity,
        });
        map.add(ring);
        circles.push(ring);
      }
    };

    if (visibility.trajectories) {
      trajectories.forEach((track, index) => {
        if (track.points.length < 2) return;
        const path = track.points.map((p) => {
          const [lng, lat] = toGcj(p.gps_lng, p.gps_lat);
          return [lng, lat] as [number, number];
        });
        const color = TRAJECTORY_COLORS[index % TRAJECTORY_COLORS.length];
        const line = new AMapNS.Polyline({
          path,
          strokeColor: color,
          strokeWeight: 4,
          strokeOpacity: 0.85,
        });
        map.add(line);
        polylinesRef.current.push(line);
      });
    }

    if (visibility.identify) {
      for (const item of data.identify) {
        const [lng, lat] = toGcj(item.gps_lng, item.gps_lat);
        addMarker(
          lng,
          lat,
          markerHtml("#40916c", "识"),
          `<strong>${item.label_main}</strong><br/>${riskLabel(item.risk_level)} · ${item.scene}<br/>${formatTs(item.server_ts)}${
            item.summary ? `<br/><span class="muted">${item.summary.slice(0, 120)}</span>` : ""
          }`,
          { color: "#40916c", fill: "#52b788", opacity: 0.12 },
          item.accuracy_m,
        );
      }
    }

    if (visibility.sos) {
      for (const item of data.sos) {
        const [lng, lat] = toGcj(item.gps_lng, item.gps_lat);
        const coords = formatGpsCoords(item.gps_lat, item.gps_lng, item.accuracy_m);
        addMarker(
          lng,
          lat,
          sosStarHtml(),
          `<strong>SOS · ${sosEventLabel(item.event_type)}</strong><br/>${
            item.display_name ? `${item.display_name}<br/>` : ""
          }${coords ? `${coords}<br/>` : ""}${formatTs(item.server_ts)}${
            item.device_id ? `<br/>${item.device_id}` : ""
          }`,
          { color: "#e76f51", fill: "#e76f51", opacity: 0.14 },
          item.accuracy_m,
        );
      }
    }

    if (visibility.members) {
      for (const item of data.members) {
        const [lng, lat] = toGcj(item.gps_lng, item.gps_lat);
        addMarker(
          lng,
          lat,
          markerHtml("#2d6a4f", "队"),
          memberMapPopupHtml(item),
          { color: "#1b4332", fill: "#2d6a4f", opacity: 0.12 },
          item.accuracy_m,
        );
      }
    }

    if (visibility.demoPoints !== false && demoPoints.length > 0) {
      for (const item of demoPoints) {
        const [lng, lat] = toGcj(item.gps_lng, item.gps_lat);
        const color = item.color ?? (item.kind === "start" ? "#6c757d" : "#40916c");
        const coords = formatGpsCoords(item.gps_lat, item.gps_lng);
        const extraClass = item.kind === "task" ? "obs-map-marker--task" : "obs-map-marker--start";
        addMarker(
          lng,
          lat,
          `<span class="obs-map-marker ${extraClass}" style="--marker-color:${color}">${item.glyph}</span>`,
          `<strong>${item.label}</strong>${
            item.member ? `<br/>@${item.member}` : ""
          }${coords ? `<br/>${coords}` : ""}`,
          { color, fill: color, opacity: item.kind === "start" ? 0.1 : 0.16 },
        );
      }
    }

    overlaysRef.current = markers;
    circlesRef.current = circles;

    if (focusRequestId) {
      const target = data.identify.find((x) => x.request_id === focusRequestId);
      if (target) {
        const [lng, lat] = toGcj(target.gps_lng, target.gps_lat);
        map.setZoomAndCenter(Math.max(map.getZoom(), 18), [lng, lat]);
      }
    } else if (markers.length > 0 || polylinesRef.current.length > 0) {
      map.setFitView([...markers, ...circles, ...polylinesRef.current], false, [48, 48, 48, 48], 18);
    }
  }, [data, trajectories, visibility, demoPoints, focusRequestId, ready]);

  return <div ref={rootRef} className="obs-map-canvas obs-map-canvas--amap" aria-label="观测地图" />;
}
