import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import MemberAvatar from "../components/MemberAvatar";
import TeamAvatar from "../components/TeamAvatar";
import {
  chatErrorMessage,
  fetchConversations,
  fetchMessages,
  markChatRead,
  previewText,
  sendMessage,
} from "../lib/chat";
import type { ChatConversationItem, ChatMessageItem, TeamDetail, UserPublicItem } from "../lib/types";
import { formatTs } from "../lib/types";
import {
  createTeam,
  deleteTeamAvatar,
  disbandTeam,
  fetchTeam,
  inviteToTeam,
  kickMember,
  leaveTeam,
  lookupUsers,
  teamErrorMessage,
  uploadTeamAvatar,
} from "../lib/team";

type Props = {
  onActivity: (msg: string) => void;
  refreshKey?: number;
};

export default function TeamTab({ onActivity, refreshKey = 0 }: Props) {
  const { user } = useAuth();
  const [conversations, setConversations] = useState<ChatConversationItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<TeamDetail | null>(null);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [messagesTeamId, setMessagesTeamId] = useState<string | null>(null);
  const [input, setInput] = useState("");
  const [conversationsLoading, setConversationsLoading] = useState(false);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [totalUnread, setTotalUnread] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState("");
  const [newDesc, setNewDesc] = useState("");
  const [showManage, setShowManage] = useState(false);
  const [inviteQuery, setInviteQuery] = useState("");
  const [suggestions, setSuggestions] = useState<UserPublicItem[]>([]);
  const [avatarKey, setAvatarKey] = useState(0);

  const avatarInputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const loadSeqRef = useRef(0);
  const messagesRef = useRef<ChatMessageItem[]>([]);
  const selectedIdRef = useRef<string | null>(null);
  const messagesTeamIdRef = useRef<string | null>(null);

  messagesRef.current = messages;
  selectedIdRef.current = selectedId;
  messagesTeamIdRef.current = messagesTeamId;

  const selected = conversations.find((c) => c.team_id === selectedId) ?? null;
  const visibleMessages = messagesTeamId === selectedId ? messages : [];
  const isOwner = detail && user && detail.owner_id === user.id;

  const scrollToBottom = useCallback(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, []);

  const applyUnreadCleared = useCallback((teamId: string) => {
    setConversations((prev) => {
      const conv = prev.find((c) => c.team_id === teamId);
      const cleared = conv?.unread_count ?? 0;
      if (cleared > 0) setTotalUnread((n) => Math.max(0, n - cleared));
      return prev.map((c) => (c.team_id === teamId ? { ...c, unread_count: 0 } : c));
    });
  }, []);

  const loadConversations = useCallback(async () => {
    try {
      const data = await fetchConversations();
      setConversations(data.items);
      setTotalUnread(data.total_unread);
      setSelectedId((prev) => {
        if (prev && data.items.some((c) => c.team_id === prev)) return prev;
        return data.items[0]?.team_id ?? null;
      });
    } catch (e) {
      setErr(chatErrorMessage(e));
    }
  }, []);

  const loadDetail = useCallback(async (teamId: string) => {
    setDetailLoading(true);
    try {
      setDetail(await fetchTeam(teamId));
    } catch (e) {
      setErr(teamErrorMessage(e));
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!user) return;
    setConversationsLoading(true);
    loadConversations().finally(() => setConversationsLoading(false));
    const t = window.setInterval(loadConversations, 15_000);
    return () => window.clearInterval(t);
  }, [user, loadConversations, refreshKey]);

  useEffect(() => {
    if (!selectedId || !user) {
      setDetail(null);
      setShowManage(false);
      return;
    }
    void loadDetail(selectedId);
  }, [selectedId, user, loadDetail, refreshKey]);

  useEffect(() => {
    if (!selectedId || !user) {
      setMessages([]);
      setMessagesTeamId(null);
      return;
    }

    const teamId = selectedId;
    const seq = ++loadSeqRef.current;

    setMessages([]);
    setMessagesTeamId(null);
    setMessagesLoading(true);
    setErr("");

    void (async () => {
      try {
        const data = await fetchMessages(teamId, { limit: 50 });
        if (loadSeqRef.current !== seq) return;
        setMessages(data.items);
        setMessagesTeamId(teamId);
        await markChatRead(teamId);
        if (loadSeqRef.current !== seq) return;
        applyUnreadCleared(teamId);
      } catch (e) {
        if (loadSeqRef.current === seq) setErr(chatErrorMessage(e));
      } finally {
        if (loadSeqRef.current === seq) setMessagesLoading(false);
      }
    })();
  }, [selectedId, user, applyUnreadCleared]);

  useEffect(() => {
    if (!selectedId || !user) return;
    const teamId = selectedId;
    const poll = window.setInterval(() => {
      const activeTeam = selectedIdRef.current;
      if (!activeTeam || activeTeam !== teamId || messagesTeamIdRef.current !== teamId) return;
      const last = messagesRef.current[messagesRef.current.length - 1];
      const seq = loadSeqRef.current;
      void fetchMessages(teamId, { since: last?.created_at })
        .then(async (data) => {
          if (loadSeqRef.current !== seq || selectedIdRef.current !== teamId) return;
          if (!data.items.length) return;
          setMessages((prev) => {
            const ids = new Set(prev.map((m) => m.id));
            const merged = [...prev];
            for (const m of data.items) {
              if (!ids.has(m.id)) merged.push(m);
            }
            return merged.sort(
              (a, b) => new Date(a.created_at).getTime() - new Date(b.created_at).getTime(),
            );
          });
          await markChatRead(teamId);
          if (selectedIdRef.current === teamId) applyUnreadCleared(teamId);
        })
        .catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(poll);
  }, [selectedId, user, applyUnreadCleared]);

  useEffect(() => {
    if (!messagesLoading && visibleMessages.length > 0) scrollToBottom();
  }, [visibleMessages, messagesLoading, scrollToBottom]);

  useEffect(() => {
    const q = inviteQuery.trim();
    if (q.length < 2) {
      setSuggestions([]);
      return;
    }
    const t = window.setTimeout(() => {
      lookupUsers(q)
        .then(setSuggestions)
        .catch(() => setSuggestions([]));
    }, 300);
    return () => window.clearTimeout(t);
  }, [inviteQuery]);

  const handleSelectTeam = (teamId: string) => {
    if (teamId === selectedId) return;
    setSelectedId(teamId);
    setInput("");
    setShowManage(false);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const name = newName.trim();
    if (!name) return;
    setBusy(true);
    setErr("");
    try {
      const team = await createTeam({ name, description: newDesc.trim() });
      setShowCreate(false);
      setNewName("");
      setNewDesc("");
      await loadConversations();
      setSelectedId(team.id);
      onActivity(`已创建小队「${team.name}」`);
    } catch (ex) {
      setErr(teamErrorMessage(ex));
    } finally {
      setBusy(false);
    }
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    const text = input.trim();
    if (!text || !selectedId || sending || messagesTeamId !== selectedId) return;
    setSending(true);
    setErr("");
    try {
      const msg = await sendMessage(selectedId, text);
      if (messagesTeamId === selectedId) setMessages((prev) => [...prev, msg]);
      setInput("");
      onActivity(`已发送到「${selected?.team_name ?? "小队"}」`);
      await loadConversations();
      scrollToBottom();
    } catch (ex) {
      setErr(chatErrorMessage(ex));
    } finally {
      setSending(false);
    }
  };

  const handleInvite = async (query?: string) => {
    if (!selectedId) return;
    const q = (query ?? inviteQuery).trim();
    if (!q) return;
    setBusy(true);
    setErr("");
    try {
      await inviteToTeam(selectedId, q);
      setInviteQuery("");
      setSuggestions([]);
      onActivity(`已向 ${q} 发送邀请`);
    } catch (ex) {
      setErr(teamErrorMessage(ex));
    } finally {
      setBusy(false);
    }
  };

  const handleKick = async (memberId: string, name: string) => {
    if (!selectedId || !window.confirm(`确定将 ${name} 移出小队？`)) return;
    setBusy(true);
    try {
      await kickMember(selectedId, memberId);
      await loadDetail(selectedId);
      await loadConversations();
      onActivity(`已将 ${name} 移出小队`);
    } catch (ex) {
      setErr(teamErrorMessage(ex));
    } finally {
      setBusy(false);
    }
  };

  const handleLeave = async () => {
    if (!selectedId || !detail || !window.confirm(`确定离开「${detail.name}」？`)) return;
    setBusy(true);
    try {
      await leaveTeam(selectedId);
      setSelectedId(null);
      setDetail(null);
      setShowManage(false);
      await loadConversations();
      onActivity(`已离开小队「${detail.name}」`);
    } catch (ex) {
      setErr(teamErrorMessage(ex));
    } finally {
      setBusy(false);
    }
  };

  const handleDisband = async () => {
    if (!selectedId || !detail || !window.confirm(`确定解散「${detail.name}」？此操作不可撤销。`)) return;
    setBusy(true);
    try {
      await disbandTeam(selectedId);
      setSelectedId(null);
      setDetail(null);
      setShowManage(false);
      await loadConversations();
      onActivity(`已解散小队「${detail.name}」`);
    } catch (ex) {
      setErr(teamErrorMessage(ex));
    } finally {
      setBusy(false);
    }
  };

  const handleTeamAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file || !selectedId) return;
    setBusy(true);
    setErr("");
    try {
      const updated = await uploadTeamAvatar(selectedId, file);
      setDetail(updated);
      setAvatarKey((k) => k + 1);
      await loadConversations();
      onActivity(`已更新「${updated.name}」群头像`);
    } catch (ex) {
      setErr(teamErrorMessage(ex));
    } finally {
      setBusy(false);
    }
  };

  const handleRemoveTeamAvatar = async () => {
    if (!selectedId || !detail?.has_avatar) return;
    if (!window.confirm("确定恢复为成员拼图头像？")) return;
    setBusy(true);
    setErr("");
    try {
      const updated = await deleteTeamAvatar(selectedId);
      setDetail(updated);
      setAvatarKey((k) => k + 1);
      await loadConversations();
      onActivity(`已恢复「${updated.name}」默认拼图头像`);
    } catch (ex) {
      setErr(teamErrorMessage(ex));
    } finally {
      setBusy(false);
    }
  };

  const memberAvatarsFor = (teamId: string) => {
    if (detail?.id === teamId) {
      return detail.members.map((m) => ({
        user_id: m.user_id,
        display_name: m.display_name,
        has_avatar: Boolean(m.has_avatar),
      }));
    }
    const conv = conversations.find((c) => c.team_id === teamId);
    return conv?.member_avatars ?? [];
  };

  if (!user) {
    return (
      <div className="chat-empty neo-card">
        <p>请先登录后使用组队与群聊。</p>
        <p className="muted">在右上角头像菜单中登录或注册。</p>
      </div>
    );
  }

  return (
    <div className="chat-layout team-chat-layout">
      <aside className="chat-sidebar neo-card">
        <div className="chat-sidebar-head">
          <h2>我的小队</h2>
          <div className="team-sidebar-actions">
            {totalUnread > 0 ? <span className="chat-unread-pill">{totalUnread}</span> : null}
            <button type="button" className="btn primary sm" onClick={() => setShowCreate((v) => !v)}>
              {showCreate ? "取消" : "+ 创建"}
            </button>
          </div>
        </div>

        {showCreate ? (
          <form className="team-create-form compact" onSubmit={handleCreate}>
            <div className="field">
              <label htmlFor="team-name">小队名称</label>
              <input
                id="team-name"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                placeholder="例如：周末探险队"
                maxLength={64}
                required
              />
            </div>
            <div className="field">
              <label htmlFor="team-desc">简介（可选）</label>
              <input
                id="team-desc"
                value={newDesc}
                onChange={(e) => setNewDesc(e.target.value)}
                placeholder="一句话介绍"
                maxLength={500}
              />
            </div>
            <button type="submit" className="btn secondary" disabled={busy}>
              确认创建
            </button>
          </form>
        ) : null}

        {conversationsLoading && conversations.length === 0 ? (
          <p className="muted chat-sidebar-hint">加载中…</p>
        ) : null}
        {!conversationsLoading && conversations.length === 0 ? (
          <p className="muted chat-sidebar-hint">还没有小队，点击「创建」开始组队与群聊。</p>
        ) : null}

        <ul className="chat-conv-list">
          {conversations.map((c) => (
            <li key={c.team_id}>
              <button
                type="button"
                className={`chat-conv-item${selectedId === c.team_id ? " active" : ""}`}
                onClick={() => handleSelectTeam(c.team_id)}
              >
                <TeamAvatar
                  teamId={c.team_id}
                  teamName={c.team_name}
                  hasAvatar={Boolean(c.has_avatar)}
                  memberAvatars={c.member_avatars}
                  cacheKey={avatarKey}
                />
                <span className="chat-conv-body">
                  <span className="chat-conv-top">
                    <span className="chat-conv-name">{c.team_name}</span>
                    {c.last_message ? (
                      <span className="chat-conv-time muted">{formatTs(c.last_message.created_at)}</span>
                    ) : null}
                  </span>
                  <span className="chat-conv-preview muted">
                    {c.last_message
                      ? `${c.last_message.is_mine ? "我: " : ""}${previewText(c.last_message.body)}`
                      : `${c.member_count} 人 · 暂无消息`}
                  </span>
                </span>
                {c.unread_count > 0 ? (
                  <span className="chat-badge">{c.unread_count > 99 ? "99+" : c.unread_count}</span>
                ) : null}
              </button>
            </li>
          ))}
        </ul>
      </aside>

      <section className="chat-panel neo-card">
        {selected ? (
          <>
            <header className="chat-panel-head team-chat-head">
              <div className="team-chat-head-main">
                <TeamAvatar
                  teamId={selected.team_id}
                  teamName={selected.team_name}
                  hasAvatar={Boolean(selected.has_avatar ?? detail?.has_avatar)}
                  memberAvatars={memberAvatarsFor(selected.team_id)}
                  cacheKey={avatarKey}
                />
                <div>
                  <h2>{selected.team_name}</h2>
                  <p className="muted">{selected.member_count} 人 · 组队群聊</p>
                </div>
              </div>
              <button
                type="button"
                className={`btn secondary sm${showManage ? " active" : ""}`}
                onClick={() => setShowManage((v) => !v)}
              >
                {showManage ? "返回聊天" : "小队管理"}
              </button>
            </header>

            {err ? <div className="chat-err">{err}</div> : null}

            {showManage ? (
              <div className="team-manage-panel">
                {detailLoading || !detail ? (
                  <p className="muted chat-empty-hint">加载小队信息…</p>
                ) : (
                  <>
                    {isOwner ? (
                      <div className="team-avatar-settings">
                        <h3>群头像</h3>
                        <div className="team-avatar-settings-row">
                          <TeamAvatar
                            teamId={detail.id}
                            teamName={detail.name}
                            hasAvatar={Boolean(detail.has_avatar)}
                            memberAvatars={memberAvatarsFor(detail.id)}
                            size="md"
                            cacheKey={avatarKey}
                          />
                          <div className="team-avatar-settings-actions">
                            <button
                              type="button"
                              className="btn secondary sm"
                              disabled={busy}
                              onClick={() => avatarInputRef.current?.click()}
                            >
                              更换头像
                            </button>
                            {detail.has_avatar ? (
                              <button
                                type="button"
                                className="btn ghost sm"
                                disabled={busy}
                                onClick={() => void handleRemoveTeamAvatar()}
                              >
                                恢复拼图
                              </button>
                            ) : null}
                            <p className="muted small">未设置时将按成员头像自动拼图（微信风格）</p>
                          </div>
                        </div>
                        <input
                          ref={avatarInputRef}
                          type="file"
                          accept="image/jpeg,image/png,image/webp"
                          hidden
                          onChange={(e) => void handleTeamAvatarChange(e)}
                        />
                      </div>
                    ) : null}

                    {detail.description ? <p className="muted">{detail.description}</p> : null}
                    <p className="muted team-meta">
                      {detail.member_count} / 20 人 · 创建于 {formatTs(detail.created_at)}
                    </p>

                    {isOwner ? (
                      <div className="team-invite compact">
                        <h3>邀请成员</h3>
                        <div className="team-invite-row">
                          <input
                            type="text"
                            value={inviteQuery}
                            onChange={(e) => setInviteQuery(e.target.value)}
                            placeholder="UUID / username / 昵称"
                            onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), handleInvite())}
                          />
                          <button
                            type="button"
                            className="btn primary"
                            disabled={busy || !inviteQuery.trim()}
                            onClick={() => handleInvite()}
                          >
                            邀请
                          </button>
                        </div>
                        {suggestions.length ? (
                          <ul className="team-suggestions">
                            {suggestions.map((u) => (
                              <li key={u.id}>
                                <button
                                  type="button"
                                  className="team-suggestion-btn"
                                  onClick={() => handleInvite(u.username)}
                                >
                                  <strong>{u.display_name}</strong>
                                  <span className="muted">@{u.username}</span>
                                </button>
                              </li>
                            ))}
                          </ul>
                        ) : null}
                      </div>
                    ) : null}

                    <div className="team-members compact">
                      <h3>成员列表</h3>
                      <ul className="team-member-list">
                        {detail.members.map((m) => (
                          <li key={m.user_id} className="team-member-row">
                            <div className="team-member-info">
                              <MemberAvatar
                                teamId={detail.id}
                                user={{
                                  id: m.user_id,
                                  display_name: m.display_name,
                                  has_avatar: Boolean(m.has_avatar),
                                }}
                                size="sm"
                              />
                              <div>
                                <strong>{m.display_name}</strong>
                                <span className="muted"> @{m.username}</span>
                                {m.role === "owner" ? <span className="tag">队长</span> : null}
                              </div>
                            </div>
                            {isOwner && m.user_id !== user?.id ? (
                              <button
                                type="button"
                                className="btn ghost danger-text"
                                disabled={busy}
                                onClick={() => handleKick(m.user_id, m.display_name)}
                              >
                                踢出
                              </button>
                            ) : null}
                          </li>
                        ))}
                      </ul>
                    </div>

                    <div className="team-manage-actions">
                      {!isOwner ? (
                        <button type="button" className="btn secondary" disabled={busy} onClick={handleLeave}>
                          离开小队
                        </button>
                      ) : (
                        <button type="button" className="btn danger" disabled={busy} onClick={handleDisband}>
                          解散小队
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>
            ) : (
              <>
                <div className="chat-messages" ref={listRef} key={selectedId}>
                  {messagesLoading ? (
                    <p className="muted chat-empty-hint">加载消息中…</p>
                  ) : visibleMessages.length === 0 ? (
                    <p className="muted chat-empty-hint">还没有消息，发一句打个招呼吧</p>
                  ) : (
                    visibleMessages.map((m) => (
                      <div key={m.id} className={`chat-row${m.is_mine ? " mine" : ""}`}>
                        {!m.is_mine ? (
                          <MemberAvatar teamId={selectedId!} user={m.sender} size="sm" />
                        ) : null}
                        <div className="chat-bubble-wrap">
                          {!m.is_mine ? (
                            <span className="chat-sender-name muted">{m.sender.display_name}</span>
                          ) : null}
                          <div className={`chat-bubble${m.is_mine ? " mine" : ""}`}>{m.body}</div>
                          <span className="chat-msg-time muted">{formatTs(m.created_at)}</span>
                        </div>
                      </div>
                    ))
                  )}
                </div>

                <form className="chat-compose" onSubmit={handleSend}>
                  <textarea
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                    rows={2}
                    maxLength={2000}
                    disabled={messagesLoading}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && !e.shiftKey) {
                        e.preventDefault();
                        void handleSend(e);
                      }
                    }}
                  />
                  <button
                    type="submit"
                    className="btn primary"
                    disabled={sending || messagesLoading || !input.trim()}
                  >
                    {sending ? "发送中…" : "发送"}
                  </button>
                </form>
              </>
            )}
          </>
        ) : (
          <div className="chat-empty">
            <p>选择或创建一个小队，开始群聊</p>
          </div>
        )}
      </section>
    </div>
  );
}
