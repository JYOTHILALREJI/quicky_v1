package com.example.data

import com.example.model.GameDefinition
import com.example.model.GeoSuggestion
import com.example.model.OnboardingDraft
import com.example.model.TruthOrDarePrompt
import com.example.model.UserProfile
import com.example.model.VisibilityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Data-access layer for Supabase (database + storage).
 *
 * While credentials are placeholders (see [SupabaseConfig]) every
 * function short-circuits and the app runs in a clean, empty state.
 *
 * Table shapes (create them by running supabase/schema.sql):
 *
 *  games:           id TEXT PK, name TEXT, description TEXT,
 *                   is_free BOOLEAN, tag TEXT, players_count TEXT
 *
 *  game_prompts:    id TEXT PK, category TEXT, type TEXT,
 *                   text TEXT, difficulty TEXT
 *
 *  interests:       id INT PK, name TEXT UNIQUE   (system catalog)
 *  hobbies:         id INT PK, name TEXT UNIQUE   (system catalog)
 *
 *  profiles:        see supabase/schema.sql — written by onboarding,
 *                   read by fetchProfile()
 *
 *  user_interests:  user_id UUID, interest TEXT, source TEXT
 */
object SupabaseRepository {

    /** A profiles row + its onboarding flags (not part of UserProfile). */
    data class RemoteProfile(
        val profile: UserProfile,
        val onboardingCompleted: Boolean,
        val onboardingStep: Int
    )

    fun isConfigured(): Boolean = SupabaseConfig.isConfigured

    // ==============================================================
    // READS
    // ==============================================================

    /**
     * Loads the games catalog from the `games` table.
     * Returns an empty list when Supabase is not configured or the
     * table is missing — callers should keep their bundled defaults.
     */
    suspend fun fetchGamesCatalog(): List<GameDefinition> {
        if (!isConfigured()) return emptyList()

        return runCatching {
            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_GAMES}",
                query = mapOf("select" to "*")
            )

            val jsonArray = SupabaseClient.parseArray(raw)

