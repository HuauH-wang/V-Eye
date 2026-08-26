import { useAuthenticatedImage } from "../hooks/useAuthenticatedImage";
import {
  avatarColor,
  avatarInitial,
  collageLayout,
  pickCollageMembers,
} from "../lib/avatarCollage";
import type { MemberAvatarItem } from "../lib/types";
import MemberAvatar from "./MemberAvatar";

type Props = {
  teamId: string;
  teamName: string;
  hasAvatar?: boolean;
  memberAvatars?: MemberAvatarItem[];
  size?: "sm" | "md";
  cacheKey?: number;
  className?: string;
};

function CollageCell({
  teamId,
  member,
  layout,
  index,
}: {
  teamId: string;
  member: MemberAvatarItem;
  layout: ReturnType<typeof collageLayout>;
  index: number;
}) {
  const cell = layout.cells[index];
  if (!cell) return null;

  return (
    <div
      className="team-avatar-collage-cell"
      style={{
        gridColumn: cell.colSpan ? `${cell.col} / span ${cell.colSpan}` : String(cell.col),
        gridRow: cell.rowSpan ? `${cell.row} / span ${cell.rowSpan}` : String(cell.row),
      }}
    >
      <MemberAvatar
        teamId={teamId}
        user={{
          id: member.user_id,
          display_name: member.display_name,
          has_avatar: member.has_avatar,
        }}
        fill
        className="team-avatar-collage-member"
      />
    </div>
  );
}

export default function TeamAvatar({
  teamId,
  teamName,
  hasAvatar = false,
  memberAvatars = [],
  size = "md",
  cacheKey = 0,
  className = "",
}: Props) {
  const customPath = hasAvatar ? `/teams/${teamId}/avatar` : "";
  const { url, failed } = useAuthenticatedImage(customPath, cacheKey);
  const showCustom = Boolean(hasAvatar && url && !failed);

  const members = pickCollageMembers(memberAvatars);
  const layout = members.length > 0 ? collageLayout(members.length) : null;

  if (showCustom) {
    return (
      <span className={`chat-avatar team-avatar custom${size === "sm" ? " sm" : ""} ${className}`.trim()} aria-hidden>
        <img src={url!} alt={teamName} />
      </span>
    );
  }

  if (layout && members.length > 0) {
    return (
      <span
        className={`chat-avatar team-avatar collage n-${members.length}${size === "sm" ? " sm" : ""} ${className}`.trim()}
        aria-hidden
      >
        <span
          className="team-avatar-collage"
          style={{
            gridTemplateColumns: `repeat(${layout.cols}, 1fr)`,
            gridTemplateRows: `repeat(${layout.rows}, 1fr)`,
          }}
        >
          {members.map((member, index) => (
            <CollageCell key={member.user_id} teamId={teamId} member={member} layout={layout} index={index} />
          ))}
        </span>
      </span>
    );
  }

  const initial = avatarInitial(teamName);
  return (
    <span
      className={`chat-avatar team-avatar fallback${size === "sm" ? " sm" : ""} ${className}`.trim()}
      style={{ background: avatarColor(teamName) }}
      aria-hidden
    >
      {initial}
    </span>
  );
}
