import { useCallback, useState } from "react";
import { AuthProvider, useAuth } from "./context/AuthContext";
import AuthPage from "./pages/AuthPage";
import AppShell from "./AppShell";
import LeafEnterTransition from "./components/LeafEnterTransition";

function Root() {
  const { user, loading } = useAuth();
  const [entering, setEntering] = useState(false);

  const handleLoginSuccess = useCallback(() => {
    setEntering(true);
  }, []);

  const finishEnter = useCallback(() => {
    setEntering(false);
  }, []);

  if (loading) {
    return (
      <div className="auth-loading-shell">
        <div className="auth-card neo-card auth-loading-card">
          <p>正在验证登录状态…</p>
        </div>
      </div>
    );
  }

  if (user && entering) {
    return <LeafEnterTransition onComplete={finishEnter} />;
  }

  if (user) {
    return <AppShell />;
  }

  return <AuthPage onLoginSuccess={handleLoginSuccess} />;
}

export default function App() {
  return (
    <AuthProvider>
      <Root />
    </AuthProvider>
  );
}
