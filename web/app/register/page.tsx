"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { register } from "@/lib/api";

/**
 * Форма регистрации на сайте. Android её не показывает.
 */
export default function RegisterPage() {
  const router = useRouter();
  const [loginValue, setLoginValue] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState("");

  /**
   * Создать аккаунт и сразу войти.
   * @param e событие формы
   */
  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErr("");
    try {
      await register(loginValue, password);
      router.replace("/chat");
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "Ошибка");
    }
  }

  return (
    <div className="page-center">
      <form className="card" onSubmit={onSubmit}>
        <h1>Регистрация</h1>
        <label>Логин (3–30, буквы и цифры)</label>
        <input value={loginValue} onChange={(e) => setLoginValue(e.target.value)} />
        <label>Пароль (3–30, буквы, цифры, пробел)</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <div className="err">{err}</div>
        <button className="btn" type="submit">Создать</button>
        <Link className="btn secondary" href="/login">Уже есть аккаунт</Link>
      </form>
    </div>
  );
}
