/**
 * Форматирование last seen и времени сообщений по-русски.
 */

/**
 * Относительный статус «был(а)…».
 * @param iso ISO-время last_seen
 * @param online сейчас в сети
 */
export function lastSeenText(iso: string | undefined, online: boolean): string {
  if (online) return "в сети";
  if (!iso) return "";
  const then = new Date(iso).getTime();
  const diff = Date.now() - then;
  if (diff < 60_000) return "был(а) только что";
  if (diff < 5 * 60_000) return "был(а) 5 минут назад";
  const d = new Date(iso);
  const now = new Date();
  const hh = d.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
  if (d.toDateString() === now.toDateString()) return `был(а) в ${hh}`;
  const yest = new Date(now);
  yest.setDate(now.getDate() - 1);
  if (d.toDateString() === yest.toDateString()) return `был(а) вчера в ${hh}`;
  return `был(а) ${d.toLocaleDateString("ru-RU")} в ${hh}`;
}

/**
 * Короткое время для пузыря и списка.
 * @param iso ISO-время
 */
export function shortTime(iso: string | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
}

/**
 * Цвет круга-заглушки аватара по логину.
 * @param login логин
 */
export function avatarColor(login: string): string {
  const colors = ["#e17076", "#7bc862", "#e5ca77", "#65aadd", "#a695e7", "#ee7aae"];
  let h = 0;
  for (let i = 0; i < login.length; i += 1) h = (h + login.charCodeAt(i) * (i + 1)) % colors.length;
  return colors[h];
}
