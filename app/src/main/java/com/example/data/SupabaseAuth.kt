package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ============================================================
 *  SUPABASE AUTH — Email/Password + Google OAuth (REST)
 * ============================================================
 *
 * Real Supabase Auth integration over the platform REST API:
 *
 *   POST /auth/v1/signup                   (email + password registration)
 *   POST /auth/v1/token?grant_type=password   (login)
 *   POST /auth/v1/token?grant_type=refresh_token (session refresh)
 *   GET  /auth/v1/user                    (session validation)
 *   POST /auth/v1/logout                   (revoke session server-side)
 *   POST /auth/v1/recover                  (password reset email)
 *   GET  /auth/v1/authorize?provider=google&redirect_to=…  (OAuth)
 *
 * Sessions are persisted locally (SharedPreferences) so the correct
 * screen appears instantly on cold start without a network round-trip,
 * then validated/refreshed in the background.
 *
 * No passwords are ever stored — only Supabase tokens.
 */
object SupabaseAuth {

    /** Deep-link scheme Supabase redirects back to after Google OAuth. */
    const val OAUTH_REDIRECT_URI = "quicky://auth-callback"

    data class AuthSession(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val email: String,
        val expiresAtMillis: Long
    )

    data class AuthResult(
        val success: Boolean,
        val session: AuthSession? = null,
        /** Human-readable error for the UI; null on success. */
        val errorMessage: String? = null,
        /** True when signup succeeded but an email confirmation is required. */
        val needsEmailConfirmation: Boolean = false
    )

    private const val PREFS = "quicky_auth"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_EXPIRES_AT = "expires_at"

    // ------------------------------------------------------------
    // LOCAL SESSION PERSISTENCE
    // ------------------------------------------------------------

