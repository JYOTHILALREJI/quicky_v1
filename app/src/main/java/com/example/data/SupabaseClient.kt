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
        val cleanPath = objectPath
            .removePrefix("$bucket/")
            .removePrefix("/$bucket/")
            .removePrefix("/")
        try {
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$cleanPath")
                .apply { authHeaders(accessToken).forEach { (k, v) -> header(k, v) } }
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .post(bytes.toRequestBody(contentType.toMediaType()))
                .build()

            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    val keyFromJson = runCatching {
                        JSONObject(bodyString).optString("Key")
                    }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
                    val resolvedKey = keyFromJson
                        ?.removePrefix("$bucket/")
                        ?.removePrefix("/") ?: cleanPath
                    return@withContext resolvedKey
                } else if (response.code == 404 || response.code == 400 || response.code == 405) {
                    // Fallback to PUT if POST is rejected by storage version
                    val putRequest = Request.Builder()
                        .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$cleanPath")
                        .apply { authHeaders(accessToken).forEach { (k, v) -> header(k, v) } }
                        .header("Content-Type", contentType)
                        .header("x-upsert", "true")
                        .put(bytes.toRequestBody(contentType.toMediaType()))
                        .build()
                    val putResp = http.newCall(putRequest).execute()
                    if (putResp.isSuccessful) {
                        return@withContext cleanPath
                    }
                }
                android.util.Log.w(
                    "QuickyStorage",
                    "Upload failed ($bucket/$cleanPath): HTTP ${response.code} — " +
                            response.body?.string().orEmpty().take(300)
                )
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("QuickyStorage", "Upload threw ($bucket/$cleanPath): ${e.message}")
            null
        }
    }

    /** Public CDN URL for an object stored in a public bucket. */
    fun storagePublicUrl(bucket: String, objectPath: String): String {
        val cleanPath = objectPath
            .removePrefix("$bucket/")
            .removePrefix("/$bucket/")
            .removePrefix("/")
        return "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/$bucket/$cleanPath"
    }

    /**
     * Downloads an object from a PRIVATE bucket (v3.3.4 club voice notes, snaps).
     * The request rides the Supabase Auth user JWT so the bucket's
     * `authenticated` read policy applies; fallback queries anon and public CDN.
     *
     * @return the raw bytes, or null on any failure (caller keeps the UI).
     */
    suspend fun storageDownload(
        bucket: String,
        objectPath: String,
        accessToken: String? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        // 0. If already a full URL, fetch directly
        if (objectPath.startsWith("http://") || objectPath.startsWith("https://")) {
            return@withContext runCatching {
                val req = Request.Builder()
                    .url(objectPath)
                    .apply { authHeaders(accessToken).forEach { (k, v) -> header(k, v) } }
                    .get()
                    .build()
                http.newCall(req).execute().use { r ->
                    if (r.isSuccessful) r.body?.bytes() else null
                }
            }.getOrNull()
        }

        val cleanPath = objectPath
            .substringAfter("/$bucket/", objectPath)
            .removePrefix("$bucket/")
            .removePrefix("/$bucket/")
            .removePrefix("/")

        runCatching {
            // 1. Try authenticated endpoint for private buckets
            val authReq = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/authenticated/$bucket/$cleanPath")
                .apply { authHeaders(accessToken).forEach { (k, v) -> header(k, v) } }
                .get()
                .build()
            val authBytes = http.newCall(authReq).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
            if (authBytes != null && authBytes.isNotEmpty()) return@runCatching authBytes

            // 2. Try signed URL generation (bypasses RLS issues for private buckets)
            val signBody = JSONObject().put("expiresIn", 300).toString()
            val signReq = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/sign/$bucket/$cleanPath")
                .apply {
                    authHeaders(accessToken).forEach { (k, v) -> header(k, v) }
                    header("Content-Type", "application/json")
                }
                .post(signBody.toRequestBody(JSON))
                .build()
            val signedPath = runCatching {
                http.newCall(signReq).execute().use { r ->
                    if (r.isSuccessful) {
                        val body = r.body?.string().orEmpty()
                        val obj = JSONObject(body)
                        obj.optString("signedURL").ifBlank { obj.optString("signedUrl") }
                    } else null
                }
            }.getOrNull()

            if (!signedPath.isNullOrBlank()) {
                val fullSignedUrl = if (signedPath.startsWith("http")) signedPath
                else "${SupabaseConfig.SUPABASE_URL}/storage/v1$signedPath"
                val signedDownloadReq = Request.Builder().url(fullSignedUrl).get().build()
                val signedBytes = http.newCall(signedDownloadReq).execute().use { r ->
                    if (r.isSuccessful) r.body?.bytes() else null
                }
                if (signedBytes != null && signedBytes.isNotEmpty()) return@runCatching signedBytes
            }

            // 3. Standard /object/$bucket/$cleanPath endpoint
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$cleanPath")
                .apply { authHeaders(accessToken).forEach { (k, v) -> header(k, v) } }
                .get()
                .build()
            val bytes = http.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    android.util.Log.w(
                        "QuickyStorage",
                        "Download failed ($bucket/$cleanPath): HTTP ${response.code}"
                    )
                    null
                }
            }
            if (bytes != null && bytes.isNotEmpty()) return@runCatching bytes

            // 4. Fallback with anon key
            val anonReq = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$cleanPath")
                .apply { authHeaders(null).forEach { (k, v) -> header(k, v) } }
                .get()
                .build()
            val anonBytes = http.newCall(anonReq).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
            if (anonBytes != null && anonBytes.isNotEmpty()) return@runCatching anonBytes

            // 5. Fallback to public CDN URL
            val pubReq = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/$bucket/$cleanPath")
                .get()
                .build()
            http.newCall(pubReq).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        }.getOrNull()
    }

    /**
     * Deletes an object from a bucket. v3.3.7: used by the view-once snap
     * flow — when a receiver opens a snap, the stored image is destroyed so
     * it can never be fetched again (the RPC `mark_snap_viewed` is the
     * authoritative path; this direct delete is the fallback).
     *
     * @return true when the server acknowledged the delete.
     */
    suspend fun storageDelete(
        bucket: String,
        objectPath: String,
        accessToken: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanPath = objectPath
            .removePrefix("$bucket/")
            .removePrefix("/$bucket/")
            .removePrefix("/")
        runCatching {
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$cleanPath")
                .apply { authHeaders(accessToken).forEach { (k, v) -> header(k, v) } }
                .delete()
                .build()
            http.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

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
