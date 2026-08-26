import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "../lib/api";

export function useAuthenticatedImage(path: string, refreshKey: string | number = 0) {
  const [url, setUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(true);
  const urlRef = useRef<string | null>(null);

  const revokeCurrent = useCallback(() => {
    if (urlRef.current) {
      URL.revokeObjectURL(urlRef.current);
      urlRef.current = null;
    }
  }, []);

  const load = useCallback(async () => {
    if (!path) {
      revokeCurrent();
      setUrl(null);
      setFailed(false);
      setLoading(false);
      return;
    }

    setFailed(false);
    const hadUrl = Boolean(urlRef.current);
    if (!hadUrl) setLoading(true);

    try {
      const res = await apiFetch(path, { method: "GET" });
      if (!res.ok) throw new Error(`image_${res.status}`);
      const blob = await res.blob();
      if (!blob.size) throw new Error("image_empty");
      const next = URL.createObjectURL(blob);
      revokeCurrent();
      urlRef.current = next;
      setUrl(next);
    } catch {
      revokeCurrent();
      setUrl(null);
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [path, revokeCurrent]);

  useEffect(() => {
    void load();
    return () => {
      revokeCurrent();
    };
  }, [load, refreshKey, revokeCurrent]);

  return { url, failed, loading, retry: load };
}
