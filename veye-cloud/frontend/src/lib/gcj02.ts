/** WGS84 → GCJ-02（火星坐标），高德底图打点需转换 */

const A = 6378245.0;
const EE = 0.00669342162296594323;

function outOfChina(lat: number, lng: number): boolean {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
}

function transformLat(lng: number, lat: number): number {
  let ret =
    -100.0 +
    2.0 * lng +
    3.0 * lat +
    0.2 * lat * lat +
    0.1 * lng * lat +
    0.2 * Math.sqrt(Math.abs(lng));
  ret += ((20.0 * Math.sin(6.0 * lng * Math.PI) + 20.0 * Math.sin(2.0 * lng * Math.PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(lat * Math.PI) + 40.0 * Math.sin((lat / 3.0) * Math.PI)) * 2.0) / 3.0;
  ret += ((160.0 * Math.sin((lat / 12.0) * Math.PI) + 320 * Math.sin((lat * Math.PI) / 30.0)) * 2.0) / 3.0;
  return ret;
}

function transformLng(lng: number, lat: number): number {
  let ret =
    300.0 +
    lng +
    2.0 * lat +
    0.1 * lng * lng +
    0.1 * lng * lat +
    0.1 * Math.sqrt(Math.abs(lng));
  ret += ((20.0 * Math.sin(6.0 * lng * Math.PI) + 20.0 * Math.sin(2.0 * lng * Math.PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(lng * Math.PI) + 40.0 * Math.sin((lng / 3.0) * Math.PI)) * 2.0) / 3.0;
  ret += ((150.0 * Math.sin((lng / 12.0) * Math.PI) + 300.0 * Math.sin((lng / 30.0) * Math.PI)) * 2.0) / 3.0;
  return ret;
}

export function wgs84ToGcj02(lat: number, lng: number): [number, number] {
  if (outOfChina(lat, lng)) return [lat, lng];
  let dLat = transformLat(lng - 105.0, lat - 35.0);
  let dLng = transformLng(lng - 105.0, lat - 35.0);
  const radLat = (lat / 180.0) * Math.PI;
  let magic = Math.sin(radLat);
  magic = 1 - EE * magic * magic;
  const sqrtMagic = Math.sqrt(magic);
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * Math.PI);
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * Math.PI);
  return [lat + dLat, lng + dLng];
}
