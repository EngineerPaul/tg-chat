package ru.daystream.calc.net

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Request
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Базовый адрес API.
 */
const val BASE = "https://daystream.ru/extra/chat/api/"

/**
 * Тело текстового сообщения.
 */
data class TextSend(val text: String, val reply_to_id: Long? = null)

/**
 * REST-методы чата.
 */
interface ChatApi {
    /** Войти логином и паролем. */
    @POST("auth/login")
    suspend fun login(@Body body: Map<String, String>): Map<String, Any>

    /** Обновить access. */
    @POST("auth/refresh")
    suspend fun refresh(@Body body: Map<String, String>): Map<String, String>

    /** Выйти. */
    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") auth: String, @Body body: Map<String, String>): Map<String, Any>

    /** Текущий пользователь. */
    @GET("users/me")
    suspend fun me(@Header("Authorization") auth: String): Map<String, Any>

    /** Привязать FCM. */
    @POST("users/me/fcm")
    suspend fun fcm(@Header("Authorization") auth: String, @Body body: Map<String, String>): Map<String, Any>

    /** Список диалогов. */
    @GET("dialogs")
    suspend fun dialogs(@Header("Authorization") auth: String): List<Map<String, Any>>

    /** Открыть диалог по логину. */
    @POST("dialogs")
    suspend fun openDialog(@Header("Authorization") auth: String, @Body body: Map<String, String>): Map<String, Any>

    /** История. */
    @GET("dialogs/{id}/messages")
    suspend fun messages(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 50,
    ): Map<String, Any>

    /** Текст. Тело ответа — сырой JSON: в Map Gson падает на null. */
    @POST("dialogs/{id}/messages")
    suspend fun sendText(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body body: TextSend,
    ): okhttp3.ResponseBody

    /** Прочитать. */
    @POST("dialogs/{id}/read")
    suspend fun read(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: Map<String, Any?>): Map<String, Any>

    /** Правка. */
    @PATCH("messages/{id}")
    suspend fun edit(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: Map<String, String>): Map<String, Any>

    /** Удаление у обоих. */
    @DELETE("messages/{id}")
    suspend fun delete(@Header("Authorization") auth: String, @Path("id") id: Long): Map<String, Any>

    /** Ник собеседника. */
    @PATCH("dialogs/{id}/alias")
    suspend fun alias(@Header("Authorization") auth: String, @Path("id") id: Long, @Body body: Map<String, String>): Map<String, Any>

    /** Медиа-альбом. */
    @Multipart
    @POST("dialogs/{id}/media")
    suspend fun sendMedia(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Part files: List<MultipartBody.Part>,
        @Part("caption") caption: RequestBody,
        @Part("already_compressed") compressed: RequestBody,
    ): Map<String, Any>

    /** Файл. */
    @Multipart
    @POST("dialogs/{id}/file")
    suspend fun sendFile(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Part file: MultipartBody.Part,
        @Part("caption") caption: RequestBody,
        @Part("gzip_flag") gzip: RequestBody,
        @Part("orig_size") orig: RequestBody,
    ): Map<String, Any>
}

/**
 * Собрать Retrofit-клиент с длинным таймаутом на файлы.
 */
fun createApi(): ChatApi {
    val ok = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(BASE)
        .client(ok)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ChatApi::class.java)
}

/**
 * Открыть WebSocket с access в query.
 * @param access JWT
 * @param listener обработчик кадров
 */
fun openWs(access: String, listener: WebSocketListener): WebSocket {
    val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    val req = Request.Builder().url("wss://daystream.ru/extra/chat/api/ws?token=$access").build()
    return client.newWebSocket(req, listener)
}
