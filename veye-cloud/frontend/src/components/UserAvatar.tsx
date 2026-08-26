import { useEffect, useState } from "react";
import { apiFetch } from "../lib/api";
import type { UserProfile } from "../lib/types";

type Props = {
  user: UserProfile;
  size?: "sm" | "md" | "lg";
  className?: string;
  cacheKey?: number;
  "aria-hidden"?: boolean;
};

export default function UserAvatar({
  user,
  size = "md",
  className = "",
  cacheKey = 0,
  "aria-hidden": ariaHidden = true,
}: Props) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!user.has_avatar) {
      setUrl(null);
      return;
    }
    let objectUrl: string | null = null;
    apiFetch("/auth/me/avatar")
      .then((res) => {
        if (!res.ok) throw new Error("avatar load failed");
        return res.blob();
      })
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => setUrl(null));
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [user.has_avatar, user.id, cacheKey]);

  const initial = user.display_name.slice(0, 1).toUpperCase() || "?";

  return (
    <span
      className={`user-avatar user-avatar-${size} ${className}`.trim()}
      aria-hidden={ariaHidden}
    >
      {url ? <img src={url} alt={user.display_name} /> : initial}
    </span>
  );
}
