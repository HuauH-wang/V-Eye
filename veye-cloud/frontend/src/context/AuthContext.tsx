import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  authErrorMessage,
  clearAuth,
  fetchMe,
  loadCachedUser,
  loadToken,
  loginUser,
  registerUser,
  saveAuth,
  updateProfile,
  uploadAvatar,
  deleteAvatar,
} from "../lib/auth";
import type { UserProfile } from "../lib/types";

type AuthContextValue = {
  user: UserProfile | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (
    username: string,
    password: string,
    displayName?: string,
    email?: string,
  ) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
  updateDisplayName: (displayName: string) => Promise<void>;
  uploadAvatar: (file: File) => Promise<void>;
  removeAvatar: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(() => loadCachedUser());
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    const token = loadToken();
    if (!token) {
      setUser(null);
      return;
    }
    const profile = await fetchMe();
    setUser(profile);
    saveAuth(token, profile);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        if (loadToken()) {
          await refreshUser();
        } else {
          setUser(null);
        }
      } catch {
        if (!cancelled) {
          clearAuth();
          setUser(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refreshUser]);

  const login = useCallback(async (username: string, password: string) => {
    try {
      const data = await loginUser({ username, password });
      saveAuth(data.access_token, data.user);
      setUser(data.user);
    } catch (e) {
      throw new Error(authErrorMessage(e));
    }
  }, []);

  const register = useCallback(
    async (username: string, password: string, displayName?: string, email?: string) => {
      try {
        const data = await registerUser({
          username,
          password,
          display_name: displayName?.trim() || undefined,
          email: email?.trim() || undefined,
        });
        saveAuth(data.access_token, data.user);
        setUser(data.user);
      } catch (e) {
        throw new Error(authErrorMessage(e));
      }
    },
    [],
  );

  const logout = useCallback(() => {
    clearAuth();
    setUser(null);
  }, []);

  const updateDisplayName = useCallback(async (displayName: string) => {
    const profile = await updateProfile({ display_name: displayName });
    const token = loadToken();
    if (token) saveAuth(token, profile);
    setUser(profile);
  }, []);

  const uploadAvatarFn = useCallback(async (file: File) => {
    const profile = await uploadAvatar(file);
    const token = loadToken();
    if (token) saveAuth(token, profile);
    setUser(profile);
  }, []);

  const removeAvatarFn = useCallback(async () => {
    const profile = await deleteAvatar();
    const token = loadToken();
    if (token) saveAuth(token, profile);
    setUser(profile);
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      login,
      register,
      logout,
      refreshUser,
      updateDisplayName,
      uploadAvatar: uploadAvatarFn,
      removeAvatar: removeAvatarFn,
    }),
    [user, loading, login, register, logout, refreshUser, updateDisplayName, uploadAvatarFn, removeAvatarFn],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
