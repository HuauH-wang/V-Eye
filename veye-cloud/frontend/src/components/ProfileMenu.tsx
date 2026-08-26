import { useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import UserAvatar from "./UserAvatar";
import { authErrorMessage } from "../lib/auth";
import { formatTs } from "../lib/types";

type Props = {
  onActivity: (msg: string) => void;
};

export default function ProfileMenu({ onActivity }: Props) {
  const { user, logout, refreshUser, updateDisplayName, uploadAvatar, removeAvatar } = useAuth();
  const [open, setOpen] = useState(false);
  const [displayName, setDisplayName] = useState(user?.display_name ?? "");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [saved, setSaved] = useState(false);
  const [avatarKey, setAvatarKey] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setDisplayName(user?.display_name ?? "");
  }, [user?.display_name]);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const onEsc = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDocClick);
    document.addEventListener("keydown", onEsc);
    return () => {
      document.removeEventListener("mousedown", onDocClick);
      document.removeEventListener("keydown", onEsc);
    };
  }, [open]);

  if (!user) return null;

  const saveName = async () => {
    setBusy(true);
    setErr("");
    setSaved(false);
    try {
      await updateDisplayName(displayName.trim());
      setSaved(true);
      onActivity(`昵称已更新：${displayName.trim()}`);
      window.setTimeout(() => setSaved(false), 2000);
    } catch (e) {
      setErr(authErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const onPickAvatar = async (file: File | null) => {
    if (!file) return;
    setBusy(true);
    setErr("");
    try {
      await uploadAvatar(file);
      setAvatarKey((k) => k + 1);
      onActivity("头像已更新");
    } catch (e) {
      setErr(authErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const onRemoveAvatar = async () => {
    setBusy(true);
    setErr("");
    try {
      await removeAvatar();
      setAvatarKey((k) => k + 1);
      onActivity("已恢复默认头像");
    } catch (e) {
      setErr(authErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const handleRefresh = async () => {
    setBusy(true);
    setErr("");
    try {
      await refreshUser();
      setAvatarKey((k) => k + 1);
      onActivity("个人资料已刷新");
    } catch (e) {
      setErr(authErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="profile-menu-wrap" ref={rootRef}>
      <button
        type="button"
        className={`profile-menu-trigger neo-card${open ? " open" : ""}`}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="dialog"
      >
        <UserAvatar user={user} size="sm" cacheKey={avatarKey} />
        <span className="profile-menu-name">{user.display_name}</span>
        <span className="profile-menu-chevron" aria-hidden>
          ▾
        </span>
      </button>

      {open ? (
        <div className="profile-menu-panel neo-card" role="dialog" aria-label="个人中心">
          <div className="profile-menu-head">
            <UserAvatar user={user} size="lg" cacheKey={avatarKey} />
            <div>
              <h3>{user.display_name}</h3>
              <p className="muted">@{user.username}</p>
            </div>
          </div>

          <div className="profile-menu-avatar-actions">
            <label className="file-btn">
              更换头像
              <input
                type="file"
                accept="image/*"
                hidden
                disabled={busy}
                onChange={(e) => {
                  onPickAvatar(e.target.files?.[0] ?? null);
                  e.target.value = "";
                }}
              />
            </label>
            {user.has_avatar ? (
              <button type="button" className="btn ghost" disabled={busy} onClick={onRemoveAvatar}>
                恢复默认
              </button>
            ) : null}
          </div>
          <p className="muted profile-menu-hint">支持 JPG/PNG/WebP，最大 2MB，自动裁切为方形。</p>

          <div className="profile-stats compact">
            <div className="mini-card neo-card">
              <h3>识别次数</h3>
              <p className="stat-num">{user.stats.identify_count}</p>
            </div>
            <div className="mini-card neo-card">
              <h3>注册时间</h3>
              <p className="small">{formatTs(user.created_at)}</p>
            </div>
          </div>

          <div className="field">
            <label htmlFor="profileName">显示昵称</label>
            <input
              id="profileName"
              type="text"
              value={displayName}
              disabled={busy}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </div>

          <div className="profile-menu-actions">
            <button type="button" className="btn primary" disabled={busy} onClick={saveName}>
              {saved ? "已保存 ✓" : "保存昵称"}
            </button>
            <button type="button" className="btn secondary" disabled={busy} onClick={handleRefresh}>
              刷新
            </button>
            <button
              type="button"
              className="btn danger"
              disabled={busy}
              onClick={() => {
                logout();
                setOpen(false);
              }}
            >
              退出登录
            </button>
          </div>

          {user.email ? <p className="muted small">{user.email}</p> : null}
          <p className="mono small muted">ID: {user.id}</p>

          {err ? (
            <div className="alert inline" role="alert">
              {err}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