            buildList {
                for (index in 0 until jsonArray.length()) {
                    val row = jsonArray.optJSONObject(index) ?: continue

                    add(
                        GameDefinition(
                            id = row.optString("id"),
                            name = row.optString("name"),
                            description = row.optString("description"),
                            isFree = row.optBoolean("is_free", false),
                            tag = row.optString("tag", "PREMIUM"),
                            playersCount = row.optString("players_count", "2 Players")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Loads Truth or Dare prompts from the `game_prompts` table.
     * Returns an empty list when Supabase is not configured or the
     * table is missing — callers should keep their bundled defaults.
     */
    suspend fun fetchTruthOrDarePrompts(): List<TruthOrDarePrompt> {
        if (!isConfigured()) return emptyList()

        return runCatching {
            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_GAME_PROMPTS}",
                query = mapOf("select" to "*")
            )

            val jsonArray = SupabaseClient.parseArray(raw)

            buildList {
                for (index in 0 until jsonArray.length()) {
                    val row = jsonArray.optJSONObject(index) ?: continue

                    add(
                        TruthOrDarePrompt(
                            id = row.optString("id"),
                            category = row.optString("category", "Flirty"),
                            type = row.optString("type", "TRUTH"),
                            text = row.optString("text"),
                            difficulty = row.optString("difficulty", "Medium")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Loads the admin-manageable system interest catalog (`interests` table). */
    suspend fun fetchInterestCatalog(): List<String> =
        fetchNameCatalog(SupabaseConfig.TABLE_INTEREST_CATALOG)

    /** Loads the admin-manageable hobby catalog (`hobbies` table). */
    suspend fun fetchHobbyCatalog(): List<String> =
        fetchNameCatalog(SupabaseConfig.TABLE_HOBBY_CATALOG)

    private suspend fun fetchNameCatalog(table: String): List<String> {
        if (!isConfigured()) return emptyList()
        return runCatching {
            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/$table",
                query = mapOf("select" to "name", "order" to "name.asc")
            )
            val rows = SupabaseClient.parseArray(raw)
            buildList {
                for (i in 0 until rows.length()) {
                    rows.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Loads the signed-in user's profiles row and maps it into a
     * [UserProfile]. Returns null when the row is missing or the
     * request fails — callers should then treat onboarding as fresh.
     */
    suspend fun fetchProfile(
        userId: String,
        accessToken: String?
    ): RemoteProfile? {
        if (!isConfigured()) return null
        return runCatching {
            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_PROFILES}",
                query = mapOf("select" to "*", "id" to "eq.$userId"),
                accessToken = accessToken
            )
            val rows = SupabaseClient.parseArray(raw)
            val row = rows.optJSONObject(0) ?: return@runCatching null

            RemoteProfile(
                profile = UserProfile(
                    id = row.optString("id").ifEmpty { userId },
                    name = row.optString("name"),
                    age = row.optInt("age", 21),
                    bio = row.optString("bio"),
                    city = row.optString("city"),
                    distanceKm = row.optInt("distance_km", 0),
                    relationshipIntent = row.optString("relationship_intent"),
                    occupation = row.optString("occupation"),
                    industry = row.optString("industry"),
                    education = row.optString("education"),
                    educationLevel = row.optString("education_level"),
                    height = row.optString("height"),
                    gender = row.optString("gender"),
                    interestedIn = row.optString("interested_in", "Everyone"),
                    languages = row.optJSONArray("languages").toStringList().ifEmpty { listOf("English") },
                    isVerified = row.optBoolean("is_verified", false),
                    isOnline = row.optBoolean("is_online", false),
                    characterBadge = row.optString("character_badge", "The Explorer"),
                    characterDescription = row.optString("character_description"),
                    showCharacterBadge = row.optBoolean("show_character_badge", true),
                    photoUris = row.optJSONArray("photo_urls").toStringList(),
                    interests = row.optJSONArray("interests").toStringList(),
                    hobbies = row.optJSONArray("hobbies").toStringList(),
                    lookingFor = row.optJSONArray("looking_for").toStringList(),
                    heightCm = if (row.isNull("height_cm")) null else row.optInt("height_cm").takeIf { it > 0 },
                    weightKg = if (row.isNull("weight_kg")) null else row.optDouble("weight_kg").takeIf { it > 0 }?.toFloat(),
                    latitude = if (row.isNull("latitude")) null else row.optDouble("latitude").takeIf { !it.isNaN() },
                    longitude = if (row.isNull("longitude")) null else row.optDouble("longitude").takeIf { !it.isNaN() },
                    dateOfBirth = row.optString("date_of_birth").takeIf { it.isNotBlank() },
                    lifestyle = buildMap {
                        row.optJSONObject("lifestyle")?.let { obj ->
                            for (k in obj.keys()) put(k, obj.optString(k))
                        }
                    },
                    fieldVisibility = buildMap {
                        row.optJSONObject("field_visibility")?.let { obj ->
                            for (k in obj.keys()) {
                                put(k, runCatching { VisibilityLevel.valueOf(obj.optString(k)) }
                                    .getOrDefault(VisibilityLevel.EVERYONE))
                            }
                        }
                    },
                    prompts = buildList {
                        row.optJSONArray("prompts")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val p = arr.optJSONObject(i) ?: continue
                                add(com.example.model.ProfilePrompt(p.optString("question"), p.optString("answer")))
                            }
                        }
                    },
                    compatibilityScore = row.optInt("compatibility_score", 85),
                    profileCompletionScore = row.optInt("profile_completion_score", 85)
                ),
                onboardingCompleted = row.optBoolean("onboarding_completed", false),
                onboardingStep = row.optInt("onboarding_step", 1)
            )
        }.getOrNull()
    }

    /** True for interests that are NOT part of the served system catalog. */
    fun isCustomInterest(interest: String, systemCatalog: List<String>): Boolean =
        interest.trim() !in systemCatalog.map { it.trim() }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optString(i)?.takeIf { it.isNotBlank() && it != "null" }?.let { add(it) }
            }
        }
    }

    // ==============================================================
    // v2 HARDENED DISCOVERY (RPC — server-side filtering & ranking)
    // ==============================================================

    /**
     * Server-side discovery query (PRD §6.1). Calls the
     * `get_discovery_profiles` Postgres function which enforces the age
     * window, distance (Haversine), gender, relationship intent,
     * verified-only, occupation keyword and shared-interest filters IN
     * the database — plus excludes already-swiped profiles and ranks by
     * a deterministic compatibility score.
     *
     * Falls back to an empty list (with a `null` result distinction) when
     * the RPC is not deployed yet, so the caller can keep its bundled
     * deck.
     *
     * @return null when the RPC is missing/unreachable, the ranked
     *         candidates otherwise.
     */
    suspend fun fetchDiscoveryCandidates(
        session: SupabaseAuth.AuthSession,
        minAge: Int,
        maxAge: Int,
        maxDistanceKm: Int,
        gender: String?,
        intent: String?,
        verifiedOnly: Boolean,
        occupation: String?,
        sharedInterests: List<String>,
        languages: List<String> = emptyList(),
        limit: Int = 20,
        offset: Int = 0
    ): List<UserProfile>? {
        if (!isConfigured()) return null
        return runCatching {
            val body = JSONObject()
                .put("p_user_id", session.userId)
                .put("p_min_age", minAge)
                .put("p_max_age", maxAge)
                .put("p_max_distance_km", maxDistanceKm)
                .put("p_gender", gender ?: JSONObject.NULL)
                .put("p_intent", intent ?: JSONObject.NULL)
                .put("p_verified_only", verifiedOnly)
                .put("p_occupation", occupation?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("p_shared_interests", JSONArray(sharedInterests))
                .put("p_languages", JSONArray(languages))
                .put("p_limit", limit)
                .put("p_offset", offset)

            val raw = SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/rpc/${SupabaseConfig.RPC_GET_DISCOVERY_PROFILES}",
                body = body.toString(),
                accessToken = session.accessToken
            )
            val rows = SupabaseClient.parseArray(raw)

            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    add(
                        UserProfile(
                            id = row.optString("profile_id"),
                            name = row.optString("name"),
                            age = row.optInt("age", 21),
                            bio = row.optString("bio"),
                            city = row.optString("city"),
                            distanceKm = row.optDouble("distance_km", 0.0).toInt(),
                            relationshipIntent = row.optString("relationship_intent")
                                .takeIf { it.isNotBlank() && it != "null" }
                                ?: "Long-term partner",
                            photoUris = row.optJSONArray("photo_urls").toStringList(),
                            interests = row.optJSONArray("interests").toStringList(),
                            languages = row.optJSONArray("languages").toStringList()
                                .ifEmpty { listOf("English") },
                            isVerified = row.optBoolean("is_verified", false),
                            compatibilityScore = row.optInt("compatibility_score", 0)
                                .coerceIn(1, 100)
                        )
                    )
                }
            }
        }.getOrNull()
    }

    // ==============================================================
    // CONVERSATIONS (v3.1 — chats list survives restarts + seeded chats)
    // ==============================================================

    /** One chat message row as stored in the `messages` table. */
    data class RemoteChatMessage(
        val id: String,
        val conversationId: String,
        val senderId: String,
        val text: String,
        val isRead: Boolean,
        val createdAtIso: String?
    )

    /**
     * One conversation of the signed-in user: the other side's profile, the
     * match row and its (server-side) message history.
     *
     * NOTE on sender attribution: outgoing messages written by this app
     * store the literal sender id "user_me" (the long-standing convention of
     * [insertChatMessage]); incoming/seeded rows carry the sender's profile
     * id. Both are mapped to isMine by the caller.
     */
    data class RemoteConversation(
        val conversationId: String,
        val profile: UserProfile,
        val matchedAtIso: String?,
        val isNewMatch: Boolean,
        val hasActiveGame: Boolean,
        val lastMessageText: String?,
        val unreadCount: Int,
        val messages: List<RemoteChatMessage>
    )

    /**
     * Loads every conversation of [userId]: match rows from `matches`
     * (either side), the other side's `profiles` row and the shared
     * `messages` history. Three plain PostgREST reads — no RPC, so this
     * works against the already-deployed schema (RLS: participants read
     * their own matches, authenticated read profiles/messages).
     *
     * @return null when not configured or the request failed (caller keeps
     *         its local state); an empty list when the user has no chats.
     */
    suspend fun fetchConversations(
        userId: String,
        accessToken: String?
    ): List<RemoteConversation>? {
        if (!isConfigured()) return null
        return runCatching {
            // 1) The user's match rows (most recent first).
            val rawMatches = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_MATCHES}",
                query = mapOf(
                    "select" to "id,user_a_id,user_b_id,matched_at,last_message,is_new,has_active_game",
                    "or" to "(user_a_id.eq.$userId,user_b_id.eq.$userId)",
                    "order" to "matched_at.desc",
                    "limit" to "30"
                ),
                accessToken = accessToken
            )
            val matchRows = SupabaseClient.parseArray(rawMatches)
            if (matchRows.length() == 0) return@runCatching emptyList()

            // 2) The other side's profile per match (one batched read).
            val otherIds = linkedSetOf<String>()
            val otherByMatch = mutableMapOf<String, String>() // match row id -> other profile id
            for (i in 0 until matchRows.length()) {
                val row = matchRows.optJSONObject(i) ?: continue
                val a = row.optString("user_a_id")
                val b = row.optString("user_b_id")
                val other = if (a == userId) b else a
                if (other.isBlank()) continue
                otherByMatch[row.optString("id")] = other
                otherIds.add(other)
            }
            val profileById = mutableMapOf<String, UserProfile>()
            if (otherIds.isNotEmpty()) {
                val idFilter = otherIds.joinToString(",") { "\"$it\"" }
                val rawProfiles = SupabaseClient.rest(
                    method = "GET",
                    path = "/rest/v1/${SupabaseConfig.TABLE_PROFILES}",
                    query = mapOf(
                        "select" to "*",
                        "id" to "in.($idFilter)"
                    ),
                    accessToken = accessToken
                )
                val profileRows = SupabaseClient.parseArray(rawProfiles)
                for (i in 0 until profileRows.length()) {
                    val row = profileRows.optJSONObject(i) ?: continue
                    val id = row.optString("id")
                    if (id.isBlank()) continue
                    profileById[id] = chatProfileFromRow(row)
                }
            }

            // 3) The message history for every conversation (one batched read).
            //    conversation_id == "match_<otherProfileId>" (client convention).
            val conversationIdByMatch = otherByMatch.mapValues { (_, other) -> "match_$other" }
            val convFilter = conversationIdByMatch.values
                .distinct()
                .joinToString(",") { "\"$it\"" }
            val messagesByConversation = mutableMapOf<String, List<RemoteChatMessage>>()
            runCatching {
                val rawMessages = SupabaseClient.rest(
                    method = "GET",
                    path = "/rest/v1/${SupabaseConfig.TABLE_MESSAGES}",
                    query = mapOf(
                        "select" to "id,conversation_id,sender_id,text,is_read,created_at",
                        "conversation_id" to "in.($convFilter)",
                        "order" to "created_at.asc",
                        "limit" to "500"
                    ),
                    accessToken = accessToken
                )
                val messageRows = SupabaseClient.parseArray(rawMessages)
                for (i in 0 until messageRows.length()) {
                    val row = messageRows.optJSONObject(i) ?: continue
                    val conv = row.optString("conversation_id")
                    if (conv.isBlank()) continue
                    messagesByConversation[conv] =
                        (messagesByConversation[conv] ?: emptyList()) + RemoteChatMessage(
                            id = row.optString("id"),
                            conversationId = conv,
                            senderId = row.optString("sender_id"),
                            text = row.optString("text"),
                            isRead = row.optBoolean("is_read", false),
                            createdAtIso = row.optString("created_at").takeIf { it.isNotBlank() }
                        )
                }
            } // unread-history failures are non-fatal

            buildList {
                for (i in 0 until matchRows.length()) {
                    val row = matchRows.optJSONObject(i) ?: continue
                    val matchRowId = row.optString("id")
                    val other = otherByMatch[matchRowId] ?: continue
                    val profile = profileById[other] ?: continue
                    val convId = conversationIdByMatch[matchRowId] ?: "match_$other"
                    val messages = messagesByConversation[convId].orEmpty()
                    val unread = messages.count {
                        it.senderId != "user_me" && it.senderId != userId && !it.isRead
                    }
                    add(
                        RemoteConversation(
                            conversationId = convId,
                            profile = profile,
                            matchedAtIso = row.optString("matched_at").takeIf { it.isNotBlank() },
                            isNewMatch = row.optBoolean("is_new", false),
                            hasActiveGame = row.optBoolean("has_active_game", false),
                            lastMessageText = messages.lastOrNull()?.text
                                ?: row.optString("last_message").takeIf { it.isNotBlank() },
                            unreadCount = unread,
                            messages = messages
                        )
                    )
                }
            }
        }.getOrNull()
    }

