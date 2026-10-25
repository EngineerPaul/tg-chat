package ru.daystream.calc.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.TokenClient
import kotlin.coroutines.resume

/**
 * Отправить текущий FCM-токен на сервер, если сессия жива.
 */
suspend fun registerFcm(store: SessionStore) {
    if (!store.hasSession()) return
    val token = firebaseToken() ?: return
    if (token.isBlank()) return
    try {
        TokenClient(store).call { auth -> fcm(auth, mapOf("token" to token)) }
    } catch (_: Exception) { }
}

private suspend fun firebaseToken(): String? = suspendCancellableCoroutine { cont ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { cont.resume(it) }
        .addOnFailureListener { cont.resume(null) }
}
