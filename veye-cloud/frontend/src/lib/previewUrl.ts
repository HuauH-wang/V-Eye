/** blob: URL 刷新后失效，仅 data: URL 可持久化到 localStorage。 */
export function sanitizePreviewDataUrl(url?: string): string | undefined {
  if (!url) return undefined;
  if (url.startsWith("data:image/")) return url;
  return undefined;
}

export function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result;
      if (typeof result === "string" && result.startsWith("data:image/")) {
        resolve(result);
        return;
      }
      reject(new Error("preview_read_failed"));
    };
    reader.onerror = () => reject(reader.error ?? new Error("preview_read_failed"));
    reader.readAsDataURL(file);
  });
}
