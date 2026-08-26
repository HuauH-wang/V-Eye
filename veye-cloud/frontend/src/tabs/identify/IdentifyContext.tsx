import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { identifyImage, libraryErrorMessage } from "../../lib/api";
import { loadGpsFix } from "../../lib/gpsSettings";
import { readFileAsDataUrl } from "../../lib/previewUrl";
import type { IdentifyResult, Scene } from "../../lib/types";
import { formatPct } from "../../lib/types";

type IdentifyContextValue = {
  scene: Scene;
  setScene: (s: Scene) => void;
  lang: string;
  setLang: (v: string) => void;
  deviceId: string;
  setDeviceId: (v: string) => void;
  file: File | null;
  setFile: (f: File | null) => void;
  previewUrl: string | null;
  result: IdentifyResult | null;
  busy: boolean;
  slowHint: boolean;
  err: string;
  runIdentify: () => Promise<void>;
  topCandidate: { label: string; confidence: number } | null;
};

const IdentifyContext = createContext<IdentifyContextValue | null>(null);

export function IdentifyProvider({
  children,
  onSuccess,
  onActivity,
}: {
  children: ReactNode;
  onSuccess: (result: IdentifyResult, scene: Scene, previewUrl: string | undefined) => void;
  onActivity: (msg: string) => void;
}) {
  const [scene, setScene] = useState<Scene>("generic");
  const [lang, setLang] = useState("zh-CN");
  const [deviceId, setDeviceId] = useState("web-console");
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [result, setResult] = useState<IdentifyResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [slowHint, setSlowHint] = useState(false);
  const [err, setErr] = useState("");
  const previewRef = useRef<string | null>(null);

  useEffect(() => {
    if (previewRef.current) {
      URL.revokeObjectURL(previewRef.current);
      previewRef.current = null;
    }
    if (!file) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    previewRef.current = url;
    setPreviewUrl(url);
    return () => {
      if (previewRef.current) {
        URL.revokeObjectURL(previewRef.current);
        previewRef.current = null;
      }
    };
  }, [file]);

  const topCandidate = useMemo(() => {
    if (!result?.candidates?.length) return null;
    return result.candidates[0];
  }, [result]);

  const runIdentify = useCallback(async () => {
    if (!file) {
      setErr("请先选择一张图片。");
      return;
    }
    setBusy(true);
    setSlowHint(false);
    setErr("");
    setResult(null);
    onActivity("正在分析上传图片…");

    const fd = new FormData();
    fd.append("image", file);
    fd.append("scene", scene);
    fd.append("lang", lang);
    if (deviceId.trim()) fd.append("device_id", deviceId.trim());
    const gps = loadGpsFix();
    if (gps) {
      fd.append("gps_lat", String(gps.lat));
      fd.append("gps_lng", String(gps.lng));
      if (gps.accuracyM !== undefined) fd.append("accuracy_m", String(gps.accuracyM));
    }

    const ctrl = new AbortController();
    const slowTimer = window.setTimeout(() => setSlowHint(true), 3000);
    const abortTimer = window.setTimeout(() => ctrl.abort(), 120_000);

    try {
      const data = await identifyImage(fd, ctrl.signal);
      setResult(data);
      let previewDataUrl: string | undefined;
      try {
        previewDataUrl = await readFileAsDataUrl(file);
      } catch {
        previewDataUrl = undefined;
      }
      onSuccess(data, scene, previewDataUrl);
      onActivity(
        `识别完成：${data.label_main}（${formatPct(data.confidence)}）· ${data.latency_ms}ms`,
      );
    } catch (e) {
      setErr(libraryErrorMessage(e));
      onActivity("识别失败，请检查服务与网络。");
    } finally {
      window.clearTimeout(slowTimer);
      window.clearTimeout(abortTimer);
      setBusy(false);
      setSlowHint(false);
    }
  }, [file, scene, lang, deviceId, previewUrl, onSuccess, onActivity]);

  const value = useMemo(
    () => ({
      scene,
      setScene,
      lang,
      setLang,
      deviceId,
      setDeviceId,
      file,
      setFile,
      previewUrl,
      result,
      busy,
      slowHint,
      err,
      runIdentify,
      topCandidate,
    }),
    [scene, lang, deviceId, file, previewUrl, result, busy, slowHint, err, runIdentify, topCandidate],
  );

  return <IdentifyContext.Provider value={value}>{children}</IdentifyContext.Provider>;
}

export function useIdentify(): IdentifyContextValue {
  const ctx = useContext(IdentifyContext);
  if (!ctx) throw new Error("useIdentify must be used within IdentifyProvider");
  return ctx;
}
