import type { MemberAvatarItem } from "./types";

export type CollageCell = {
  col: number;
  row: number;
  colSpan?: number;
  rowSpan?: number;
};

export type CollageLayout = {
  cols: number;
  rows: number;
  cells: CollageCell[];
};

/** 微信风格群头像拼图布局（最多 9 人） */
export function collageLayout(count: number): CollageLayout {
  const n = Math.min(Math.max(count, 1), 9);
  switch (n) {
    case 1:
      return { cols: 1, rows: 1, cells: [{ col: 1, row: 1 }] };
    case 2:
      return {
        cols: 2,
        rows: 1,
        cells: [
          { col: 1, row: 1 },
          { col: 2, row: 1 },
        ],
      };
    case 3:
      return {
        cols: 2,
        rows: 2,
        cells: [
          { col: 1, row: 1 },
          { col: 2, row: 1 },
          { col: 1, row: 2, colSpan: 2 },
        ],
      };
    case 4:
      return {
        cols: 2,
        rows: 2,
        cells: [
          { col: 1, row: 1 },
          { col: 2, row: 1 },
          { col: 1, row: 2 },
          { col: 2, row: 2 },
        ],
      };
    case 5:
      return {
        cols: 6,
        rows: 2,
        cells: [
          { col: 1, row: 1, colSpan: 3 },
          { col: 4, row: 1, colSpan: 3 },
          { col: 1, row: 2, colSpan: 2 },
          { col: 3, row: 2, colSpan: 2 },
          { col: 5, row: 2, colSpan: 2 },
        ],
      };
    case 6:
      return {
        cols: 3,
        rows: 2,
        cells: [
          { col: 1, row: 1 },
          { col: 2, row: 1 },
          { col: 3, row: 1 },
          { col: 1, row: 2 },
          { col: 2, row: 2 },
          { col: 3, row: 2 },
        ],
      };
    case 7:
      return {
        cols: 3,
        rows: 3,
        cells: [
          { col: 1, row: 1 },
          { col: 2, row: 1 },
          { col: 3, row: 1 },
          { col: 1, row: 2 },
          { col: 2, row: 2 },
          { col: 3, row: 2 },
          { col: 2, row: 3 },
        ],
      };
    case 8:
      return {
        cols: 3,
        rows: 3,
        cells: [
          { col: 1, row: 1 },
          { col: 2, row: 1 },
          { col: 3, row: 1 },
          { col: 1, row: 2 },
          { col: 2, row: 2 },
          { col: 3, row: 2 },
          { col: 1, row: 3 },
          { col: 2, row: 3 },
        ],
      };
    default:
      return {
        cols: 3,
        rows: 3,
        cells: Array.from({ length: 9 }, (_, i) => ({
          col: (i % 3) + 1,
          row: Math.floor(i / 3) + 1,
        })),
      };
  }
}

const AVATAR_COLORS = ["#40916c", "#577590", "#f4a261", "#e76f51", "#90be6d", "#6a4c93", "#43aa8b"];

export function avatarInitial(name: string): string {
  const t = name.trim();
  return t ? t.slice(0, 1).toUpperCase() : "?";
}

export function avatarColor(name: string): string {
  let hash = 0;
  for (const ch of name) hash = (hash + ch.charCodeAt(0)) % AVATAR_COLORS.length;
  return AVATAR_COLORS[hash] ?? AVATAR_COLORS[0];
}

export function pickCollageMembers(members: MemberAvatarItem[], limit = 9): MemberAvatarItem[] {
  return members.slice(0, limit);
}
