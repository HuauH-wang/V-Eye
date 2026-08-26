import { lazy, Suspense } from "react";
import { isAmapSdkConfigured } from "../lib/amapSdk";
import type { MapBaseLayer, MapDemoPoint, MapLayersResponse, MapTrajectory } from "../lib/types";

const ObservatoryMapAmap = lazy(() => import("./ObservatoryMapAmap"));
const ObservatoryMapLeaflet = lazy(() => import("./ObservatoryMapLeaflet"));

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
  onMapError?: (message: string) => void;
};

export default function ObservatoryMap(props: Props) {
  const sdkMode = isAmapSdkConfigured();
  const MapImpl = sdkMode ? ObservatoryMapAmap : ObservatoryMapLeaflet;
  const shared = {
    data: props.data,
    trajectories: props.trajectories,
    baseLayer: props.baseLayer,
    visibility: props.visibility,
    demoPoints: props.demoPoints,
    focusRequestId: props.focusRequestId,
  };
  const mapProps = sdkMode
    ? { ...shared, onMapError: props.onMapError }
    : {
        ...shared,
        onTileLayerWarning: props.onTileLayerWarning,
        onTileLayerError: props.onTileLayerError,
      };

  return (
    <Suspense fallback={<div className="obs-map-canvas muted">地图加载中…</div>}>
      <MapImpl {...mapProps} />
    </Suspense>
  );
}
