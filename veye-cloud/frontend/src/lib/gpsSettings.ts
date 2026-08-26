const LS_GPS_LAT = "veye_gps_lat";
const LS_GPS_LNG = "veye_gps_lng";
const LS_GPS_ACC = "veye_gps_accuracy_m";

export type GpsFix = {
  lat: number;
  lng: number;
  accuracyM?: number;
};

export function loadGpsFix(): GpsFix | null {
  const latRaw = (localStorage.getItem(LS_GPS_LAT) ?? "").trim();
  const lngRaw = (localStorage.getItem(LS_GPS_LNG) ?? "").trim();
  if (!latRaw || !lngRaw) return null;
  const lat = Number(latRaw);
  const lng = Number(lngRaw);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
  const accRaw = (localStorage.getItem(LS_GPS_ACC) ?? "").trim();
  const accuracyM = accRaw ? Number(accRaw) : undefined;
  return {
    lat,
    lng,
    accuracyM: accuracyM !== undefined && Number.isFinite(accuracyM) ? accuracyM : undefined,
  };
}

export function saveGpsFix(fix: GpsFix | null): void {
  if (!fix) {
    localStorage.removeItem(LS_GPS_LAT);
    localStorage.removeItem(LS_GPS_LNG);
    localStorage.removeItem(LS_GPS_ACC);
    return;
  }
  localStorage.setItem(LS_GPS_LAT, String(fix.lat));
  localStorage.setItem(LS_GPS_LNG, String(fix.lng));
  if (fix.accuracyM !== undefined && Number.isFinite(fix.accuracyM)) {
    localStorage.setItem(LS_GPS_ACC, String(fix.accuracyM));
  } else {
    localStorage.removeItem(LS_GPS_ACC);
  }
}

export function gpsFixFromFields(lat: string, lng: string, accuracy: string): GpsFix | null {
  const latN = lat.trim() ? Number(lat.trim()) : NaN;
  const lngN = lng.trim() ? Number(lng.trim()) : NaN;
  if (!Number.isFinite(latN) || !Number.isFinite(lngN)) return null;
  const accN = accuracy.trim() ? Number(accuracy.trim()) : undefined;
  return {
    lat: latN,
    lng: lngN,
    accuracyM: accN !== undefined && Number.isFinite(accN) ? accN : undefined,
  };
}
