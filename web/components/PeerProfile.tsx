"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api, jsonOrThrow } from "@/lib/api";
import { avatarColor } from "@/lib/format";

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
  message_id?: number;
  created_at?: string;
};

type SharedLink = {
  message_id: number;
  text: string;
  href: string;
  created_at: string;
};

type Tab = "links" | "media" | "files";

type Props = {
  dialogId: number;
  peer: User;
  onPeer: (peer: User) => void;
  onClose: () => void;
  onOpenMedia: (items: Attachment[], index: number) => void;
};

type Page<T> = { items: T[]; has_more: boolean };

/**
 * Размер файла для вкладки «Файлы».
 */
function formatSize(n: number): string {
  if (!n) return "";
  if (n < 1024) return `${n} Б`;
  if (n < 1024 * 1024) return `${Math.round(n / 1024)} КБ`;
  return `${(n / 1024 / 1024).toFixed(1)} МБ`;
}

/**
 * Профиль собеседника: фото, логин, ник, вкладки ссылок / фото / файлов с сервера.
 */
export default function PeerProfile({ dialogId, peer, onPeer, onClose, onOpenMedia }: Props) {
  const [nick, setNick] = useState(peer.nick || "");
  const [tab, setTab] = useState<Tab>("links");
  const [links, setLinks] = useState<SharedLink[]>([]);
  const [media, setMedia] = useState<Attachment[]>([]);
  const [files, setFiles] = useState<Attachment[]>([]);
  const [linksMore, setLinksMore] = useState(true);
  const [mediaMore, setMediaMore] = useState(true);
  const [filesMore, setFilesMore] = useState(true);
  const loading = useRef({ links: false, media: false, files: false });
  const nickTimer = useRef<number | null>(null);
  const nickRef = useRef(peer.nick || "");
  const [mediaBytes, setMediaBytes] = useState(0);
  const [fileBytes, setFileBytes] = useState(0);

  useEffect(() => {
    setNick(peer.nick || "");
    nickRef.current = peer.nick || "";
  }, [peer.id, peer.nick]);

  useEffect(() => {
    let gone = false;
    void (async () => {
      try {
        const data = await jsonOrThrow<{ items: { id: number; size: number; kind?: string }[] }>(
          await api(`/dialogs/${dialogId}/storage`),
        );
        if (gone) return;
        let photos = 0;
        let docs = 0;
        for (const row of data.items) {
          const n = row.size || 0;
          if (row.kind === "file") docs += n;
          else photos += n;
        }
        setMediaBytes(photos);
        setFileBytes(docs);
      } catch {
        if (!gone) {
          setMediaBytes(0);
          setFileBytes(0);
        }
      }
    })();
    return () => {
      gone = true;
    };
  }, [dialogId]);

  /**
   * Сохранить ник; пустой сбрасывает на логин.
   */
  const persistNick = useCallback(
    async (value: string) => {
      if (!dialogId) return;
      const next = await jsonOrThrow<User>(
        await api(`/dialogs/${dialogId}/alias`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ nick: value }),
        }),
      );
      nickRef.current = next.nick || "";
      onPeer(next);
    },
    [dialogId, onPeer],
  );

  useEffect(() => {
    if (nickTimer.current) window.clearTimeout(nickTimer.current);
    nickTimer.current = window.setTimeout(() => {
      if (nick === nickRef.current) return;
      void persistNick(nick);
    }, 700);
    return () => {
      if (nickTimer.current) window.clearTimeout(nickTimer.current);
    };
  }, [nick, persistNick]);

  /**
   * Догрузить текущую вкладку с сервера.
   */
  const loadMore = useCallback(
    async (which: Tab) => {
      if (loading.current[which]) return;
      const more = which === "links" ? linksMore : which === "media" ? mediaMore : filesMore;
      if (!more) return;
      loading.current[which] = true;
      try {
        const list = which === "links" ? links : which === "media" ? media : files;
        const before =
          which === "links"
            ? list.length
              ? (list[list.length - 1] as SharedLink).message_id
              : undefined
            : list.length
              ? (list[list.length - 1] as Attachment).id
              : undefined;
        const kind = which === "media" ? "media" : which;
        const qs = new URLSearchParams({ limit: "50" });
        if (before) qs.set("before", String(before));
        const page = await jsonOrThrow<Page<Attachment | SharedLink>>(
          await api(`/dialogs/${dialogId}/shared/${kind}?${qs}`),
        );
        if (which === "links") {
          setLinks((cur) => [...cur, ...(page.items as SharedLink[])]);
          setLinksMore(page.has_more);
        } else if (which === "media") {
          setMedia((cur) => [...cur, ...(page.items as Attachment[])]);
          setMediaMore(page.has_more);
        } else {
          setFiles((cur) => [...cur, ...(page.items as Attachment[])]);
          setFilesMore(page.has_more);
        }
      } catch {
        if (which === "links") setLinksMore(false);
        else if (which === "media") setMediaMore(false);
        else setFilesMore(false);
      } finally {
        loading.current[which] = false;
      }
    },
    [dialogId, files, filesMore, links, linksMore, media, mediaMore],
  );

  useEffect(() => {
    const empty =
      (tab === "links" && links.length === 0 && linksMore) ||
      (tab === "media" && media.length === 0 && mediaMore) ||
      (tab === "files" && files.length === 0 && filesMore);
    if (empty) void loadMore(tab);
  }, [tab, files.length, filesMore, links.length, linksMore, loadMore, media.length, mediaMore]);

  const name = (peer.nick || "").trim() || peer.display_name || peer.login;
  const more = tab === "links" ? linksMore : tab === "media" ? mediaMore : filesMore;

  return (
    <div className="profile">
      <div className="topbar">
        <button
          className="iconbtn"
          type="button"
          onClick={() => {
            if (nick !== nickRef.current) void persistNick(nick);
            onClose();
          }}
        >←</button>
        <strong>Профиль</strong>
      </div>
      <div
        className="profile-body"
        onScroll={(e) => {
          const node = e.currentTarget;
          if (node.scrollHeight - node.scrollTop - node.clientHeight < 80 && more) void loadMore(tab);
        }}
      >
        <div className="profile-head">
          <div className="av profile-av" style={{ background: avatarColor(peer.login) }}>
            {peer.avatar_url ? <img src={peer.avatar_url} alt="" /> : peer.login.slice(0, 1).toUpperCase()}
          </div>
          <div className="profile-login">{peer.login}</div>
          {peer.online ? <div className="prev">в сети</div> : null}
          <input
            className="profile-nick"
            value={nick}
            maxLength={60}
            placeholder="Ник"
            onChange={(e) => setNick(e.target.value)}
            onBlur={() => {
              if (nick !== nickRef.current) void persistNick(nick);
            }}
          />
          <div className="prev">{name !== peer.login ? `В чате: ${name}` : "В чате показывается логин"}</div>
        </div>
        <div className="profile-tabs">
          <button type="button" className={tab === "links" ? "on" : ""} onClick={() => setTab("links")}>Ссылки</button>
          <button type="button" className={tab === "media" ? "on" : ""} onClick={() => setTab("media")}>Фото</button>
          <button type="button" className={tab === "files" ? "on" : ""} onClick={() => setTab("files")}>Файлы</button>
        </div>
        {tab === "links" ? (
          links.length ? (
            <div className="profile-list">
              {links.map((row, i) => (
                <a key={`${row.message_id}-${i}`} className="profile-link" href={row.href} target="_blank" rel="noopener noreferrer">
                  {row.text}
                </a>
              ))}
            </div>
          ) : (
            <div className="profile-empty">{linksMore ? "Загрузка…" : "Нет ссылок"}</div>
          )
        ) : null}
        {tab === "media" ? (
          <>
          <div className="profile-storebar"><span>{formatSize(mediaBytes) || "0 Б"}</span></div>
          {media.length ? (
            <div className="profile-grid">
              {media.map((a, i) => (
                <div
                  key={a.id}
                  className="cell"
                  onClick={() => onOpenMedia(media, i)}
                >
                  <img src={a.preview_url || a.url} alt="" loading="lazy" />
                  {a.kind === "video" ? <span className="play">▶</span> : null}
                </div>
              ))}
            </div>
          ) : (
            <div className="profile-empty">{mediaMore ? "Загрузка…" : "Нет фото"}</div>
          )}
          </>
        ) : null}
        {tab === "files" ? (
          <>
          <div className="profile-storebar"><span>{formatSize(fileBytes) || "0 Б"}</span></div>
          {files.length ? (
            <div className="profile-list">
              {files.map((f) => (
                <a key={f.id} className="profile-file" href={`${f.url}?download=1`}>
                  <span>📄</span>
                  <span>
                    <strong>{f.orig_name || "Файл"}</strong>
                    <div className="prev">{formatSize(f.size)}</div>
                  </span>
                </a>
              ))}
            </div>
          ) : (
            <div className="profile-empty">{filesMore ? "Загрузка…" : "Нет файлов"}</div>
          )}
          </>
        ) : null}
      </div>
    </div>
  );
}
