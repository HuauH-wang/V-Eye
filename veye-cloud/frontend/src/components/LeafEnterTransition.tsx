import { useEffect } from "react";
import { useAuth } from "../context/AuthContext";
import UserAvatar from "./UserAvatar";

type Props = {
  onComplete: () => void;
};

const LEAF_TEXTURE = `${import.meta.env.BASE_URL}auth-collage/leaf-macro.jpg`;

const LEAVES = [
  { id: "l1", side: "left", delay: 0 },
  { id: "l2", side: "left", delay: 0.06 },
  { id: "l3", side: "right", delay: 0.04 },
  { id: "l4", side: "right", delay: 0.1 },
  { id: "l5", side: "left", delay: 0.14 },
  { id: "l6", side: "right", delay: 0.08 },
] as const;

export default function LeafEnterTransition({ onComplete }: Props) {
  const { user } = useAuth();

  useEffect(() => {
    const t = window.setTimeout(onComplete, 2200);
    return () => window.clearTimeout(t);
  }, [onComplete]);

  if (!user) return null;

  return (
    <div
      className="leaf-enter"
      style={{ ["--leaf-texture" as string]: `url("${LEAF_TEXTURE}") center/cover` }}
      role="status"
      aria-live="polite"
      aria-label={`${user.display_name} 正在进入观测控制台`}
    >
      <div className="leaf-enter-glow" aria-hidden />
      <div className="leaf-enter-hero">
        <UserAvatar user={user} className="leaf-enter-avatar" aria-hidden={false} />
      </div>
      {LEAVES.map((leaf) => (
        <div
          key={leaf.id}
          className={`leaf-enter-panel leaf-enter-${leaf.side}`}
          style={{ animationDelay: `${leaf.delay}s` }}
          aria-hidden
        />
      ))}
      <p className="leaf-enter-caption">正在进入观测控制台…</p>
    </div>
  );
}
