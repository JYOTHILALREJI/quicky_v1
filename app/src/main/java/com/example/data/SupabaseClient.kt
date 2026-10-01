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

    private fun authHeaders(): Map<String, String> = mapOf(
        "apikey" to SupabaseConfig.SUPABASE_ANON_KEY,
        "Authorization" to "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}"
    )

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
     * @return raw response body string
     */
    suspend fun rest(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(buildUrl(path, query))
            .apply { authHeaders().forEach { (k, v) -> header(k, v) } }

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
     * @return the storage key of the uploaded object, or null on failure
     */
    suspend fun storageUpload(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$objectPath")
                .apply { authHeaders().forEach { (k, v) -> header(k, v) } }
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
}
