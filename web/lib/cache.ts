/**
 * Локальный кэш последних 1000 сообщений диалога в IndexedDB.
 */

const DB = "chat-cache";
const STORE = "messages";

/**
 * Открыть базу кэша.
 */
function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: "id" });
        store.createIndex("dialog_id", "dialog_id");
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

/**
 * Сохранить сообщения и обрезать диалог до 1000 последних.
 * @param dialogId id диалога
 * @param messages пачка сообщений
 */
export async function cacheMessages(dialogId: number, messages: Array<{ id: number; dialog_id: number }>): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    const store = tx.objectStore(STORE);
    for (const m of messages) store.put(m);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  await trimDialog(dialogId);
}

/**
 * Прочитать кэш диалога по возрастанию id.
 * @param dialogId id диалога
 */
export async function loadCached(dialogId: number): Promise<unknown[]> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readonly");
    const idx = tx.objectStore(STORE).index("dialog_id");
    const req = idx.getAll(dialogId);
    req.onsuccess = () => {
      const rows = (req.result as Array<{ id: number }>).sort((a, b) => a.id - b.id);
      resolve(rows);
    };
    req.onerror = () => reject(req.error);
  });
}

/**
 * Оставить только 1000 последних сообщений диалога.
 * @param dialogId id диалога
 */
export async function trimDialog(dialogId: number): Promise<void> {
  const rows = (await loadCached(dialogId)) as Array<{ id: number }>;
  if (rows.length <= 1000) return;
  const drop = rows.slice(0, rows.length - 1000);
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    const store = tx.objectStore(STORE);
    for (const row of drop) store.delete(row.id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

/**
 * Удалить сообщение из кэша.
 * @param id id сообщения
 */
export async function cacheDelete(id: number): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).delete(id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
