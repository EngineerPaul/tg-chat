"use client";

import { useMemo, useState } from "react";
import { EMOJI_CATS, loadRecentEmoji, pushRecentEmoji } from "@/lib/emoji";

/**
 * Панель эмодзи над полем ввода, как в Telegram.
 * @param onPick вставить символ в поле
 */
export default function EmojiPicker({ onPick }: { onPick: (emoji: string) => void }) {
  const [cat, setCat] = useState(0);
  const [recent, setRecent] = useState(loadRecentEmoji);

  const items = useMemo(() => {
    if (cat === -1) return recent;
    return EMOJI_CATS[cat]?.items || [];
  }, [cat, recent]);

  /**
   * Выбрать эмодзи и обновить недавние.
   * @param emoji символ
   */
  function pick(emoji: string) {
    setRecent(pushRecentEmoji(emoji));
    onPick(emoji);
  }

  return (
    <div className="emojibar">
      <div className="emojigrid">
        {items.map((e, i) => (
          <button key={`${e}-${i}`} type="button" className="emojibtn" onClick={() => pick(e)}>
            {e}
          </button>
        ))}
      </div>
      <div className="emojitabs">
        <button type="button" className={cat === -1 ? "on" : ""} onClick={() => setCat(-1)} title="Недавние">
          🕒
        </button>
        {EMOJI_CATS.map((c, i) => (
          <button key={c.id} type="button" className={cat === i ? "on" : ""} onClick={() => setCat(i)}>
            {c.icon}
          </button>
        ))}
      </div>
    </div>
  );
}
