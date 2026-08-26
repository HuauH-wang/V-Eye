import { useCallback, useEffect, useState } from "react";
import type { MailItem } from "../lib/types";
import { formatTs } from "../lib/types";
import {
  MAIL_TYPE_LABELS,
  acceptMailInvite,
  declineMailInvite,
  deleteMail,
  fetchMail,
  markMailRead,
  teamErrorMessage,
} from "../lib/team";

type Props = {
  open: boolean;
  onClose: () => void;
  onUnreadChange?: (count: number) => void;
  onTeamChange?: () => void;
};

export default function MailInbox({ open, onClose, onUnreadChange, onTeamChange }: Props) {
  const [items, setItems] = useState<MailItem[]>([]);
  const [selected, setSelected] = useState<MailItem | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [filter, setFilter] = useState<"all" | "unread">("all");

  const load = useCallback(async () => {
    setLoading(true);
    setErr("");
    try {
      const data = await fetchMail(filter === "unread");
      setItems(data.items);
      onUnreadChange?.(data.unread_count);
      setSelected((prev) => {
        if (!prev) return data.items[0] ?? null;
        return data.items.find((m) => m.id === prev.id) ?? data.items[0] ?? null;
      });
    } catch (e) {
      setErr(teamErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [filter, onUnreadChange]);

  useEffect(() => {
    if (open) load();
  }, [open, load]);

  const openMail = async (mail: MailItem) => {
    setSelected(mail);
    if (!mail.is_read) {
      try {
        const updated = await markMailRead(mail.id);
        setItems((list) => list.map((m) => (m.id === updated.id ? updated : m)));
        onUnreadChange?.(Math.max(0, items.filter((m) => !m.is_read && m.id !== mail.id).length));
      } catch {
        /* ignore */
      }
    }
  };

  const runAction = async (action: "accept" | "decline" | "delete") => {
    if (!selected) return;
    setBusy(true);
    setErr("");
    try {
      if (action === "accept") {
        await acceptMailInvite(selected.id);
        onTeamChange?.();
      } else if (action === "decline") {
        await declineMailInvite(selected.id);
      } else {
        await deleteMail(selected.id);
      }
      await load();
    } catch (e) {
      setErr(teamErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (!open) return null;

  return (
    <div className="mail-overlay" role="dialog" aria-modal="true" aria-label="邮件">
      <div className="mail-panel neo-card">
        <header className="mail-head">
          <h2>邮件</h2>
          <div className="mail-head-actions">
            <button
              type="button"
              className={`chip-btn${filter === "all" ? " active" : ""}`}
              onClick={() => setFilter("all")}
            >
              全部
            </button>
            <button
              type="button"
              className={`chip-btn${filter === "unread" ? " active" : ""}`}
              onClick={() => setFilter("unread")}
            >
              未读
            </button>
            <button type="button" className="btn ghost" onClick={onClose}>
              关闭
            </button>
          </div>
        </header>

        {err ? (
          <div className="alert inline" role="alert">
            {err}
          </div>
        ) : null}

        <div className="mail-body">
          <ul className="mail-list">
            {loading ? <li className="muted mail-empty">加载中…</li> : null}
            {!loading && !items.length ? (
              <li className="muted mail-empty">暂无邮件</li>
            ) : null}
            {items.map((mail) => (
              <li key={mail.id}>
                <button
                  type="button"
                  className={`mail-row${selected?.id === mail.id ? " selected" : ""}${mail.is_read ? "" : " unread"}`}
                  onClick={() => openMail(mail)}
                >
                  <span className="mail-type-tag">{MAIL_TYPE_LABELS[mail.mail_type] ?? mail.mail_type}</span>
                  <strong>{mail.title}</strong>
                  <span className="muted">{formatTs(mail.created_at)}</span>
                </button>
              </li>
            ))}
          </ul>

          <article className="mail-detail neo-card">
            {selected ? (
              <>
                <h3>{selected.title}</h3>
                <p className="muted mail-meta">
                  {selected.sender
                    ? `来自 ${selected.sender.display_name} (@${selected.sender.username}) · `
                    : ""}
                  {formatTs(selected.created_at)}
                </p>
                <p>{selected.body}</p>
                <div className="mail-actions">
                  {selected.mail_type === "team_invite" && selected.action_status === "pending" ? (
                    <>
                      <button
                        type="button"
                        className="btn primary"
                        disabled={busy}
                        onClick={() => runAction("accept")}
                      >
                        接受邀请
                      </button>
                      <button
                        type="button"
                        className="btn secondary"
                        disabled={busy}
                        onClick={() => runAction("decline")}
                      >
                        拒绝
                      </button>
                    </>
                  ) : null}
                  {selected.action_status && selected.action_status !== "pending" ? (
                    <span className="tag">
                      已{selected.action_status === "accepted" ? "接受" : "拒绝"}
                    </span>
                  ) : null}
                  <button
                    type="button"
                    className="btn ghost"
                    disabled={busy}
                    onClick={() => runAction("delete")}
                  >
                    删除
                  </button>
                </div>
              </>
            ) : (
              <p className="muted">选择一封邮件查看详情</p>
            )}
          </article>
        </div>
      </div>
    </div>
  );
}
