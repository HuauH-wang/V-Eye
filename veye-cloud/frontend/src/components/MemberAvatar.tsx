import { useAuthenticatedImage } from "../hooks/useAuthenticatedImage";
import { avatarColor, avatarInitial } from "../lib/avatarCollage";
import type { UserPublicItem } from "../lib/types";

type Props = {
  teamId: string;
  user: Pick<UserPublicItem, "id" | "display_name" | "has_avatar">;
  size?: "sm" | "md";
  fill?: boolean;
  className?: string;
};

export default function MemberAvatar({ teamId, user, size = "md", fill = false, className = "" }: Props) {
  const path = user.has_avatar ? `/teams/${teamId}/members/${user.id}/avatar` : "";
  const { url, failed } = useAuthenticatedImage(path);

  const initial = avatarInitial(user.display_name);
  const showImage = Boolean(url && !failed);
  const sizeClass = fill ? " fill" : size === "sm" ? " sm" : "";

  return (
    <span
      className={`chat-avatar member-avatar${sizeClass} ${className}`.trim()}
      style={!showImage ? { background: avatarColor(user.display_name) } : undefined}
      aria-hidden
    >
      {showImage ? <img src={url!} alt={user.display_name} /> : initial}
    </span>
  );
}
