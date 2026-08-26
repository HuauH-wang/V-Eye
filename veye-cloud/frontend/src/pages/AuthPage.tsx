import { useCallback, useEffect, useRef, useState } from "react";
import AuthCollage from "../components/AuthCollage";
import BrandHeader from "../components/BrandHeader";
import { useAuth } from "../context/AuthContext";

type Mode = "login" | "register";
type AuthStage = "collage" | "brand" | "panel";

type Props = {
  onLoginSuccess?: () => void;
};

function prefersReducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export default function AuthPage({ onLoginSuccess }: Props) {
  const { login, register } = useAuth();
  const [stage, setStage] = useState<AuthStage>(() => (prefersReducedMotion() ? "panel" : "collage"));
  const [mode, setMode] = useState<Mode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [brandReady, setBrandReady] = useState(false);
  const timers = useRef<number[]>([]);

  const clearTimers = useCallback(() => {
    timers.current.forEach((id) => window.clearTimeout(id));
    timers.current = [];
  }, []);

  const queue = useCallback((fn: () => void, ms: number) => {
    const id = window.setTimeout(fn, ms);
    timers.current.push(id);
  }, []);

  useEffect(() => {
    if (prefersReducedMotion()) return undefined;
    queue(() => setStage("brand"), 900);
    return clearTimers;
  }, [clearTimers, queue]);

  useEffect(() => {
    if (stage !== "brand") {
      setBrandReady(false);
      return undefined;
    }
    if (prefersReducedMotion()) {
      setBrandReady(true);
      return undefined;
    }
    queue(() => setBrandReady(true), 1150);
    return clearTimers;
  }, [clearTimers, queue, stage]);

  const openPanel = () => {
    setStage("panel");
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErr("");
    try {
      if (mode === "login") {
        await login(username.trim(), password);
      } else {
        await register(username.trim(), password, displayName.trim(), email.trim());
      }
      onLoginSuccess?.();
    } catch (ex) {
      setErr(String(ex));
    } finally {
      setBusy(false);
    }
  };

  const panelOpen = stage === "panel";
  const showCenterBrand = stage === "brand";

  return (
    <div className={`auth-shell auth-stage-${stage}${panelOpen ? " is-panel-open" : ""}`}>
      <div className="auth-visual">
        <AuthCollage interactive={!panelOpen} />
        <div className={`auth-hero-overlay${stage === "brand" ? " brand-focus" : ""}`} aria-hidden="true" />

        {showCenterBrand ? (
          <button
            type="button"
            className={`auth-splash-brand entering${brandReady ? " ready" : ""}`}
            onClick={brandReady ? openPanel : undefined}
            disabled={!brandReady}
            aria-label="打开登录面板"
          >
            <span className="auth-splash-brand-glow" aria-hidden />
            <span className="auth-splash-brand-inner">
              <BrandHeader className="auth-splash-brand-header" />
              <p className="auth-splash-tagline">户外视觉安全 · 自然观测控制台</p>
              <p className="auth-splash-desc">
                识别有毒植物、药品与通用场景，记录图鉴并与团队共享观测结果。
              </p>
              {brandReady ? <span className="auth-splash-hint">点击进入</span> : null}
            </span>
          </button>
        ) : null}
      </div>

      <div className="auth-panel-curtain" aria-hidden={!panelOpen}>
        <div className="auth-panel">
          <div className="auth-panel-inner">
            <header className="auth-panel-intro">
              <p className="auth-panel-kicker">V-Eye Cloud</p>
              <h1 className="auth-panel-title">{mode === "login" ? "欢迎回来" : "创建账号"}</h1>
              <p className="auth-panel-sub muted">
                {mode === "login"
                  ? "登录以同步识别图鉴与个人资料。"
                  : "注册后即可使用识别、图鉴与组队功能。"}
              </p>
            </header>

            <div className="auth-card neo-card">
              <div className="auth-tabs">
                <button
                  type="button"
                  className={`nav-btn${mode === "login" ? " active" : ""}`}
                  onClick={() => setMode("login")}
                >
                  登录
                </button>
                <button
                  type="button"
                  className={`nav-btn${mode === "register" ? " active" : ""}`}
                  onClick={() => setMode("register")}
                >
                  注册
                </button>
              </div>

              <form className="auth-form" onSubmit={submit}>
                <div className="field">
                  <label htmlFor="username">用户名</label>
                  <input
                    id="username"
                    type="text"
                    autoComplete="username"
                    placeholder="3–32 位字母、数字、下划线"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                  />
                </div>

                <div className="field">
                  <label htmlFor="password">密码</label>
                  <input
                    id="password"
                    type="password"
                    autoComplete={mode === "login" ? "current-password" : "new-password"}
                    placeholder="至少 6 位"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                </div>

                {mode === "register" ? (
                  <>
                    <div className="field">
                      <label htmlFor="displayName">昵称（可选）</label>
                      <input
                        id="displayName"
                        type="text"
                        placeholder="默认同用户名"
                        value={displayName}
                        onChange={(e) => setDisplayName(e.target.value)}
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="email">邮箱（可选）</label>
                      <input
                        id="email"
                        type="email"
                        autoComplete="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                      />
                    </div>
                  </>
                ) : null}

                {err ? (
                  <div className="alert inline" role="alert">
                    {err}
                  </div>
                ) : null}

                <button type="submit" className="btn primary auth-submit" disabled={busy}>
                  {busy ? "请稍候…" : mode === "login" ? "登录并进入" : "注册并进入"}
                </button>
              </form>

              <p className="muted auth-hint">
                登录后可使用识别、图鉴与个人中心；识别记录将关联到你的账号。
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
