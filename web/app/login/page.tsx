"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { login } from "@/lib/api";

/**
 * Страница входа в веб-чат.
 */
export default function LoginPage() {
  const router = useRouter();
  const [loginValue, setLoginValue] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState("");

  /**
   * Отправить форму входа.
   * @param e событие формы
   */
  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErr("");
    try {
      await login(loginValue, password);
      router.replace("/chat");
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Ошибка");
    }
  }

  return (
    <div className="page-center">
      <form className="card" onSubmit={onSubmit}>
        <h1>Вход</h1>
        <label>Логин</label>
        <input value={loginValue} onChange={(e) => setLoginValue(e.target.value)} autoComplete="username" />
        <label>Пароль</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        <div className="err">{err}</div>
        <button className="btn" type="submit">Войти</button>
        <Link className="btn secondary" href="/register">Регистрация</Link>
        <Link className="btn secondary" href="/install">Скачать приложение</Link>
      </form>
    </div>
  );
}
