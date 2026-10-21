"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import EmojiPicker from "@/components/EmojiPicker";
import { api, jsonOrThrow, logout } from "@/lib/api";
import { cacheDelete, cacheMessages, loadCached } from "@/lib/cache";
import { compressPhoto, maybeGzip } from "@/lib/compress";
import { avatarColor, dayChipLabel, lastSeenText, sameMessageDay, shortTime } from "@/lib/format";
import { chatInForeground, playIncoming, setTabAlert, unlockAudio } from "@/lib/notify";
import { getAccess, getTheme, setTheme as persistTheme } from "@/lib/storage";

type User = {
  id: number;
  login: string;
  avatar_url?: string | null;
  last_seen?: string;
  online?: boolean;
  nick?: string | null;
  display_name?: string;
};

type Attachment = {
  id: number;
  kind: string;
  orig_name: string;
  mime: string;
  size: number;
  gzip: boolean;
  url: string;
  preview_url?: string | null;
  duration_ms?: number | null;
};

type Msg = {
  id: number;
  dialog_id: number;
  author_id: number;
  type: string;
  text?: string | null;
  reply_to?: { id: number; text?: string | null; type: string } | null;
  edited_at?: string | null;
  created_at: string;
  ticks: string;
  attachments: Attachment[];
};

type DialogRow = {
  id: number;
  peer: User;
  last_message: string;
  last_at?: string | null;
  unread: number;
  typing?: boolean;
};

/**
 * Превью для списка диалогов.
 * @param msg сообщение
 */
function preview(msg: Msg): string {
  if (msg.type === "photo") return "Фото";
  if (msg.type === "video") return "Видео";
  if (msg.type === "file") return msg.attachments[0]?.orig_name || "Файл";
  if (msg.type === "album") return "Альбом";
  return (msg.text || "").trim();
}

/**
 * Одно эмодзи без другого текста — крупнее, как в Telegram.
 */
function isSoloEmoji(text: string): boolean {
  const t = text.trim();
  if (!t || /\s/.test(t)) return false;
  try {
    const segs = [...new Intl.Segmenter(undefined, { granularity: "grapheme" }).segment(t)];
    if (segs.length !== 1) return false;
    return /\p{Extended_Pictographic}/u.test(segs[0].segment);
  } catch {
    return false;
  }
}

/**
 * Привести id из JSON к числу — иначе сравнение с active не сходится.
 */
function normalizeMsg(raw: Msg): Msg {
  return { ...raw, id: Number(raw.id), dialog_id: Number(raw.dialog_id), author_id: Number(raw.author_id) };
}

/**
 * Слить пачки сообщений по id, порядок по возрастанию.
 */
