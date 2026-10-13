/**
 * Локальное хранение JWT и темы. Refresh живёт до «Выйти».
 */

const ACCESS = "chat_access";
const REFRESH = "chat_refresh";
const THEME = "chat_theme";

/**
 * Прочитать access-токен.
 * @returns JWT или пустая строка
 */
export function getAccess(): string {
  if (typeof window === "undefined") return "";
  return localStorage.getItem(ACCESS) || "";
}

/**
 * Прочитать refresh-токен.
 * @returns JWT или пустая строка
 */
export function getRefresh(): string {
  if (typeof window === "undefined") return "";
  return localStorage.getItem(REFRESH) || "";
}

/**
 * Сохранить пару токенов после входа.
 * @param access короткий JWT
 * @param refresh долгий JWT
 */
export function setTokens(access: string, refresh: string): void {
  localStorage.setItem(ACCESS, access);
  localStorage.setItem(REFRESH, refresh);
}

/**
 * Обновить только access после refresh.
 * @param access новый короткий JWT
 */
export function setAccess(access: string): void {
  localStorage.setItem(ACCESS, access);
}

/**
 * Стереть сессию браузера.
 */
export function clearTokens(): void {
  localStorage.removeItem(ACCESS);
  localStorage.removeItem(REFRESH);
}

/**
 * Сохранённая тема.
 * @returns light или dark
 */
export function getTheme(): "light" | "dark" {
  if (typeof window === "undefined") return "light";
  return localStorage.getItem(THEME) === "dark" ? "dark" : "light";
}

/**
 * Запомнить тему.
 * @param theme light или dark
 */
export function setTheme(theme: "light" | "dark"): void {
  localStorage.setItem(THEME, theme);
}
