import { useIdentify } from "./IdentifyContext";
import type { Scene } from "../../lib/types";

type Props = {
  variant?: "sidebar" | "settings";
};

export default function IdentifySidebar({ variant = "sidebar" }: Props) {
  const {
    scene,
    setScene,
    lang,
    setLang,
    deviceId,
    setDeviceId,
    file,
    setFile,
    busy,
    runIdentify,
  } = useIdentify();

  if (variant === "settings") {
    return (
      <div className="form-grid neo-card settings-section settings-identify-form">
        <div className="field">
          <label htmlFor="scene">识别场景</label>
          <select id="scene" value={scene} onChange={(e) => setScene(e.target.value as Scene)}>
            <option value="generic">generic · 通用</option>
            <option value="toxic_plant">toxic_plant · 有毒植物</option>
            <option value="medicine">medicine · 药品</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="lang">语言</label>
          <input id="lang" type="text" value={lang} onChange={(e) => setLang(e.target.value)} />
        </div>
        <div className="field span-2">
          <label htmlFor="dev">device_id</label>
          <input id="dev" type="text" value={deviceId} onChange={(e) => setDeviceId(e.target.value)} />
        </div>
        <label className="file-btn">
          选择图片
          <input
            type="file"
            accept="image/*"
            hidden
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </label>
        <button type="button" className="btn primary" disabled={busy} onClick={runIdentify}>
          {busy ? "识别中…" : "开始识别"}
        </button>
        {file ? <p className="muted span-2 settings-identify-file">{file.name}</p> : null}
      </div>
    );
  }

  return (
    <div className="sidebar-section">
      <p className="sidebar-section-title">识别参数</p>
      <div className="sidebar-fields">
        <div className="field">
          <label htmlFor="scene">识别场景</label>
          <select id="scene" value={scene} onChange={(e) => setScene(e.target.value as Scene)}>
            <option value="generic">generic · 通用</option>
            <option value="toxic_plant">toxic_plant · 有毒植物</option>
            <option value="medicine">medicine · 药品</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="lang">语言</label>
          <input id="lang" type="text" value={lang} onChange={(e) => setLang(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="dev">device_id</label>
          <input id="dev" type="text" value={deviceId} onChange={(e) => setDeviceId(e.target.value)} />
        </div>
        <label className="file-btn sidebar-file-btn">
          选择图片
          <input
            type="file"
            accept="image/*"
            hidden
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </label>
        {file ? <p className="muted sidebar-file-name">{file.name}</p> : null}
        <button type="button" className="btn primary sidebar-run-btn" disabled={busy} onClick={runIdentify}>
          {busy ? "识别中…" : "开始识别"}
        </button>
      </div>
    </div>
  );
}
