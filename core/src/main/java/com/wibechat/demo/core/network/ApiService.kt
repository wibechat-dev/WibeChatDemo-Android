package com.wibechat.demo.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Any non-2xx response. [message] is the server's `error` field when present. */
class ApiException(val statusCode: Int, override val message: String) : Exception(message)

/**
 * Thin JSON client for the Cloud Functions. Attaches the signed-in user's
 * Firebase ID token as `Authorization: Bearer …` and turns non-2xx responses
 * into [ApiException]. Every backend call goes through here.
 *
 * @param idTokenProvider returns the current Firebase ID token, or null when signed out.
 */
class ApiService(
    private val idTokenProvider: suspend () -> String?,
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun get(
        url: String,
        queryParameters: Map<String, String> = emptyMap(),
        auth: Boolean = true,
    ): JSONObject {
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            queryParameters.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder().url(httpUrl).get().headers(auth).build()
        return execute(request)
    }

    suspend fun post(url: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val payload = (body ?: JSONObject()).toString().toRequestBody(JSON)
        val request = Request.Builder().url(url).post(payload).headers(auth).build()
        return execute(request)
    }

    private suspend fun Request.Builder.headers(auth: Boolean): Request.Builder {
        header("Content-Type", "application/json")
        if (auth) {
            val idToken = idTokenProvider() ?: throw IllegalStateException("Must be signed in to call the API.")
            header("Authorization", "Bearer $idToken")
        }
        return this
    }

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response -> decode(response) }
    }

    private fun decode(response: Response): JSONObject {
        val text = response.body?.string().orEmpty()
        val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        if (!response.isSuccessful) {
            val message = json.optString("error").ifBlank { "Request failed (${response.code})" }
            throw ApiException(response.code, message)
        }
        return json
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
