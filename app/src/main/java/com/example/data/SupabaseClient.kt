package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight Supabase REST client built on OkHttp (already a project
 * dependency — no extra SDK needed).
 *
 * Covers the three Supabase surfaces a mobile client needs:
 *  - PostgREST  : {SUPABASE_URL}/rest/v1/{table}        (database CRUD)
 *  - Storage    : {SUPABASE_URL}/storage/v1/object/...   (media upload)
 *  - (Auth)     : wire up Supabase Auth or keep your own auth later
 *
 * Every call is suspend + IO-dispatched and returns parsed org.json
 * values, so callers never deal with raw HTTP.
 */
object SupabaseClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun authHeaders(accessToken: String? = null): Map<String, String> {
        // User access token (Supabase Auth) when present, anon key otherwise.
        val bearer = accessToken ?: SupabaseConfig.SUPABASE_ANON_KEY
        return mapOf(
            "apikey" to SupabaseConfig.SUPABASE_ANON_KEY,
            "Authorization" to "Bearer $bearer"
        )
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = "${SupabaseConfig.SUPABASE_URL}$path"
        return if (query.isEmpty()) base else {
            base + "?" + query.entries.joinToString("&") { "${it.key}=${it.value}" }
        }
    }

    /**
     * Generic PostgREST request.
     *
     * @param method HTTP verb: "GET", "POST", "PATCH", "DELETE"
     * @param path   e.g. "/rest/v1/messages"
     * @param query  URL query params, e.g. mapOf("select" to "*", "club_id" to "eq.123")
     * @param body   optional JSON body (POST/PATCH)
     * @param accessToken Supabase Auth user JWT — sent as the Bearer token
     *                    so RLS policies evaluate the signed-in user.
     * @param prefer optional PostgREST `Prefer` header value, e.g.
     *               "return=minimal" or "resolution=merge-duplicates"
     * @return raw response body string
     */
    suspend fun rest(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
        accessToken: String? = null,
        prefer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(buildUrl(path, query))
            .apply { authHeaders(accessToken).forEach { (k, v) -> header(k, v) } }

        if (prefer != null) builder.header("Prefer", prefer)

        when (method.uppercase()) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(JSON))
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(JSON))
            "DELETE" -> builder.delete()
            else -> builder.get()
        }

        http.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Supabase ${method} $path failed: HTTP ${response.code} — $responseBody"
                )
            }
            responseBody
        }
    }

    /**
     * Uploads a binary file to a Supabase Storage bucket.
     *
     * @param bucket      bucket name (see [SupabaseConfig])
     * @param objectPath  path inside the bucket, e.g. "users/u123/avatar.jpg"
     * @param bytes       file content
     * @param contentType MIME type, e.g. "image/jpeg"
     * @param accessToken Supabase Auth user JWT — required for buckets whose
     *                    upload policies demand an authenticated user
     * @return the storage key of the uploaded object, or null on failure
     */
    suspend fun storageUpload(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String,
        accessToken: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$objectPath")
                .apply { authHeaders(accessToken).forEach { (k, v) -> header(k, v) } }
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string().orEmpty())
                    .optString("Key")
                    .ifEmpty { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Public CDN URL for an object stored in a public bucket. */
    fun storagePublicUrl(bucket: String, objectPath: String): String =
        "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/$bucket/$objectPath"

    /** Convenience: parse a REST response as a JSON array. */
    fun parseArray(raw: String): JSONArray = JSONArray(raw.ifEmpty { "[]" })

    /**
     * Server wall-clock sample in epoch-ms, read from the HTTP `Date` response
     * header of a lightweight HEAD request (Ludo timer sync, v3 PRD §29).
     * Clients compute  offset = serverTime() - System.currentTimeMillis()
     * and apply it when converting server deadlines to local epoch-ms.
     */
    suspend fun serverTime(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(
                    buildUrl(
                        "/rest/v1/${SupabaseConfig.TABLE_LUDO_MATCHES}",
                        mapOf("select" to "id", "limit" to "1")
                    )
                )
                .apply { authHeaders(null).forEach { (k, v) -> header(k, v) } }
                .head()
                .build()
            http.newCall(request).execute().use { response ->
                response.headers.getDate("Date")?.time
            }
        }.getOrNull()
    }

    /**
     * Invokes a Supabase Edge Function (Deno) deployed under
     * `{SUPABASE_URL}/functions/v1/{name}` — e.g. the server-authoritative
     * Ludo Arena dice roll / move validation (v2.1 §3.2.2 / §3.2.4).
     *
     * @param name        function folder name, e.g. "roll_ludo_dice"
     * @param payload     JSON-serializable request body
     * @param accessToken Supabase Auth user JWT (identifies the caller
     *                    server-side); anon key is used when null.
     * @return the function's parsed JSON response object
     */
    suspend fun functions(
        name: String,
        payload: Map<String, Any?> = emptyMap(),
        accessToken: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { payload.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/functions/v1/$name")
            .apply {
                authHeaders(accessToken).forEach { (k, v) -> header(k, v) }
                header("Content-Type", "application/json")
            }
            .post(body.toString().toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Edge function $name failed: HTTP ${response.code} — $responseBody"
                )
            }
            JSONObject(responseBody.ifEmpty { "{}" })
        }
    }
}
