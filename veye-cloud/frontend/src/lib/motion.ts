import { apiFetch } from "./api";

export type CompanionState = {
  name: string;
  mood: string;
  level: number;
  energy: number;
  total_steps: number;
  message: string;
  pose_json: Record<string, unknown>;
  updated_at?: string | null;
};

export type MotionSession = {
  id: string;
  status: string;
  started_at: string;
  ended_at?: string | null;
  steps: number;
  fall_count: number;
  pose_score: number;
  summary_json: Record<string, unknown>;
  created_at: string;
  updated_at: string;
};

export async function fetchCompanion(): Promise<CompanionState> {
  const res = await apiFetch("/companion/xiaoou");
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export async function fetchMotionSessions(limit = 20): Promise<{ items: MotionSession[]; total: number }> {
  const res = await apiFetch(`/motion/sessions?limit=${limit}`);
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}