    /** Compact profiles-row → [UserProfile] mapping for chat list rows. */
    private fun chatProfileFromRow(row: JSONObject): UserProfile = UserProfile(
        id = row.optString("id"),
        name = row.optString("name").ifBlank { "Quicky user" },
        age = row.optInt("age", 21),
        bio = row.optString("bio"),
        city = row.optString("city"),
        distanceKm = row.optInt("distance_km", 0),
        relationshipIntent = row.optString("relationship_intent").ifBlank { "Long-term partner" },
        occupation = row.optString("occupation"),
        gender = row.optString("gender").ifBlank { "Female" },
        languages = row.optJSONArray("languages").toStringList().ifEmpty { listOf("English") },
        isVerified = row.optBoolean("is_verified", false),
        isOnline = row.optBoolean("is_online", false),
        characterBadge = row.optString("character_badge", "The Explorer"),
        characterDescription = row.optString("character_description"),
        photoUris = row.optJSONArray("photo_urls").toStringList(),
        interests = row.optJSONArray("interests").toStringList(),
        hobbies = row.optJSONArray("hobbies").toStringList(),
        compatibilityScore = row.optInt("compatibility_score", 85)
    )

    /**
     * Records a LIKE / PASS / SUPER_LIKE server-side via the
     * `record_swipe` RPC (PRD §6.1/§6.2): rate-limited (200/hour),
     * deduplicated and — when the like is mutual — creates the match row
     * atomically.
     *
     * @return the RPC result token ("OK", "MATCH", "RATE_LIMITED_*",
     *         "INVALID_*") or null when the call failed / not configured.
     */
    suspend fun recordSwipe(
        session: SupabaseAuth.AuthSession,
        targetUserId: String,
        action: String
    ): String? {
        if (!isConfigured()) return null
        return runCatching {
            val body = JSONObject()
                .put("p_user_id", session.userId)
                .put("p_target_user_id", targetUserId)
                .put("p_action", action)

            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/rpc/${SupabaseConfig.RPC_RECORD_SWIPE}",
                body = body.toString(),
                accessToken = session.accessToken
            ).trim().trim('"')
        }.getOrNull()
    }

    // ==============================================================
    // PROFILE BOOST (PRD v2.3 §9–§15) — account-scoped via Supabase
    // ==============================================================

    /** The signed-in user's active boost window (epoch millis), if any. */
    data class ActiveBoost(
        val startedAtMs: Long,
        val expiresAtMs: Long
    ) {
        val isActive: Boolean get() = expiresAtMs > System.currentTimeMillis()
    }

    /**
     * Loads the AUTHENTICATED user's active boost row (RLS: own rows only).
     * Returns null when none is active or it has already expired — the
     * boost state belongs to the Supabase Auth user id, never to the
     * device, so a newly signed-in account can never inherit the previous
     * account's boost (PRD Test A).
     */
    suspend fun fetchActiveBoost(session: SupabaseAuth.AuthSession): ActiveBoost? {
        if (!isConfigured()) return null
        return runCatching {
            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_BOOSTS}",
                query = mapOf(
                    "select" to "started_at,expires_at",
                    "user_id" to "eq.${session.userId}",
                    "is_active" to "eq.true",
                    "order" to "started_at.desc",
                    "limit" to "1"
                ),
                accessToken = session.accessToken
            )
            val row = SupabaseClient.parseArray(raw).optJSONObject(0)
                ?: return@runCatching null
            val started = LudoTime.parseIsoToEpochMs(row.optString("started_at"))
            val expires = LudoTime.parseIsoToEpochMs(row.optString("expires_at"))
            if (started == null || expires == null) return@runCatching null
            ActiveBoost(started, expires)
        }.getOrNull()
    }

    /**
     * Activates a fresh 30-minute boost for the AUTHENTICATED user through
     * the SECURITY DEFINER RPC (schema.sql v2.8): the previous row is
     * retired first, and only the caller's own row is ever touched.
     * Returns the new window, or null when the RPC is unavailable
     * (offline / not yet deployed) — callers then keep local-only state.
     */
    suspend fun activateBoostRpc(
        session: SupabaseAuth.AuthSession,
        minutes: Int = 30
    ): ActiveBoost? {
        if (!isConfigured()) return null
        return runCatching {
            val raw = SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/rpc/${SupabaseConfig.RPC_ACTIVATE_BOOST}",
                body = JSONObject().put("p_minutes", minutes).toString(),
                accessToken = session.accessToken
            )
            // PostgREST returns the function's jsonb result directly
            // (or an error object — guard both shapes).
            val obj = JSONObject(raw)
            val started = LudoTime.parseIsoToEpochMs(obj.optString("started_at"))
            val expires = LudoTime.parseIsoToEpochMs(obj.optString("expires_at"))
            if (started == null || expires == null) null else ActiveBoost(started, expires)
        }.getOrNull()
    }

    // ==============================================================
    // WRITES
    // ==============================================================

    /**
     * Marks the UNREAD messages of one conversation as read server-side
     * (v3.1 — unread badges persist across restarts now that the chat
     * history is loaded on sign-in). Fire-and-forget; returns false
     * instead of throwing.
     *
     * @param accessToken the signed-in user's JWT — the update policy
     *        requires the authenticated role (the anon key is rejected).
     */
    suspend fun markConversationRead(
        conversationId: String,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "PATCH",
                path = "/rest/v1/${SupabaseConfig.TABLE_MESSAGES}",
                query = mapOf(
                    "conversation_id" to "eq.$conversationId",
                    "is_read" to "eq.false"
                ),
                body = JSONObject().put("is_read", true).toString(),
                accessToken = accessToken
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Persists a personal-chat message to the `messages` table.
     * Fire-and-forget: returns false instead of throwing.
     */
    suspend fun insertChatMessage(
        conversationId: String,
        senderId: String,
        text: String
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_MESSAGES}",
                // Prefer=return=minimal keeps the payload tiny
                query = mapOf("select" to "id", "Prefer" to "return=minimal"),
                body = JSONObject()
                    .put("conversation_id", conversationId)
                    .put("sender_id", senderId)
                    .put("text", text)
                    .toString()
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Persists a club-chat message to the `club_messages` table.
     * Fire-and-forget: returns false instead of throwing.
     */
    suspend fun insertClubMessage(
        clubId: String,
        senderId: String,
        senderName: String,
        messageType: String,
        text: String
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_MESSAGES}",
                query = mapOf("Prefer" to "return=minimal"),
                body = JSONObject()
                    .put("club_id", clubId)
                    .put("sender_id", senderId)
                    .put("sender_name", senderName)
                    .put("message_type", messageType)
                    .put("text", text)
                    .toString()
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Deletes a club (v2.1 §3.8 — OWNER ONLY).
     * Security is enforced server-side: the `clubs_delete` RLS policy
     * allows the delete only when `owner_id = auth.uid()`, FK cascades wipe
     * club_members / club_messages / club_events, and the
     * `notify_club_deletion` trigger pushes a notification to every member.
     */
    suspend fun deleteClub(clubId: String, accessToken: String?): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "DELETE",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUBS}",
                query = mapOf("id" to "eq.$clubId"),
                accessToken = accessToken,
                prefer = "return=minimal"
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Persists the completed onboarding draft into the `profiles` table
     * (upsert — safe even when the signup trigger already created the
     * row) and syncs the normalized `user_interests` rows behind it.
     *
     * @param systemInterests the currently served interest catalog, used
     *        to label each interest SYSTEM vs CUSTOM
     * @return true when both writes succeeded
     */
    suspend fun saveOnboardingProfile(
        session: SupabaseAuth.AuthSession,
        draft: OnboardingDraft,
        photoUrls: List<String>,
        systemInterests: List<String> = emptyList()
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            val body = JSONObject()
                .put("id", session.userId)
                .put("name", draft.fullName.trim())
                .put("age", draft.calculatedAge ?: 21)
                .put("bio", draft.bio.trim())
                .put("city", draft.city.trim())
                .put("gender", draft.gender)
                .put("custom_gender", draft.customGender.trim())
                .put("interested_in", draft.interestedIn)
                .put("relationship_intent", draft.lookingFor.firstOrNull() ?: "")
                .put("education_level", draft.qualification)
                .put("qualification", draft.qualification)
                .put("occupation", draft.occupation.trim())
                .put("interests", JSONArray(draft.interests))
                .put("hobbies", JSONArray(draft.hobbies))
                .put("languages", JSONArray(draft.languages.ifEmpty { listOf("English") }))
                .put("looking_for", JSONArray(draft.lookingFor))
                .put("height", if (draft.heightCm != null) "${draft.heightCm} cm" else "")
                .put("photo_urls", JSONArray(photoUrls))
                .put("onboarding_completed", true)
                .put("onboarding_step", 4)

            if (draft.dateOfBirthEpochDay != null) {
                body.put("date_of_birth", java.time.LocalDate.ofEpochDay(draft.dateOfBirthEpochDay).toString())
            } else {
                body.put("date_of_birth", JSONObject.NULL)
            }
            if (draft.heightCm != null) body.put("height_cm", draft.heightCm) else body.put("height_cm", JSONObject.NULL)
            if (draft.weightKg != null) body.put("weight_kg", draft.weightKg.toDouble()) else body.put("weight_kg", JSONObject.NULL)
            if (draft.latitude != null && draft.longitude != null) {
                body.put("latitude", draft.latitude).put("longitude", draft.longitude)
            }

            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_PROFILES}",
                prefer = "resolution=merge-duplicates,return=minimal",
                body = body.toString(),
                accessToken = session.accessToken
            )

            // Sync the normalized per-interest rows that power matching.
            syncUserInterestRows(
                userId = session.userId,
                interests = draft.interests,
                systemInterests = systemInterests,
                accessToken = session.accessToken
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Persists the signed-in user's interest selection everywhere it lives:
     * the denormalized `profiles.interests` array plus the normalized
     * `user_interests` rows that power shared-interest matching and filters.
     *
     * Called when interests are edited post-onboarding (Discovery
     * Preferences sheet, Personal Information sheet).
     */
    suspend fun saveUserInterests(
        session: SupabaseAuth.AuthSession,
        interests: List<String>,
        systemInterests: List<String> = emptyList()
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "PATCH",
                path = "/rest/v1/${SupabaseConfig.TABLE_PROFILES}",
                query = mapOf("id" to "eq.${session.userId}"),
                prefer = "return=minimal",
                body = JSONObject().put("interests", JSONArray(interests)).toString(),
                accessToken = session.accessToken
            )
            syncUserInterestRows(
                userId = session.userId,
                interests = interests,
                systemInterests = systemInterests,
                accessToken = session.accessToken
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Replaces the user's normalized `user_interests` rows with the given
     * selection (delete-then-insert, both scoped to the owning user).
     */
    private suspend fun syncUserInterestRows(
        userId: String,
        interests: List<String>,
        systemInterests: List<String>,
        accessToken: String?
    ) {
        SupabaseClient.rest(
            method = "DELETE",
            path = "/rest/v1/${SupabaseConfig.TABLE_USER_INTERESTS}",
            query = mapOf("user_id" to "eq.$userId"),
            accessToken = accessToken
        )
        if (interests.isNotEmpty()) {
            val rows = JSONArray()
            interests.forEach { interest ->
                rows.put(
                    JSONObject()
                        .put("user_id", userId)
                        .put("interest", interest.trim())
                        .put(
                            "source",
                            if (isCustomInterest(interest, systemInterests)) "CUSTOM" else "SYSTEM"
                        )
                )
            }
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_USER_INTERESTS}",
                prefer = "return=minimal",
                body = rows.toString(),
                accessToken = accessToken
            )
        }
    }

    /**
     * Patches a subset of the signed-in user's `profiles` columns
     * (e.g. photo_urls after add/remove/reorder, occupation after an edit).
     * Fire-and-forget: returns false instead of throwing.
     */
    suspend fun updateProfileFields(
        userId: String,
        fields: JSONObject,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "PATCH",
                path = "/rest/v1/${SupabaseConfig.TABLE_PROFILES}",
                query = mapOf("id" to "eq.$userId"),
                prefer = "return=minimal",
                body = fields.toString(),
                accessToken = accessToken
            )
            true
        }.getOrDefault(false)
    }

    /** Generic single-row insert for any configured table. */
    suspend fun insertRow(table: String, row: JSONObject): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/$table",
                query = mapOf("Prefer" to "return=minimal"),
                body = row.toString()
            )
            true
        }.getOrDefault(false)
    }

    // ==============================================================
    // STORAGE
    // ==============================================================

    /**
     * Uploads a profile photo and returns its public CDN URL.
     *
     * @param userId owner id, used to namespace the object path
     * @param bytes  image bytes
     * @param ext    file extension, e.g. "jpg"
     * @param accessToken Supabase Auth user JWT for authenticated uploads
     */
    suspend fun uploadProfilePhoto(
        userId: String,
        bytes: ByteArray,
        ext: String = "jpg",
        contentType: String = "image/jpeg",
        accessToken: String? = null
    ): String? {
        if (!isConfigured()) return null
        val objectPath = "$userId/avatar_${System.currentTimeMillis()}.$ext"
        val key = SupabaseClient.storageUpload(
            bucket = SupabaseConfig.BUCKET_PROFILE_PHOTOS,
            objectPath = objectPath,
            bytes = bytes,
            contentType = contentType,
            accessToken = accessToken
        ) ?: return null
        return SupabaseClient.storagePublicUrl(SupabaseConfig.BUCKET_PROFILE_PHOTOS, objectPath)
            .ifEmpty { key }
    }

    // ==============================================================
    // GEOCODING (Edit Location sheet — change request #5)
    // ==============================================================

    /**
     * Forward-geocodes a city / area name via the free Nominatim
     * (OpenStreetMap) search API. Nominatim requires a descriptive
     * User-Agent, so a dedicated plain OkHttp client is used (no Supabase
     * headers). Results are capped at 5 and each suggestion carries a
     * shortened "City, Region" label for the UI.
     *
     * Location PRD §5: the label is built from the STRUCTURED address
     * object (addressdetails=1) — locality/town/village + state — so a
     * postal code can never leak into the stored/displayed location. The
     * display_name fallback explicitly skips postcode-shaped parts.
     */
    suspend fun searchCity(query: String): List<GeoSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://nominatim.openstreetmap.org/search" +
                        "?q=${URLEncoder.encode(trimmed, "UTF-8")}" +
                        "&format=json&limit=5&addressdetails=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Quicky-Android/1.0 (dating app location search)")
                    .header("Accept", "application/json")
                    .build()
                val body = geoHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else return@runCatching emptyList()
                } ?: return@runCatching emptyList()
                val rows = JSONArray(body)
                buildList {
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        val lat = row.optDouble("lat", Double.NaN)
                        val lon = row.optDouble("lon", Double.NaN)
                        if (lat.isNaN() || lon.isNaN()) continue
                        val display = row.optString("display_name")
                        if (display.isBlank()) continue
                        val shortLabel = shortLocationLabel(row.optJSONObject("address"), display)
                        add(
                            GeoSuggestion(
                                latitude = lat,
                                longitude = lon,
                                label = display,
                                city = shortLabel
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    /**
     * Builds the short "Locality, State" label from the structured Nominatim
     * address when available (village/town/city + state — never postcode).
     * Falls back to parsing display_name and skipping postcode-shaped parts.
     */
    private fun shortLocationLabel(address: JSONObject?, display: String): String {
        // Structured path — preferred (PRD §5.2 "use the existing structured
        // location fields wherever possible").
        val locality = address?.let {
            listOfNotNull(
                it.optString("city").takeIf { s -> s.isNotBlank() },
                it.optString("town").takeIf { s -> s.isNotBlank() },
                it.optString("village").takeIf { s -> s.isNotBlank() },
                it.optString("municipality").takeIf { s -> s.isNotBlank() },
                it.optString("county").takeIf { s -> s.isNotBlank() }
            ).firstOrNull()
        }
        val state = address?.optString("state")?.takeIf { it.isNotBlank() }
        if (locality != null) {
            return if (state != null && state != locality) "$locality, $state" else locality
        }
        // Fallback: "Kochi, Ernakulam District, Kerala, India" ->
        // "Kochi, Kerala" (first + a nearby NON-postal part).
        val parts = display.split(", ").map { it.trim() }.filter { it.isNotBlank() }
        val nonPostal = parts.filterNot { part ->
            val compact = part.replace(" ", "").replace("-", "")
            part.length in 3..8 && compact.isNotEmpty() &&
                    part.all { it.isDigit() || it == ' ' || it == '-' } &&
                    compact.all { it.isDigit() }
        }
        return when {
            nonPostal.size >= 3 -> "${nonPostal.first()}, ${nonPostal[nonPostal.size - 2]}"
            nonPostal.isNotEmpty() -> nonPostal.first()
            else -> display
        }
    }

    /** Plain HTTP client for external (non-Supabase) geocoding calls. */
    private val geoHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
