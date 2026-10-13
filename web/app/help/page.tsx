/**
 * Справка владельца: ссылки, регистрация, Docker, Firebase.
 */
export default function HelpPage() {
  return (
    <div className="help">
      <h1>Справка</h1>
      <ul>
        <li>Чат: <a href="https://daystream.ru/extra/chat">https://daystream.ru/extra/chat</a></li>
        <li>API: <code>/extra/chat/api</code>, WebSocket: <code>wss://daystream.ru/extra/chat/api/ws</code></li>
        <li>Установка: <a href="/extra/chat/install">/extra/chat/install</a></li>
        <li>APK: <a href="/extra/chat/chat.apk">/extra/chat/chat.apk</a></li>
      </ul>
      <h2>Регистрация</h2>
      <p>Форма на сайте или вручную в контейнере:</p>
      <pre>docker compose exec backend python scripts/create_user.py логин пароль</pre>
      <p>В Android регистрации нет, только вход ключом из калькулятора.</p>
      <h2>Телефон</h2>
      <p>Снаружи — калькулятор, чат за ключом (пароль аккаунта). Неизвестный источник. Обновление — тот же пакет поверх.</p>
      <p>Веб не маскируется.</p>
      <h2>Docker</h2>
      <p>Сеть <code>extra_services</code>, сервис nginx <code>chat</code>. Хост проксирует <code>/extra/chat</code> на <code>http://chat:80</code>. Volume SQLite и загрузок — <code>chat_data</code>.</p>
      <h2>Firebase</h2>
      <p>Для push нужен <code>google-services.json</code> в Android и путь к ключу сервера в <code>FIREBASE_CREDENTIALS</code>. Без них чат работает, уведомлений на телефоне нет.</p>
    </div>
  );
}
