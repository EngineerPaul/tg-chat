/**
 * Звук входящего и пометка вкладки браузера.
 * Тон свой, в характере короткого TG-сигнала, не файл Telegram.
 */

const TITLE = "Чат";
let audio: AudioContext | null = null;
let blink: number | null = null;

/**
 * Страница чата на экране и в фокусе. Другая вкладка или окно — нет.
 */
export function chatInForeground(): boolean {
  return document.visibilityState === "visible" && document.hasFocus();
}

/**
 * Разрешить звук после первого клика (политика автовоспроизведения).
 */
export function unlockAudio(): void {
  if (!audio) audio = new AudioContext();
  if (audio.state === "suspended") void audio.resume();
}

/**
 * Короткий двухтональный сигнал входящего.
 */
export function playIncoming(): void {
  unlockAudio();
  if (!audio) return;
  const now = audio.currentTime;
  const osc = audio.createOscillator();
  const gain = audio.createGain();
  osc.type = "sine";
  osc.frequency.setValueAtTime(880, now);
  osc.frequency.setValueAtTime(1175, now + 0.08);
  gain.gain.setValueAtTime(0.0001, now);
  gain.gain.exponentialRampToValueAtTime(0.12, now + 0.015);
  gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.26);
  osc.connect(gain);
  gain.connect(audio.destination);
  osc.start(now);
  osc.stop(now + 0.28);
}

/**
 * Нарисовать иконку вкладки, при непрочитанных — красная точка.
 * Ссылку меняем целиком: Chrome кэширует старый href.
 * @param marked есть непрочитанные
 */
function setFavicon(marked: boolean): void {
  const canvas = document.createElement("canvas");
  canvas.width = 64;
  canvas.height = 64;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  ctx.fillStyle = "#3390ec";
  ctx.beginPath();
  ctx.arc(32, 32, 28, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#fff";
  ctx.font = "bold 32px sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("C", 32, 34);
  if (marked) {
    ctx.fillStyle = "#e53935";
    ctx.beginPath();
    ctx.arc(50, 14, 13, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#fff";
    ctx.lineWidth = 3;
    ctx.stroke();
  }
  document.querySelectorAll("link[rel*='icon']").forEach((el) => el.remove());
  const link = document.createElement("link");
  link.rel = "icon";
  link.type = "image/png";
  link.href = `${canvas.toDataURL("image/png")}#${marked ? "on" : "off"}`;
  document.head.appendChild(link);
}

/**
 * Заголовок вкладки и точка на иконке. В фоне заголовок мигает.
 * @param unread сумма непрочитанных
 * @param hidden вкладка не на экране
 */
export function setTabAlert(unread: number, hidden: boolean): void {
  if (blink !== null) {
    window.clearInterval(blink);
    blink = null;
  }
  if (unread <= 0) {
    document.title = TITLE;
    setFavicon(false);
    return;
  }
  const marked = `(${unread}) ${TITLE}`;
  document.title = marked;
  setFavicon(true);
  if (!hidden) return;
  let flip = false;
  blink = window.setInterval(() => {
    document.title = flip ? "Новое сообщение" : marked;
    flip = !flip;
  }, 1200);
}
