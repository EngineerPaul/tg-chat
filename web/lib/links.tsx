"use client";

import type { ReactNode } from "react";

const URL_RE = /\b(?:https?:\/\/|www\.)[^\s<>"']+/gi;

/**
 * Убрать хвостовую пунктуацию, которая не часть адреса.
 */
function trimUrl(raw: string): string {
  let s = raw;
  while (s && /[.,;:!?]$/.test(s)) s = s.slice(0, -1);
  while (s.endsWith(")") && (s.match(/\(/g)?.length ?? 0) < (s.match(/\)/g)?.length ?? 0)) {
    s = s.slice(0, -1);
  }
  return s;
}

/**
 * http(s) как есть; www. — через https.
 */
function hrefOf(cut: string): string | null {
  const t = cut.trim();
  if (!t) return null;
  const href = /^https?:\/\//i.test(t) ? t : `https://${t}`;
  if (!/^https?:\/\//i.test(href)) return null;
  return href;
}

/**
 * Текст сообщения: http(s) и www. как ссылки в новой вкладке.
 */
export function LinkedText({ text, suffix }: { text: string; suffix?: string }) {
  const nodes: ReactNode[] = [];
  const re = new RegExp(URL_RE.source, "gi");
  let last = 0;
  let m: RegExpExecArray | null;
  let n = 0;
  while ((m = re.exec(text))) {
    const cut = trimUrl(m[0]);
    const href = hrefOf(cut);
    if (!href || cut.length < 4) continue;
    if (m.index > last) nodes.push(text.slice(last, m.index));
    nodes.push(
      <a key={`l${n}`} href={href} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}>
        {cut}
      </a>,
    );
    n += 1;
    last = m.index + cut.length;
  }
  if (last < text.length) nodes.push(text.slice(last));
  if (suffix) nodes.push(suffix);
  return <>{nodes}</>;
}
