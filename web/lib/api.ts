/**
 * Клиент REST API чата с автообновлением access.
 */

import { clearTokens, getAccess, getRefresh, setAccess, setTokens } from "./storage";

const API = "/extra/chat/api";

/**
 * Вызвать API. При 401 пробует refresh и повторяет запрос.
 * @param path путь после /extra/chat/api
 * @param init fetch-опции
 */
export async function api(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  const access = getAccess();
  if (access) headers.set("Authorization", `Bearer ${access}`);
  let res = await fetch(`${API}${path}`, { ...init, headers });
  if (res.status === 401 && getRefresh()) {
    const ok = await refreshAccess();
    if (ok) {
      headers.set("Authorization", `Bearer ${getAccess()}`);
      res = await fetch(`${API}${path}`, { ...init, headers });
    }
  }
  return res;
}

/**
 * Обновить access по refresh.
 * @returns получилось ли
 */
export async function refreshAccess(): Promise<boolean> {
  const refresh = getRefresh();
  if (!refresh) return false;
  const res = await fetch(`${API}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refresh_token: refresh }),
  });
  if (!res.ok) {
    clearTokens();
    return false;
  }
  const data = await res.json();
  setAccess(data.access_token);
  return true;
}

/**
 * Войти и сохранить токены.
 * @param login логин
 * @param password пароль
 */
export async function login(login: string, password: string): Promise<void> {
  const res = await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ login, password }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || "Не удалось войти");
  setTokens(data.access_token, data.refresh_token);
}

/**
 * Зарегистрироваться и сразу войти.
 * @param login логин
 * @param password пароль
 */
export async function register(login: string, password: string): Promise<void> {
  const res = await fetch(`${API}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ login, password }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(typeof data.detail === "string" ? data.detail : "Не удалось зарегистрироваться");
  setTokens(data.access_token, data.refresh_token);
}

/**
 * Выйти: отозвать refresh на сервере и стереть локально.
 */
export async function logout(): Promise<void> {
  const refresh = getRefresh();
  try {
    await api("/auth/logout", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: refresh }),
    });
  } finally {
    clearTokens();
  }
}

/**
 * Разобрать JSON, если ответ успешный.
 * @param res ответ fetch
 */
export async function jsonOrThrow<T>(res: Response): Promise<T> {
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const detail = (data as { detail?: string }).detail;
    throw new Error(typeof detail === "string" ? detail : "Ошибка запроса");
  }
  return data as T;
}