function mergeMessages(...batches: Msg[][]): Msg[] {
  const byId = new Map<number, Msg>();
  for (const batch of batches) {
    for (const m of batch) byId.set(m.id, m);
  }
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

/**
 * Веб-клиент: список слева, переписка справа, без калькулятора.
 */
export default function ChatApp() {
  const router = useRouter();
  const [me, setMe] = useState<User | null>(null);
  const [dialogs, setDialogs] = useState<DialogRow[]>([]);
  const [active, setActive] = useState<DialogRow | null>(null);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [text, setText] = useState("");
  const [reply, setReply] = useState<Msg | null>(null);
  const [editing, setEditing] = useState<Msg | null>(null);
  const [search, setSearch] = useState("");
  const [theme, setTheme] = useState<"light" | "dark">("light");
  const [settings, setSettings] = useState(false);
  const [viewer, setViewer] = useState<Attachment[] | null>(null);
  const [viewerIdx, setViewerIdx] = useState(0);
  const [typing, setTyping] = useState(false);
  const [narrowList, setNarrowList] = useState(false);
  const [flash, setFlash] = useState(false);
  const [emojiOn, setEmojiOn] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const dialogsRef = useRef<DialogRow[]>([]);
  const boxRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);
  const typingTimer = useRef<number | null>(null);
  const activeIdRef = useRef<number | null>(null);
  const meIdRef = useRef<number | null>(null);
  const applyIncomingRef = useRef<(msg: Msg) => void>(() => undefined);
  const pendingScroll = useRef<number | null>(null);
  const [jumpFlash, setJumpFlash] = useState<number | null>(null);
  activeIdRef.current = active?.id ?? null;
  meIdRef.current = me?.id ?? null;
  dialogsRef.current = dialogs;

  /**
   * Подгрузить профиль и список диалогов.
   */
  const boot = useCallback(async () => {
    if (!getAccess()) {
      router.replace("/login");
      return;
    }
    const meRes = await api("/users/me");
    if (!meRes.ok) {
      router.replace("/login");
      return;
    }
    const profile = await meRes.json();
    setMe(profile);
    const list = await jsonOrThrow<DialogRow[]>(await api("/dialogs"));
    setDialogs(list);
    if (list.length === 1) void openDialog(list[0]);
  }, [router]);

  useEffect(() => {
    const t = getTheme();
    setTheme(t);
    document.documentElement.setAttribute("data-theme", t);
    unlockAudio();
    const unlock = () => unlockAudio();
    window.addEventListener("pointerdown", unlock);
    window.addEventListener("keydown", unlock);
    window.addEventListener("touchstart", unlock);
    boot();
    return () => {
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
      window.removeEventListener("touchstart", unlock);
    };
  }, [boot]);

  useEffect(() => {
    const unread = dialogs.reduce((sum, d) => sum + d.unread, 0);
    setTabAlert(unread, !chatInForeground());
  }, [dialogs]);

  useEffect(() => {
    /**
     * Вернулись на вкладку чата: прочитать активный диалог и снять бейдж.
     */
    function onPageView() {
      const here = chatInForeground();
      if (here && activeIdRef.current) {
        const id = activeIdRef.current;
        void api(`/dialogs/${id}/read`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({}),
        });
        setDialogs((list) => list.map((d) => (d.id === id ? { ...d, unread: 0 } : d)));
      }
      const unread = dialogsRef.current.reduce(
        (sum, d) => sum + (d.id === activeIdRef.current && here ? 0 : d.unread),
        0,
      );
      setTabAlert(unread, !here);
    }
    document.addEventListener("visibilitychange", onPageView);
    window.addEventListener("focus", onPageView);
    window.addEventListener("blur", onPageView);
    return () => {
      document.removeEventListener("visibilitychange", onPageView);
      window.removeEventListener("focus", onPageView);
      window.removeEventListener("blur", onPageView);
    };
  }, []);

  /**
   * Открыть диалог и загрузить хвост истории.
   * @param row строка списка
   */
  async function openDialog(row: DialogRow) {
    setActive(row);
    activeIdRef.current = row.id;
    setNarrowList(false);
    const cached = (await loadCached(row.id)) as Msg[];
    if (activeIdRef.current !== row.id) return;
    if (cached.length) setMessages(cached);
    else setMessages([]);
    const data = await jsonOrThrow<{ messages: Msg[] }>(await api(`/dialogs/${row.id}/messages?limit=50`));
    if (activeIdRef.current !== row.id) return;
    setMessages((cur) => mergeMessages(cur.filter((m) => m.dialog_id === row.id), data.messages));
    await cacheMessages(row.id, data.messages);
    await api(`/dialogs/${row.id}/read`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({}),
    });
    setDialogs((d) => d.map((x) => (x.id === row.id ? { ...x, unread: 0 } : x)));
  }

  /**
   * Показать сообщение в открытом чате и обновить превью слева.
   * @param msg новое или своё только что отправленное
   */
  function applyIncoming(raw: Msg) {
    const msg = normalizeMsg(raw);
    if (msg.dialog_id === activeIdRef.current) {
      setMessages((cur) => (cur.some((m) => m.id === msg.id) ? cur : [...cur, msg]));
    }
    setDialogs((list) => {
      const mine = msg.author_id === meIdRef.current;
      const looking = activeIdRef.current === msg.dialog_id && chatInForeground();
      const next = list.map((d) =>
        d.id === msg.dialog_id
          ? {
              ...d,
              last_message: preview(msg),
              last_at: msg.created_at,
              unread: mine || looking ? 0 : d.unread + 1,
            }
          : d,
      );
      return next.sort((a, b) => (b.last_at || "").localeCompare(a.last_at || ""));
    });
    void cacheMessages(msg.dialog_id, [msg]);
  }
  applyIncomingRef.current = applyIncoming;

  /**
   * Перемотать к оригиналу ответа.
   */
  async function jumpToReply(id: number) {
    const el = document.getElementById(`msg-${id}`);
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      setJumpFlash(id);
      window.setTimeout(() => setJumpFlash((cur) => (cur === id ? null : cur)), 1600);
      return;
    }
    if (!active) return;
    pendingScroll.current = id;
    let oldest = messages[0]?.id;
    let acc = messages;
    for (let i = 0; i < 20 && oldest; i += 1) {
      const data = await jsonOrThrow<{ messages: Msg[] }>(
        await api(`/dialogs/${active.id}/messages?limit=50&before=${oldest}`),
      );
      if (!data.messages.length) break;
      acc = mergeMessages(data.messages, acc);
      setMessages(acc);
      await cacheMessages(active.id, data.messages);
      if (acc.some((m) => m.id === id)) break;
      oldest = data.messages[0]?.id;
    }
  }

  /**
   * Подгрузить старше при прокрутке вверх.
   */
  async function loadOlder() {
    if (!active || !messages.length) return;
    const data = await jsonOrThrow<{ messages: Msg[] }>(
      await api(`/dialogs/${active.id}/messages?limit=50&before=${messages[0].id}`),
    );
    if (!data.messages.length) return;
    const merged = [...data.messages, ...messages];
    setMessages(merged);
    await cacheMessages(active.id, data.messages);
  }

  useEffect(() => {
    const id = pendingScroll.current;
    if (id == null) return;
    const el = document.getElementById(`msg-${id}`);
    if (!el) return;
    pendingScroll.current = null;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    setJumpFlash(id);
    const t = window.setTimeout(() => setJumpFlash((cur) => (cur === id ? null : cur)), 1600);
    return () => window.clearTimeout(t);
  }, [messages]);

  useEffect(() => {
    if (!me) return;
    let closed = false;
    /**
     * Поднять WebSocket с текущим access. Не пересоздавать при смене чата.
     */
    function connect() {
      if (closed) return;
      const proto = location.protocol === "https:" ? "wss" : "ws";
      const ws = new WebSocket(`${proto}://${location.host}/extra/chat/api/ws?token=${encodeURIComponent(getAccess())}`);
      wsRef.current = ws;
      ws.onmessage = (ev) => {
        const data = JSON.parse(ev.data);
        if (data.event === "message") {
          const msg = normalizeMsg(data.message as Msg);
          applyIncomingRef.current(msg);
          if (msg.author_id !== meIdRef.current) {
            playIncoming();
            setFlash(true);
            window.setTimeout(() => setFlash(false), 600);
            if (msg.dialog_id === activeIdRef.current && chatInForeground()) {
              api(`/dialogs/${msg.dialog_id}/read`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ up_to_id: msg.id }),
              });
            }
          }
        }
        if (data.event === "edited") {
          const msg = data.message as Msg;
          if (msg.dialog_id === activeIdRef.current) {
            setMessages((cur) => cur.map((m) => (m.id === msg.id ? msg : m)));
          }
        }
        if (data.event === "deleted") {
          if (!data.dialog_id || data.dialog_id === activeIdRef.current) {
            setMessages((cur) => cur.filter((m) => m.id !== data.id));
          }
          cacheDelete(data.id);
        }
        if (data.event === "caught_up") {
          const dialogId = Number(data.dialog_id);
          setDialogs((list) => list.map((d) => (d.id === dialogId ? { ...d, unread: 0 } : d)));
        }
        if (data.event === "delivered" || data.event === "read") {
          const ids = (data.ids || (data.id != null ? [data.id] : [])).map(Number);
          const tick = data.event === "read" ? "read" : "delivered";
          setMessages((cur) => {
            const next = cur.map((m) => {
              if (!ids.includes(m.id)) return m;
              if (tick === "delivered" && m.ticks === "read") return m;
              return { ...m, ticks: tick };
            });
            const changed = next.filter((m) => ids.includes(m.id));
            if (changed.length) void cacheMessages(changed[0].dialog_id, changed);
            return next;
          });
        }
        if (data.event === "typing" && data.dialog_id === activeIdRef.current) {
          setTyping(true);
          if (typingTimer.current) window.clearTimeout(typingTimer.current);
          typingTimer.current = window.setTimeout(() => setTyping(false), 4000);
        }
        if (data.event === "presence") {
          setDialogs((list) =>
            list.map((d) =>
              d.peer.id === data.user_id
                ? { ...d, peer: { ...d.peer, online: data.online, last_seen: data.last_seen || d.peer.last_seen } }
                : d,
            ),
          );
          setActive((cur) =>
            cur && cur.peer.id === data.user_id
              ? { ...cur, peer: { ...cur.peer, online: data.online, last_seen: data.last_seen || cur.peer.last_seen } }
              : cur,
          );
        }
      };
      ws.onclose = () => {
        if (wsRef.current === ws && !closed) setTimeout(connect, 2000);
      };
    }
    connect();
    const ping = window.setInterval(() => {
      if (wsRef.current?.readyState === 1) wsRef.current.send(JSON.stringify({ event: "ping" }));
    }, 20000);
    return () => {
      closed = true;
      window.clearInterval(ping);
      wsRef.current?.close();
    };
  }, [me]);

  useEffect(() => {
    boxRef.current?.scrollTo(0, boxRef.current.scrollHeight);
  }, [messages.length, active?.id]);

  /**
   * Отправить текст или правку.
   */
  async function sendText() {
    if (!active || !text.trim()) return;
    if (editing) {
      const updated = await jsonOrThrow<Msg>(
        await api(`/messages/${editing.id}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ text: text.trim() }),
        }),
      );
      setMessages((cur) => cur.map((m) => (m.id === updated.id ? updated : m)));
      setEditing(null);
      setText("");
      return;
    }
    const created = await jsonOrThrow<Msg>(
      await api(`/dialogs/${active.id}/messages`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: text.trim(), reply_to_id: reply?.id }),
      }),
    );
    applyIncoming(created);
    setText("");
    setReply(null);
  }

  /**
   * Сообщить «печатает».
   */
  function onType(value: string) {
    setText(value);
    if (wsRef.current?.readyState === 1 && active) {
      wsRef.current.send(JSON.stringify({ event: "typing", dialog_id: active.id }));
    }
  }

  /**
   * Вставить эмодзи в позицию курсора.
   * @param emoji символ
   */
  function insertEmoji(emoji: string) {
    const el = inputRef.current;
    const start = el?.selectionStart ?? text.length;
    const end = el?.selectionEnd ?? text.length;
    const next = text.slice(0, start) + emoji + text.slice(end);
    setText(next);
    requestAnimationFrame(() => {
      if (!el) return;
      const pos = start + emoji.length;
      el.focus();
      el.setSelectionRange(pos, pos);
    });
    if (wsRef.current?.readyState === 1 && active) {
      wsRef.current.send(JSON.stringify({ event: "typing", dialog_id: active.id }));
    }
  }

  /**
   * Найти человека по логину и открыть диалог.
   */
  async function findUser() {
    const login = search.trim();
    if (!login) return;
    const res = await api("/dialogs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ login }),
    });
    const data = await jsonOrThrow<{ id: number; peer: User }>(res);
    const row: DialogRow = { id: data.id, peer: data.peer, last_message: "", unread: 0 };
    setDialogs((d) => (d.some((x) => x.id === row.id) ? d : [row, ...d]));
    openDialog(row);
    setSearch("");
  }

  /**
   * Загрузить фото/видео с сжатием фото на клиенте. Видео уйдёт на ffmpeg сервера.
   * @param files выбранные файлы
   */
  async function sendMedia(files: FileList | null) {
    if (!active || !files?.length) return;
    const chosen = Array.from(files).slice(0, 10);
    const fd = new FormData();
    for (const file of chosen) {
      if (file.type.startsWith("image/")) {
        if (file.size > 10 * 1024 * 1024) {
          alert("Фото больше 10 МБ");
          return;
        }
        const blob = await compressPhoto(file);
        fd.append("files", new File([blob], file.name.replace(/\.[^.]+$/, ".jpg"), { type: "image/jpeg" }));
      } else if (file.type.startsWith("video/")) {
        if (file.size > 100 * 1024 * 1024) {
          alert("Видео больше 100 МБ");
          return;
        }
        fd.append("files", file);
      }
    }
    fd.append("caption", text);
    fd.append("already_compressed", "0");
    if (reply) fd.append("reply_to_id", String(reply.id));
    const created = await jsonOrThrow<Msg>(await api(`/dialogs/${active.id}/media`, { method: "POST", body: fd }));
    applyIncoming(created);
    setText("");
    setReply(null);
  }

  /**
   * Отправить файл с gzip при выигрыше ≥ 10%.
   * @param files выбранные файлы
   */
  async function sendFiles(files: FileList | null) {
    if (!active || !files?.length) return;
    for (const file of Array.from(files)) {
      if (file.size > 50 * 1024 * 1024) {
        alert("Файл больше 50 МБ");
        continue;
      }
      const packed = await maybeGzip(file);
      const fd = new FormData();
      fd.append("file", new File([packed.blob], file.name, { type: file.type }));
      fd.append("gzip_flag", packed.gzip ? "1" : "0");
      fd.append("orig_size", String(packed.origSize));
      fd.append("caption", text);
      const created = await jsonOrThrow<Msg>(await api(`/dialogs/${active.id}/file`, { method: "POST", body: fd }));
      applyIncoming(created);
    }
    setText("");
  }

  /**
   * Удалить сообщение у обоих.
   * @param id id сообщения
   */
  async function removeMsg(id: number) {
    await api(`/messages/${id}`, { method: "DELETE" });
  }

  /**
   * Сменить тему.
   */
  function toggleTheme() {
    const next = theme === "light" ? "dark" : "light";
    setTheme(next);
    persistTheme(next);
    document.documentElement.setAttribute("data-theme", next);
  }

  /**
   * Выйти из аккаунта.
   */
  async function doLogout() {
    await logout();
    router.replace("/login");
  }

  /**
   * Загрузить аватар.
   * @param files файл картинки
   */
  async function uploadAvatar(files: FileList | null) {
    if (!files?.[0]) return;
    const blob = await compressPhoto(files[0]);
    const fd = new FormData();
    fd.append("file", new File([blob], "avatar.jpg", { type: "image/jpeg" }));
    const user = await jsonOrThrow<User>(await api("/users/me/avatar", { method: "POST", body: fd }));
    setMe(user);
  }

  /**
   * Сохранить ник собеседника.
   * @param nick новый ник
   */
  async function saveNick(nick: string) {
    if (!active) return;
    const peer = await jsonOrThrow<User>(
      await api(`/dialogs/${active.id}/alias`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ nick }),
      }),
    );
    setActive({ ...active, peer });
  }

  const mediaOfChat = useMemo(
    () =>
      messages.flatMap((m) => m.attachments.filter((a) => a.kind === "photo" || a.kind === "video")),
    [messages],
  );

  /**
   * Аватар или инициал.
   * @param user пользователь
   */
  function Avatar({ user }: { user: User }) {
    const name = user.display_name || user.nick || user.login;
    return (
      <div className="av" style={{ background: avatarColor(user.login) }}>
        {user.avatar_url ? <img src={user.avatar_url} alt="" /> : name.slice(0, 1).toUpperCase()}
      </div>
    );
  }

  if (!me) return <div className="page-center">Загрузка…</div>;

  return (
    <div className="app">
      <aside className={`sidebar ${!active || narrowList ? "show" : ""}`}>
        <div className={`topbar ${flash ? "flash" : ""}`}>
          <strong>Чат</strong>
          <button className="iconbtn" type="button" onClick={() => setSettings(true)}>⚙</button>
        </div>
        <input className="search" placeholder="Поиск по логину" value={search} onChange={(e) => setSearch(e.target.value)} onKeyDown={(e) => e.key === "Enter" && findUser()} />
        <div style={{ overflow: "auto" }}>
          {dialogs.map((d) => (
            <div key={d.id} className={`dlg ${active?.id === d.id ? "active" : ""}`} onClick={() => openDialog(d)}>
              <Avatar user={d.peer} />
              <div className="meta">
                <div className="row">
                  <span className="name">{d.peer.display_name || d.peer.login}{d.peer.online ? <span className="dot" /> : null}</span>
                  <span className="prev">{shortTime(d.last_at || undefined)}</span>
                </div>
                <div className="row">
                  <span className="prev">{d.last_message}</span>
                  {d.unread > 0 ? <span className="badge">{d.unread}</span> : null}
                </div>
              </div>
            </div>
          ))}
        </div>
      </aside>
      <section className="main">
        {active ? (
          <>
            <div className={`topbar ${flash ? "flash" : ""}`}>
              <button className="iconbtn" type="button" onClick={() => { setActive(null); setNarrowList(true); }}>←</button>
              <Avatar user={active.peer} />
              <div>
                <div><strong>{active.peer.display_name || active.peer.login}</strong></div>
                <div className="prev">{typing ? "печатает…" : lastSeenText(active.peer.last_seen, !!active.peer.online)}</div>
              </div>
              <div style={{ marginLeft: "auto" }}>
                <button className="iconbtn" type="button" onClick={() => {
                  const nick = prompt("Ник собеседника", active.peer.nick || "") || "";
                  saveNick(nick);
                }}>⋮</button>
              </div>
            </div>
            <div
              className="messages"
              ref={boxRef}
              onScroll={(e) => {
                if (e.currentTarget.scrollTop < 40) loadOlder();
              }}
            >
              {messages.map((m, i) => (
                <div key={m.id} style={{ display: "contents" }}>
                  {!sameMessageDay(m.created_at, messages[i - 1]?.created_at) ? (
                    <div className="daychip">{dayChipLabel(m.created_at)}</div>
                  ) : null}
                <div id={`msg-${m.id}`} className={`bubble ${m.author_id === me.id ? "out" : "in"}${jumpFlash === m.id ? " jump" : ""}`} onContextMenu={(e) => {
                  e.preventDefault();
                  const act = prompt("reply / edit / delete", "reply");
                  if (act === "reply") setReply(m);
                  if (act === "edit" && m.author_id === me.id) { setEditing(m); setText(m.text || ""); }
                  if (act === "delete") removeMsg(m.id);
                }}>
                  {m.reply_to ? (
                    <div className="replyq" onClick={() => { void jumpToReply(m.reply_to!.id); }}>
                      {m.reply_to.text || m.reply_to.type}
                    </div>
                  ) : null}
                  {m.attachments.length > 0 && m.type !== "file" ? (
                    <div className={`album n${Math.min(m.attachments.length, 4)}`}>
                      {m.attachments.map((a, i) => (
                        <div key={a.id} className="thumb" onClick={() => { setViewer(mediaOfChat); setViewerIdx(Math.max(0, mediaOfChat.findIndex((x) => x.id === a.id))); }}>
                          <img src={a.preview_url || a.url} alt="" />
                          {a.kind === "video" ? <span>▶ {a.duration_ms ? Math.round(a.duration_ms / 1000) + "с" : ""}</span> : null}
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {m.type === "file" && m.attachments[0] ? (
                    <div className="filechip">
                      <span>📄 {m.attachments[0].orig_name}</span>
                      <a href={`${m.attachments[0].url}?download=1`}>Сохранить</a>
                    </div>
                  ) : null}
                  {m.text?.trim() ? (
                    <div className={isSoloEmoji(m.text) ? "solo-emoji" : undefined}>
                      {m.text.trim()}{m.edited_at ? " (изменено)" : ""}
                    </div>
                  ) : null}
                  <div className="time">
                    {shortTime(m.created_at)}
                    {m.author_id === me.id ? (
                      <span className={`ticks ${m.ticks}`}>{m.ticks === "sent" ? "✓" : "✓✓"}</span>
                    ) : null}
                  </div>
                </div>
                </div>
              ))}
            </div>
            {reply ? <div className="composer">Ответ: {reply.text || reply.type} <button type="button" onClick={() => setReply(null)}>×</button></div> : null}
            {emojiOn ? <EmojiPicker onPick={insertEmoji} /> : null}
            <div className="composer">
              <label className="iconbtn">📎
                <input hidden type="file" multiple accept="image/*,video/*" onChange={(e) => sendMedia(e.target.files)} />
              </label>
              <label className="iconbtn">📄
                <input hidden type="file" multiple onChange={(e) => sendFiles(e.target.files)} />
              </label>
              <textarea
                ref={inputRef}
                value={text}
                onChange={(e) => onType(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendText(); } }}
                placeholder="Сообщение"
              />
              <button className={`iconbtn ${emojiOn ? "on" : ""}`} type="button" onClick={() => setEmojiOn((v) => !v)}>😊</button>
              <button className="iconbtn" type="button" onClick={sendText}>➤</button>
            </div>
          </>
        ) : (
          <div className="empty">Выберите чат</div>
        )}
      </section>
      {settings ? (
        <div className="modal" onClick={() => setSettings(false)}>
          <div className="card" onClick={(e) => e.stopPropagation()} style={{ margin: "auto" }}>
            <h1>Настройки</h1>
            <p>{me.login}</p>
            <label>Аватар<input type="file" accept="image/*" onChange={(e) => uploadAvatar(e.target.files)} /></label>
            <button className="btn" type="button" onClick={toggleTheme}>{theme === "light" ? "Тёмная тема" : "Светлая тема"}</button>
            <button className="btn" type="button" onClick={doLogout}>Выйти</button>
          </div>
        </div>
      ) : null}
      {viewer ? (
        <div className="modal" onClick={() => setViewer(null)}>
          <div className="topbar"><button className="iconbtn" type="button" onClick={() => setViewer(null)}>✕</button>
            <a className="iconbtn" href={`${viewer[viewerIdx].url}?download=1`}>Сохранить</a>
          </div>
          {viewer[viewerIdx].kind === "video" ? (
            <video src={viewer[viewerIdx].url} controls autoPlay />
          ) : (
            <img src={viewer[viewerIdx].url} alt="" onClick={(e) => e.stopPropagation()} />
          )}
          <div className="topbar">
            <button className="iconbtn" type="button" onClick={() => setViewerIdx((i) => Math.max(0, i - 1))}>‹</button>
            <button className="iconbtn" type="button" onClick={() => setViewerIdx((i) => Math.min(viewer.length - 1, i + 1))}>›</button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
