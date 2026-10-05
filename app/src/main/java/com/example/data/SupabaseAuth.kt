package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
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
 * Sessions are persisted locally (SharedPreferences) and SURVIVE normal
 * app closure: the persisted session is only ever destroyed when the
 * auth server itself rejects it (explicit sign-out, revocation) — never
 * because of a transient network problem at cold start, which is exactly
 * when apps race the radio coming up.
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
        val needsEmailConfirmation: Boolean = false,
        /**
         * True when a login attempt failed specifically because the email
         * has not been verified yet — the UI then asks for the 6-digit
         * OTP (emailed on signup) alongside the credentials.
         */
        val isEmailNotConfirmed: Boolean = false
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

    /**
     * Parses a Supabase auth response into a session. Handles BOTH wire
     * shapes: tokens flattened at the root (password / refresh-token /
     * verify grants) and tokens nested under a "session" object (the
     * modern signup response).
     */
    private fun sessionFromJson(json: JSONObject): AuthSession {
        val nested = json.optJSONObject("session")
        val source = if (!nested?.optString("access_token").isNullOrEmpty()) nested else json
        val user = source.optJSONObject("user") ?: json.optJSONObject("user") ?: JSONObject()
        return AuthSession(
            accessToken = source.optString("access_token"),
            refreshToken = source.optString("refresh_token"),
            userId = user.optString("id")
                .ifEmpty { source.optString("id").ifEmpty { json.optString("id") } },
            email = user.optString("email")
                .ifEmpty { source.optString("email").ifEmpty { json.optString("email") } },
            expiresAtMillis = System.currentTimeMillis() +
                    source.optLong("expires_in", 3600) * 1000L
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
            // Works for both wire shapes: tokens flattened at the root
            // (confirm-email off) or nested under "session" (modern GoTrue).
            val parsed = sessionFromJson(json)
            if (parsed.accessToken.isNotEmpty()) {
                writeSession(context, parsed)
                initializeProfileRecord(parsed)
                AuthResult(true, session = parsed)
            } else {
                // Supabase returns no session when "Confirm email" is enabled.
                AuthResult(true, needsEmailConfirmation = true)
            }
        }.getOrElse { AuthResult(false, errorMessage = parseThrowable(it)) }
    }

    /**
     * True when a failed request was rejected because the account's email
     * has not been verified yet (password grant returns "Email not
     * confirmed" / "email_not_confirmed" while Confirm email is on).
     */
    private fun isEmailNotConfirmedError(message: String?): Boolean {
        if (message == null) return false
        val normalized = message.lowercase().replace('_', ' ')
        return "email not confirmed" in normalized || "email not verified" in normalized
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
            val parsed = sessionFromJson(JSONObject(raw.ifEmpty { "{}" }))
            if (parsed.accessToken.isEmpty()) {
                throw IllegalStateException(
                    "Authentication failed. Please check your details and try again."
                )
            }
            writeSession(context, parsed)
            AuthResult(true, session = parsed)
        }.getOrElse {
            val message = parseThrowable(it)
            AuthResult(
                success = false,
                errorMessage = message,
                isEmailNotConfirmed = isEmailNotConfirmedError(message)
            )
        }
    }

    /**
     * Verifies the freshly created account with the 6-digit OTP from the
     * "Quicky account creation" email and returns the live session.
     *
     * POST /auth/v1/verify  { "type": "signup", "email": …, "token": … }
     *
     * A successful verification returns a full session JSON (access +
     * refresh token, user with email_confirmed_at set) exactly like the
     * password grant, so the caller can adopt it directly.
     */
    suspend fun verifyOtp(context: Context, email: String, otp: String): AuthResult {
        if (!SupabaseConfig.isConfigured) {
            return AuthResult(false, errorMessage = "Supabase is not configured yet.")
        }
        return runCatching {
            val body = JSONObject()
                .put("type", "signup")
                .put("email", email.trim())
                .put("token", otp.trim())
                .toString()
            val raw = httpCall("/auth/v1/verify", body)
            val parsed = sessionFromJson(JSONObject(raw.ifEmpty { "{}" }))
            if (parsed.accessToken.isEmpty()) {
                throw IllegalStateException("That code didn't match — double-check the 6 digits and try again.")
            }
            writeSession(context, parsed)
            initializeProfileRecord(parsed)
            AuthResult(true, session = parsed)
        }.getOrElse { AuthResult(false, errorMessage = parseThrowable(it)) }
    }

    /**
     * Re-sends the signup verification email with a fresh 6-digit OTP.
     *
     * POST /auth/v1/resend  { "type": "signup", "email": … }
     */
    suspend fun resendOtp(email: String): AuthResult {
        if (!SupabaseConfig.isConfigured) {
            return AuthResult(false, errorMessage = "Supabase is not configured yet.")
        }
        return runCatching {
            val body = JSONObject()
                .put("type", "signup")
                .put("email", email.trim())
                .toString()
            httpCall("/auth/v1/resend", body)
            AuthResult(true)
        }.getOrElse { AuthResult(false, errorMessage = parseThrowable(it)) }
    }

    /** Outcome of talking to the auth server: OK / rejected / unreachable. */
    private enum class TokenStatus { VALID, REJECTED, NETWORK }

    /** Result of a refresh attempt (network failure must NOT destroy the session). */
    private class RefreshResult(val session: AuthSession?, val networkError: Boolean = false)

    /**
     * Validates the persisted access token against the auth server and
     * refreshes it when expired — while making the session SURVIVE app
     * closure and transient connectivity problems:
     *
     *  - Access token locally unexpired → confirmed against /auth/v1/user.
     *  - Expired or rejected → refreshed with the stored refresh token,
     *    retried once for the cold-start "radio not up yet" window.
     *  - The persisted session is ONLY cleared when the auth server
     *    definitively rejects it (revocation / password changed elsewhere).
     *    Network failures never sign the user out — the refresh token was
     *    not consumed by a failed call, so the next launch retries safely.
     */
    suspend fun restoreSession(context: Context): AuthSession? {
        val stored = readSession(context) ?: return null
        // Defensive: never adopt corrupt/empty persisted tokens.
        if (stored.accessToken.isEmpty() || stored.refreshToken.isEmpty()) {
            clearSession(context)
            return null
        }

        // 1) Access token (probably) still valid — verify with the server.
        if (stored.expiresAtMillis - System.currentTimeMillis() > 60_000L) {
            when (validateAccessToken(stored)) {
                TokenStatus.VALID -> {
                    return stored
                }
                TokenStatus.NETWORK -> {
                    // Unreachable (e.g. the app started before the radio
                    // connected). The token is locally unexpired — trust it
                    // optimistically; the in-app watcher re-verifies later.
                    return stored
                }
                TokenStatus.REJECTED -> Unit // fall through and refresh
            }
        }

        // 2) Expired or server-rejected: refresh, retrying once for
        //    cold-start network hiccups.
        var result = refresh(context, stored)
        if (result.session == null && result.networkError) {
            delay(1_500L)
            result = refresh(context, stored)
        }

        return when {
            result.session != null -> result.session
            result.networkError ->
                // Still unreachable — keep the persisted session instead of
                // forcing a re-login; the next launch retries the refresh.
                stored
            else -> {
                // The auth server itself rejected the refresh token: a real
                // sign-out, not a connectivity problem.
                clearSession(context)
                null
            }
        }
    }

    /** GET /auth/v1/user — confirms the access token is still alive. */
    private suspend fun validateAccessToken(stored: AuthSession): TokenStatus =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/user")
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${stored.accessToken}")
                    .get()
                    .build()
                http().newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> TokenStatus.VALID
                        response.code >= 500 -> TokenStatus.NETWORK
                        else -> TokenStatus.REJECTED
                    }
                }
            }.getOrElse {
                if (it is IOException) TokenStatus.NETWORK else TokenStatus.REJECTED
            }
        }

    /**
     * Exchanges the stored refresh token for a fresh session. Distinguishes
     * "server rejected the token" (definitive sign-out) from "network
     * unreachable" (transient — must never destroy the local session).
     */
    private suspend fun refresh(context: Context, stored: AuthSession): RefreshResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("refresh_token", stored.refreshToken).toString()
                val request = Request.Builder()
                    .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                http().newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> {
                            val parsed = sessionFromJson(JSONObject(raw.ifEmpty { "{}" }))
                            if (parsed.accessToken.isEmpty()) {
                                RefreshResult(null, networkError = false)
                            } else {
                                writeSession(context, parsed)
                                RefreshResult(parsed)
                            }
                        }
                        response.code >= 500 -> RefreshResult(null, networkError = true)
                        else -> RefreshResult(null, networkError = false)
                    }
                }
            }.getOrElse {
                RefreshResult(null, networkError = it is IOException)
            }
        }

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
                    // OkHttp 4.x has no prebuilt empty body — make one from an empty byte array.
                    .post(ByteArray(0).toRequestBody(null))
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
