/**
 * Звук входящего и пометка вкладки браузера.
 * Тон свой, в характере короткого TG-сигнала, не файл Telegram.
 */

const TITLE = "Чат";
let blink: number | null = null;
let wavUrl: string | null = null;

/**
 * Страница чата на экране и в фокусе. Другая вкладка или окно — нет.
 */
export function chatInForeground(): boolean {
  return document.visibilityState === "visible" && document.hasFocus();
}

/**
 * Собрать короткий WAV двух тонов.
 */
function incomingWavUrl(): string {
  if (wavUrl) return wavUrl;
  const rate = 22050;
  const n = Math.floor(rate * 0.28);
  const pcm = new Int16Array(n);
  for (let i = 0; i < n; i += 1) {
    const t = i / rate;
    const freq = t < 0.09 ? 880 : 1175;
    const env = t < 0.02 ? t / 0.02 : Math.max(0, 1 - t / 0.28);
    pcm[i] = Math.round(Math.sin(2 * Math.PI * freq * t) * env * 0.28 * 32767);
  }
  const bytes = new Uint8Array(44 + n * 2);
  const view = new DataView(bytes.buffer);
  const write = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i += 1) bytes[offset + i] = text.charCodeAt(i);
  };
  write(0, "RIFF");
  view.setUint32(4, 36 + n * 2, true);
  write(8, "WAVE");
  write(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, rate, true);
  view.setUint32(28, rate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  write(36, "data");
  view.setUint32(40, n * 2, true);
  bytes.set(new Uint8Array(pcm.buffer), 44);
  wavUrl = URL.createObjectURL(new Blob([bytes], { type: "audio/wav" }));
  return wavUrl;
}

/**
 * Попытка разблокировать звук. Без жеста браузер может отказать — тогда тишина, не отложенный сигнал.
 */
export function unlockAudio(): void {
  const probe = new Audio(incomingWavUrl());
  probe.volume = 0.01;
  void probe.play().then(() => {
    probe.pause();
    probe.currentTime = 0;
  }).catch(() => undefined);
}

/**
 * Короткий сигнал входящего. Если автоплей запрещён — не ставим в очередь.
 */
export function playIncoming(): void {
  const tone = new Audio(incomingWavUrl());
  tone.volume = 0.45;
  void tone.play().catch(() => undefined);
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