    fun readSession(context: Context): AuthSession? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        AuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = prefs.getString(KEY_USER_ID, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L)
        )
    }.getOrNull()

    fun writeSession(context: Context, session: AuthSession) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCESS, session.accessToken)
                .putString(KEY_REFRESH, session.refreshToken)
                .putString(KEY_USER_ID, session.userId)
                .putString(KEY_EMAIL, session.email)
                .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
                .apply()
        }
    }

    fun clearSession(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    // ------------------------------------------------------------
    // REST CALLS (all suspend + IO, errors surfaced as AuthResult)
    // ------------------------------------------------------------

    private fun http(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun parseError(body: String): String {
        val msg = runCatching { JSONObject(body).optString("msg") }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: body.take(180).ifEmpty { "Unknown error" }
    }

    private fun sessionFromJson(json: JSONObject): AuthSession {
        val user = json.optJSONObject("user") ?: JSONObject()
        return AuthSession(
            accessToken = json.optString("access_token"),
            refreshToken = json.optString("refresh_token"),
            userId = user.optString("id").ifEmpty { json.optString("user_id") },
            email = user.optString("email").ifEmpty { json.optString("email") },
            expiresAtMillis = System.currentTimeMillis() +
                    json.optLong("expires_in", 3600) * 1000L
        )
    }

    /** Creates a Supabase Auth account (email + password). */
    suspend fun signUp(context: Context, email: String, password: String): AuthResult {
        if (!SupabaseConfig.isConfigured) {
            return AuthResult(false, errorMessage = "Supabase is not configured yet.")
        }
        return runCatching {
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .toString()
            val raw = httpCall("/auth/v1/signup", body)
            val json = JSONObject(raw.ifEmpty { "{}" })
            val session = json.optJSONObject("session")
            if (session != null && !session.optString("access_token").isNullOrEmpty()) {
                val parsed = sessionFromJson(json)
                writeSession(context, parsed)
                initializeProfileRecord(parsed)
                AuthResult(true, session = parsed)
            } else {
                // Supabase returns no session when "Confirm email" is enabled.
                AuthResult(true, needsEmailConfirmation = true)
            }
        }.getOrElse { AuthResult(false, errorMessage = parseThrowable(it)) }
    }

    /** Logs in with email + password and persists the session. */
    suspend fun signIn(context: Context, email: String, password: String): AuthResult {
        if (!SupabaseConfig.isConfigured) {
            return AuthResult(false, errorMessage = "Supabase is not configured yet.")
        }
        return runCatching {
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .toString()
            val raw = httpCall("/auth/v1/token?grant_type=password", body)
            val parsed = sessionFromJson(JSONObject(raw))
            writeSession(context, parsed)
            AuthResult(true, session = parsed)
        }.getOrElse { AuthResult(false, errorMessage = parseThrowable(it)) }
    }

    /**
     * Validates the persisted access token against the auth server and
     * refreshes it when expired. Returns the live session or null.
     */
    suspend fun restoreSession(context: Context): AuthSession? {
        val stored = readSession(context) ?: return null
        return runCatching {
            if (stored.expiresAtMillis - System.currentTimeMillis() > 60_000L) {
                // Still (probably) valid — verify it is actually alive.
                val request = okhttp3.Request.Builder()
                    .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/user")
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${stored.accessToken}")
                    .get()
                    .build()
                http().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val user = JSONObject(response.body?.string().orEmpty())
                        stored.copy(
                            userId = user.optString("id").ifEmpty { stored.userId },
                            email = user.optString("email").ifEmpty { stored.email }
                        )
                    } else {
                        refresh(context, stored) ?: clearSession(context).let { null }
                    }
                }
            } else {
                refresh(context, stored) ?: clearSession(context).let { null }
            }
        }.getOrElse {
            null
        }
    }

    private suspend fun refresh(context: Context, stored: AuthSession): AuthSession? =
        runCatching {
            val body = JSONObject().put("refresh_token", stored.refreshToken).toString()
            val raw = httpCall("/auth/v1/token?grant_type=refresh_token", body)
            val parsed = sessionFromJson(JSONObject(raw))
            writeSession(context, parsed)
            parsed
        }.getOrNull()

    /** Sends the password-recovery email. */
    suspend fun requestPasswordReset(email: String): AuthResult {
        if (!SupabaseConfig.isConfigured) {
            return AuthResult(false, errorMessage = "Supabase is not configured yet.")
        }
        return runCatching {
            val body = JSONObject().put("email", email.trim()).toString()
            httpCall("/auth/v1/recover", body)
            AuthResult(true)
        }.getOrElse { AuthResult(false, errorMessage = parseThrowable(it)) }
    }

    /**
     * Signs out: revokes the session server-side first, then clears all
     * locally cached tokens. Account data, entitlements and chat history
     * are untouched (they live in the database, not on the device).
     */
    suspend fun signOut(context: Context): Boolean {
        val stored = readSession(context)
        runCatching {
            if (stored != null) {
                val request = okhttp3.Request.Builder()
                    .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/logout")
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${stored.accessToken}")
                    .post(okhttp3.RequestBody.EMPTY)
                    .build()
                http().newCall(request).execute().use { /* best effort */ }
            }
        }
        clearSession(context)
        return true
    }

    // ------------------------------------------------------------
    // GOOGLE OAUTH (Supabase authorize endpoint + deep-link return)
    // ------------------------------------------------------------

    /**
     * Supabase Google OAuth entry URL. Opened in the browser via an
     * ACTION_VIEW intent; Supabase redirects back to
     * quicky://auth-callback#access_token=…&refresh_token=…
     */
    fun googleOAuthUrl(): String =
        "${SupabaseConfig.SUPABASE_URL}/auth/v1/authorize?provider=google" +
                "&redirect_to=" + Uri.encode(OAUTH_REDIRECT_URI)

    /**
     * Parses the OAuth redirect (tokens arrive in the URL fragment for
     * the implicit flow). Returns null when the redirect has no session.
     */
    fun parseOAuthRedirect(uri: Uri): AuthSession? {
        val fragment = uri.fragment ?: return null
        val map = fragment.split("&")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) pair.substring(0, idx) to pair.substring(idx + 1) else null
            }
            .toMap()
        val access = map["access_token"] ?: return null
        val refresh = map["refresh_token"] ?: return null
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = map["user_id"].orEmpty(),
            email = map["email"].orEmpty(),
            expiresAtMillis = System.currentTimeMillis() +
                    (map["expires_in"]?.toLongOrNull() ?: 3600L) * 1000L
        )
    }

    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------

    private suspend fun httpCall(pathAndQuery: String, jsonBody: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}$pathAndQuery")
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            http().newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseError(body))
                }
                body
            }
        }

    private fun parseThrowable(t: Throwable): String = when {
        t is IllegalStateException -> t.message ?: "Authentication failed"
        t.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
            "No internet connection. Please try again."
        else -> "Authentication failed. Please check your details and try again."
    }

    /**
     * Ensures a `profiles` row exists for a freshly created account
     * (id = auth user id, onboarding not yet completed). Fail-safe: the
     * database also has a handle_new_user() trigger doing the same.
     */
    private suspend fun initializeProfileRecord(session: AuthSession) {
        runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_PROFILES}",
                // Upsert: creates the row if the DB trigger has not yet run.
                prefer = "resolution=merge-duplicates,return=minimal",
                body = JSONObject()
                    .put("id", session.userId)
                    .put("name", session.email.substringBefore("@"))
                    .put("onboarding_completed", false)
                    .put("onboarding_step", 1)
                    .toString(),
                accessToken = session.accessToken
            )
        }
    }
}
