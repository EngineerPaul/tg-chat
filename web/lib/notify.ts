/**
 * Звук входящего и пометка вкладки браузера.
 * Свой двухтональный сигнал, если браузер даёт автоплей.
 * Если вкладка в фоне и автоплей молчит — системное уведомление со звуком ОС.
 */

const TITLE = "Чат";
let blink: number | null = null;
let wavUrl: string | null = null;
let tone: HTMLAudioElement | null = null;
let ctx: AudioContext | null = null;
let unlockBound = false;
let primed = false;
let lastNotifyAt = 0;

declare global {
  interface Window {
    __chatAc?: AudioContext;
    webkitAudioContext?: typeof AudioContext;
  }
}

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

function getTone(): HTMLAudioElement {
  if (!tone) {
    tone = new Audio(incomingWavUrl());
    tone.preload = "auto";
    tone.volume = 0.45;
  }
  return tone;
}

function audioContextCtor(): (typeof AudioContext) | undefined {
  if (typeof window === "undefined") return undefined;
  return window.AudioContext || window.webkitAudioContext;
}

/**
 * Живой AudioContext: после жеста свой тон можно играть и в фоне.
 */
function ensureCtx(): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (window.__chatAc && window.__chatAc.state !== "closed") {
    ctx = window.__chatAc;
    return ctx;
  }
  if (ctx && ctx.state !== "closed") return ctx;
  const Ctor = audioContextCtor();
  if (!Ctor) return null;
  ctx = new Ctor();
  window.__chatAc = ctx;
  return ctx;
}

/**
 * Двухтональный сигнал через Web Audio.
 */
function playTones(ac: AudioContext): void {
  const now = ac.currentTime;
  const master = ac.createGain();
  master.gain.value = 0.28;
  master.connect(ac.destination);
  const blip = (freq: number, t0: number, dur: number) => {
    const osc = ac.createOscillator();
    const gain = ac.createGain();
    osc.type = "sine";
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.0001, t0);
    gain.gain.linearRampToValueAtTime(1, t0 + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    osc.connect(gain);
    gain.connect(master);
    osc.start(t0);
    osc.stop(t0 + dur + 0.02);
  };
  blip(880, now, 0.09);
  blip(1175, now + 0.09, 0.19);
}

function playHtml(): Promise<void> {
  const node = getTone();
  node.muted = false;
  node.volume = 0.45;
  node.currentTime = 0;
  return node.play().then(() => undefined);
}

/**
 * Разрешение на уведомления запоминается браузером и переживает перезапуск.
 */
function askNotifyPermission(): void {
  if (typeof Notification === "undefined") return;
  if (Notification.permission !== "default") return;
  void Notification.requestPermission();
}

/**
 * Системный звук, когда вкладка в фоне и свой тон запрещён автоплеем.
 */
function pingDesktop(): void {
  if (typeof Notification === "undefined") return;
  if (Notification.permission !== "granted") return;
  if (chatInForeground()) return;
  const now = Date.now();
  if (now - lastNotifyAt < 400) return;
  lastNotifyAt = now;
  try {
    const n = new Notification(TITLE, {
      body: "Новое сообщение",
      tag: "chat-incoming",
      renotify: true,
      silent: false,
    });
    n.onclick = () => {
      window.focus();
      n.close();
    };
  } catch {
    /* старые браузеры */
  }
}

/**
 * Попытка разблокировать свой тон. Жест на вкладке запоминается до её закрытия.
 */
export function unlockAudio(): void {
  askNotifyPermission();
  const ac = ensureCtx();
  if (ac) {
    if (ac.state === "suspended") {
      void ac.resume().then(() => {
        primed = true;
      }).catch(() => undefined);
    } else if (ac.state === "running") {
      primed = true;
    }
  }
  if (primed) return;
  const node = getTone();
  node.muted = true;
  node.volume = 0;
  void node.play().then(() => {
    node.pause();
    node.currentTime = 0;
    node.muted = false;
    node.volume = 0.45;
    primed = true;
  }).catch(() => {
    node.muted = false;
    node.volume = 0.45;
  });
}

/**
 * Слушать жесты как можно раньше, в том числе до гидрации React.
 */
export function installAudioUnlock(): void {
  if (typeof window === "undefined" || unlockBound) return;
  unlockBound = true;
  const kick = () => unlockAudio();
  ["pointerdown", "mousedown", "keydown", "touchstart", "click"].forEach((ev) => {
    window.addEventListener(ev, kick, true);
  });
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState !== "visible") return;
    const ac = ensureCtx();
    if (ac && ac.state === "suspended") void ac.resume().catch(() => undefined);
  });
}

/**
 * Сигнал входящего: свой тон, иначе уведомление ОС, если вкладки нет на экране.
 */
export function playIncoming(): void {
  const fail = () => {
    pingDesktop();
  };
  const ac = ensureCtx();
  if (ac) {
    const run = () => {
      try {
        playTones(ac);
      } catch {
        void playHtml().catch(fail);
      }
    };
    if (ac.state === "running") {
      run();
      return;
    }
    void ac.resume().then(run).catch(() => {
      void playHtml().catch(fail);
    });
    return;
  }
  void playHtml().catch(fail);
}

if (typeof window !== "undefined") {
  installAudioUnlock();
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
  const ctx2d = canvas.getContext("2d");
  if (!ctx2d) return;
  ctx2d.fillStyle = "#3390ec";
  ctx2d.beginPath();
  ctx2d.arc(32, 32, 28, 0, Math.PI * 2);
  ctx2d.fill();
  ctx2d.fillStyle = "#fff";
  ctx2d.font = "bold 32px sans-serif";
  ctx2d.textAlign = "center";
  ctx2d.textBaseline = "middle";
  ctx2d.fillText("C", 32, 34);
  if (marked) {
    ctx2d.fillStyle = "#e53935";
    ctx2d.beginPath();
    ctx2d.arc(50, 14, 13, 0, Math.PI * 2);
    ctx2d.fill();
    ctx2d.strokeStyle = "#fff";
    ctx2d.lineWidth = 3;
    ctx2d.stroke();
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
