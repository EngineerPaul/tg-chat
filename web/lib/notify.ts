/**
 * Звук входящего и пометка вкладки браузера.
 * Тон свой, в характере короткого TG-сигнала, не файл Telegram.
 */

const TITLE = "Чат";
let audio: AudioContext | null = null;
let blink: number | null = null;

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
 * @param marked есть непрочитанные
 */
function setFavicon(marked: boolean): void {
  let link = document.querySelector<HTMLLinkElement>("link[rel='icon']");
  if (!link) {
    link = document.createElement("link");
    link.rel = "icon";
    document.head.appendChild(link);
  }
  const canvas = document.createElement("canvas");
  canvas.width = 32;
  canvas.height = 32;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  ctx.fillStyle = "#3390ec";
  ctx.beginPath();
  ctx.arc(16, 16, 14, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#fff";
  ctx.font = "bold 16px sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("C", 16, 17);
  if (marked) {
    ctx.fillStyle = "#e53935";
    ctx.beginPath();
    ctx.arc(24, 8, 6, 0, Math.PI * 2);
    ctx.fill();
  }
  link.href = canvas.toDataURL("image/png");
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
