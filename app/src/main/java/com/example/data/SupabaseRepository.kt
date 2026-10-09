package com.example.data

import com.example.model.Club
import com.example.model.ClubMember
import com.example.model.ClubMessage
import com.example.model.ClubMessageReaction
import com.example.model.GameDefinition
import com.example.model.GeoSuggestion
import com.example.model.NotificationItem
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
                    profileCompletionScore = row.optInt("profile_completion_score", 85),
                    allowClubDm = row.optBoolean("allow_club_dm", true)
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
        val createdAtIso: String?,
        /** v3.3.7: "TEXT" (default) or "SNAP" (view-once photo). */
        val messageType: String = "TEXT",
        /** v3.3.7: storage object path while the snap is unviewed; null once viewed. */
        val snapPath: String? = null,
        /** v3.3.7: true once the receiver opened the snap. */
        val snapViewed: Boolean = false
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

            // 1b) v3.3.7 — 1:1 conversations started from a club member list
            //     (`club_dm_conversations`). Ids are deterministic:
            //     "dm_<smallerUserId>_<largerUserId>", so both sides agree
            //     without a server round-trip. Failures are non-fatal.
            val dmOtherByConversation = linkedMapOf<String, String>() // dm id -> other profile id
            val dmCreatedAtByConversation = mutableMapOf<String, String>()
            runCatching {
                val rawDms = SupabaseClient.rest(
                    method = "GET",
                    path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_DM_CONVERSATIONS}",
                    query = mapOf(
                        "select" to "id,user_a_id,user_b_id,created_at",
                        "or" to "(user_a_id.eq.$userId,user_b_id.eq.$userId)",
                        "order" to "created_at.desc",
                        "limit" to "50"
                    ),
                    accessToken = accessToken
                )
                val dmRows = SupabaseClient.parseArray(rawDms)
                for (i in 0 until dmRows.length()) {
                    val row = dmRows.optJSONObject(i) ?: continue
                    val convId = row.optString("id")
                    val a = row.optString("user_a_id")
                    val b = row.optString("user_b_id")
                    val other = if (a == userId) b else a
                    if (convId.isBlank() || other.isBlank()) continue
                    dmOtherByConversation[convId] = other
                    row.optString("created_at").takeIf { it.isNotBlank() }
                        ?.let { dmCreatedAtByConversation[convId] = it }
                }
            } // club-DM failures are non-fatal — matches still load

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
            // Club-DM counterparts ride the SAME batched profile read — their
            // ids are simply added to the match-other set before it fires.
            otherIds.addAll(dmOtherByConversation.values.filter { it !in otherByMatch.values })
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
            //    conversation_id == "match_<otherProfileId>" (client convention)
            //    or "dm_<a>_<b>" for club-started 1:1 chats (v3.3.7).
            val conversationIdByMatch = otherByMatch.mapValues { (_, other) -> "match_$other" }
            val convFilter = (conversationIdByMatch.values + dmOtherByConversation.keys)
                .distinct()
                .joinToString(",") { "\"$it\"" }
            val messagesByConversation = mutableMapOf<String, List<RemoteChatMessage>>()
            runCatching {
                val rawMessages = SupabaseClient.rest(
                    method = "GET",
                    path = "/rest/v1/${SupabaseConfig.TABLE_MESSAGES}",
                    query = mapOf(
                        "select" to "id,conversation_id,sender_id,text,is_read,created_at," +
                                "message_type,snap_path,snap_viewed",
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
                            createdAtIso = row.optString("created_at").takeIf { it.isNotBlank() },
                            messageType = row.optStringOrNull("message_type") ?: "TEXT",
                            snapPath = row.optStringOrNull("snap_path"),
                            snapViewed = row.optBoolean("snap_viewed", false)
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
                // v3.3.7 — club-started 1:1 conversations ride the same list
                // (Chats tab + history), keyed by their deterministic dm_ id.
                for ((convId, otherId) in dmOtherByConversation) {
                    val profile = profileById[otherId] ?: continue
                    val messages = messagesByConversation[convId].orEmpty()
                    val unread = messages.count {
                        it.senderId != "user_me" && it.senderId != userId && !it.isRead
                    }
                    add(
                        RemoteConversation(
                            conversationId = convId,
                            profile = profile,
                            matchedAtIso = dmCreatedAtByConversation[convId],
                            isNewMatch = false,
                            hasActiveGame = false,
                            lastMessageText = messages.lastOrNull()?.text,
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
        compatibilityScore = row.optInt("compatibility_score", 85),
        allowClubDm = row.optBoolean("allow_club_dm", true)
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
     *
     * v3.3.7: [messageType] "SNAP" + [snapPath] store a view-once photo snap
     * (the image itself lives in the private `snap-images` bucket; the row
     * keeps only its object path until the receiver views it).
     */
    suspend fun insertChatMessage(
        conversationId: String,
        senderId: String,
        text: String,
        accessToken: String? = null,
        messageType: String = "TEXT",
        snapPath: String? = null
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            val body = JSONObject()
                .put("conversation_id", conversationId)
                .put("sender_id", senderId)
                .put("text", text)
                .put("message_type", messageType)
            snapPath?.let { body.put("snap_path", it) }
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_MESSAGES}",
                // v3.3.2 fix: Prefer is an HTTP HEADER in PostgREST. Sent as
                // a URL query param it 400s (PGRST100 "failed to parse filter
                // (return=minimal)") — runCatching swallowed that, so no chat
                // message ever reached the server. The JWT is also required
                // by the messages_insert RLS policy (authenticated only).
                prefer = "return=minimal",
                body = body.toString(),
                accessToken = accessToken
            )
            true
        }.getOrDefault(false)
    }

    // ==============================================================
    // v3.3.7 — SNAP PHOTOS (view-once, Snapchat-style)
    // ==============================================================

    /**
     * Uploads a captured snap photo to the PRIVATE `snap-images` bucket.
     *
     * The object path embeds a random UUID so it can never be guessed; the
     * image is destroyed by [markSnapViewed] the moment the receiver opens
     * it — the row keeps only a "viewed" flag afterwards.
     *
     * @return the storage object path (the row's `snap_path`), or null on failure.
     */
    suspend fun uploadSnapImage(
        senderId: String,
        bytes: ByteArray,
        accessToken: String?
    ): String? {
        if (!isConfigured()) return null
        val objectPath = "snaps/$senderId/${java.util.UUID.randomUUID()}.jpg"
        val key = SupabaseClient.storageUpload(
            bucket = SupabaseConfig.BUCKET_SNAP_IMAGES,
            objectPath = objectPath,
            bytes = bytes,
            contentType = "image/jpeg",
            accessToken = accessToken
        )
        if (key == null) {
            android.util.Log.w("QuickySnaps", "Snap upload failed (sender=$senderId)")
        }
        return key
    }

    /**
     * Downloads an unviewed snap for the fullscreen viewer (private bucket,
     * rides the user JWT). Returns null on any failure — the chip then toasts
     * "snap unavailable".
     */
    suspend fun downloadSnapImage(
        objectPath: String,
        accessToken: String?
    ): ByteArray? {
        if (!isConfigured()) return null
        return SupabaseClient.storageDownload(
            bucket = SupabaseConfig.BUCKET_SNAP_IMAGES,
            objectPath = objectPath,
            accessToken = accessToken
        )
    }

    /**
     * Atomic "snap viewed" (v3.3.7): the security-definer RPC
     * `mark_snap_viewed(p_message_id)` marks the row snap_viewed=true,
     * nulls its snap_path AND deletes the stored image object — the photo
     * is permanently gone from the database at the instant it is viewed.
     *
     * The direct storage delete is the belt-and-braces fallback when the
     * RPC is unavailable (pre-migration database).
     */
    suspend fun markSnapViewed(
        messageId: String,
        objectPath: String?,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/rpc/${SupabaseConfig.RPC_MARK_SNAP_VIEWED}",
                body = JSONObject().put("p_message_id", messageId).toString(),
                accessToken = accessToken
            )
            true
        }.onFailure { e ->
            android.util.Log.w("QuickySnaps", "mark_snap_viewed RPC failed: ${e.message}")
        }.getOrElse {
            // Fallback: PATCH the row + delete the object directly.
            val patched = runCatching {
                SupabaseClient.rest(
                    method = "PATCH",
                    path = "/rest/v1/${SupabaseConfig.TABLE_MESSAGES}",
                    query = mapOf("id" to "eq.$messageId"),
                    prefer = "return=minimal",
                    body = JSONObject()
                        .put("snap_viewed", true)
                        .put("snap_path", JSONObject.NULL)
                        .toString(),
                    accessToken = accessToken
                )
                true
            }.getOrDefault(false)
            val deleted = objectPath != null && SupabaseClient.storageDelete(
                bucket = SupabaseConfig.BUCKET_SNAP_IMAGES,
                objectPath = objectPath,
                accessToken = accessToken
            )
            patched && (objectPath == null || deleted)
        }
    }

    // ==============================================================
    // v3.3.7 — CLUB PERSONAL CHATS (1:1 chats from a club member list)
    // ==============================================================

    /**
     * Registers a club-started 1:1 conversation (`club_dm_conversations`).
     *
     * Idempotent: the pair (user_a_id, user_b_id) is unique and duplicates
     * are ignored via `Prefer: resolution=ignore-duplicates`, so re-opening
     * a chat never errors. [conversationId] is the deterministic
     * "dm_<smallerUserId>_<largerUserId>" both devices derive on their own.
     */
    suspend fun insertClubDmConversation(
        conversationId: String,
        clubId: String,
        userAId: String,
        userBId: String,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_DM_CONVERSATIONS}",
                prefer = "resolution=ignore-duplicates",
                body = JSONObject()
                    .put("id", conversationId)
                    .put("club_id", clubId)
                    .put("user_a_id", userAId)
                    .put("user_b_id", userBId)
                    .toString(),
                accessToken = accessToken
            )
            true
        }.onFailure { e ->
            android.util.Log.w("QuickyClubs", "insertClubDmConversation failed: ${e.message}")
        }.getOrDefault(false)
    }

    /** Server echo of a persisted club message (for state reconciliation). */
    data class ClubMessageInsertResult(
        val id: String,
        val createdAtIso: String
    )

    /**
     * Persists a club-chat message to the `club_messages` table (v3.3.4).
     *
     * Sends the FULL payload — reply quote, sticker, voice note URL/duration
     * and the "@mentioned" user ids (a database trigger turns each mention
     * into a tailored notification row for that user).
     *
     * Uses `Prefer: return=representation` so the server echoes the created
     * row (id + created_at): the ViewModel then patches its optimistic local
     * message, which keeps pagination + polling cursors and re-loads from
     * ever duplicating it.
     *
     * @return the created row's id/created_at, or null on failure (network,
     *         RLS, or the not-a-member / suspended trigger guard).
     */
    suspend fun insertClubMessage(
        clubId: String,
        senderId: String,
        senderName: String,
        messageType: String,
        text: String,
        stickerEmoji: String? = null,
        voiceDurationSeconds: Int? = null,
        voiceUrl: String? = null,
        replyToText: String? = null,
        replyToSender: String? = null,
        mentions: List<String> = emptyList(),
        accessToken: String? = null
    ): ClubMessageInsertResult? {
        if (!isConfigured()) return null
        return runCatching {
            val body = JSONObject()
                .put("club_id", clubId)
                .put("sender_id", senderId)
                .put("sender_name", senderName)
                .put("message_type", messageType)
                .put("text", text)
                .put("mentions", JSONArray(mentions))
            stickerEmoji?.let { body.put("sticker_emoji", it) }
            voiceDurationSeconds?.let { body.put("voice_duration_seconds", it) }
            voiceUrl?.let { body.put("voice_url", it) }
            replyToText?.let { body.put("reply_to_text", it.take(200)) }
            replyToSender?.let { body.put("reply_to_sender", it.take(80)) }

            val raw = SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_MESSAGES}",
                query = mapOf("select" to "id,created_at"),
                body = body.toString(),
                accessToken = accessToken,
                prefer = "return=representation"
            )
            val row = SupabaseClient.parseArray(raw).optJSONObject(0)
                ?: return@runCatching null
            ClubMessageInsertResult(
                id = row.optString("id"),
                createdAtIso = normalizeIsoCursor(row.optString("created_at"))
            )
        }.onFailure { e ->
            android.util.Log.w(
                "QuickyClubs",
                "insertClubMessage failed (club=$clubId type=$messageType): ${e.message}"
            )
        }.getOrNull()
    }

    /**
     * Loads ONE PAGE of club-chat history (v3.3.4 chunked loading).
     *
     * PostgREST cursor pagination: newest-first `created_at desc` window of
     * [limit] rows, optionally older than [beforeIso] (scroll-up page) or
     * newer than [afterIso] (live poll refresh). The page comes back
     * newest-first; this function returns it oldest-first so callers can
     * append/prepend directly, and enriches every message with its
     * reactions (one batched read).
     *
     * @return null when not configured or the request failed.
     */
    suspend fun fetchClubMessagesPage(
        clubId: String,
        currentUserId: String,
        limit: Int = 30,
        beforeIso: String? = null,
        afterIso: String? = null,
        accessToken: String? = null
    ): List<ClubMessage>? {
        if (!isConfigured()) return null
        return runCatching {
            val query = mutableMapOf(
                "select" to "id,club_id,sender_id,sender_name,message_type,text," +
                        "sticker_emoji,voice_duration_seconds,voice_url," +
                        "reply_to_text,reply_to_sender,mentions,created_at",
                "club_id" to "eq.$clubId",
                "order" to "created_at.desc",
                "limit" to limit.toString()
            )
            beforeIso?.takeIf { it.isNotBlank() }?.let { query["created_at"] = "lt.$it" }
            afterIso?.takeIf { it.isNotBlank() }?.let { query["created_at"] = "gt.$it" }

            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_MESSAGES}",
                query = query,
                accessToken = accessToken
            )
            val rows = SupabaseClient.parseArray(raw)
            val messages = ArrayList<ClubMessage>(rows.length())
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                messages.add(row.toClubMessage(currentUserId))
            }
            // Attach reactions for this page (single batched read).
            val reactions = fetchClubReactions(
                messages.map { it.id }, accessToken
            )
            messages.map { msg ->
                msg.copy(reactions = reactions[msg.id] ?: emptyList())
            }.reversed()
        }.getOrNull()
    }

    /**
     * Null-safe JSON string read (v3.3.5 fix).
     *
     * `JSONObject.optString(key)` returns the LITERAL string "null" when the
     * value is JSON null — which is how every non-reply club message ended up
     * rendering a "null" reply-quote block and a giant "null" sticker. This
     * helper maps JSON null / missing / blank / literal-"null" to Kotlin null.
     */
    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        val v = optString(key)
        return if (v.isBlank() || v == "null") null else v
    }

    /** Maps one `club_messages` row to the app model. */
    private fun JSONObject.toClubMessage(currentUserId: String): ClubMessage {
        val iso = normalizeIsoCursor(optString("created_at"))
        val mentionsJson = optJSONArray("mentions")
        val mentions = mutableListOf<String>()
        if (mentionsJson != null) {
            for (i in 0 until mentionsJson.length()) {
                mentionsJson.optString(i).takeIf { it.isNotBlank() }?.let { mentions.add(it) }
            }
        }
        return ClubMessage(
            id = optString("id"),
            clubId = optString("club_id"),
            senderId = optString("sender_id"),
            senderName = optStringOrNull("sender_name") ?: "Member",
            messageType = optString("message_type").ifBlank { "TEXT" },
            text = optStringOrNull("text") ?: "",
            stickerEmoji = optStringOrNull("sticker_emoji"),
            voiceDurationSeconds = if (isNull("voice_duration_seconds")) null
            else optInt("voice_duration_seconds"),
            voiceUrl = optStringOrNull("voice_url"),
            timestamp = LudoTime.isoToClock(iso) ?: "Now",
            isMine = optString("sender_id") == currentUserId,
            replyToText = optStringOrNull("reply_to_text"),
            replyToSender = optStringOrNull("reply_to_sender"),
            createdAtIso = iso,
            mentions = mentions
        )
    }

    /** "…+00:00" → "…Z" so the value is URL-safe as a PostgREST cursor. */
    private fun normalizeIsoCursor(iso: String): String =
        iso.trim().replace("+00:00", "Z").replace("+0000", "Z")

    /**
     * Batched read of the reactions for a set of club messages (v3.3.4).
     *
     * @return message id → its reactions; empty map when there are none or
     *         the read fails (reactions are cosmetic — never fatal).
     */
    suspend fun fetchClubReactions(
        messageIds: List<String>,
        accessToken: String?
    ): Map<String, List<ClubMessageReaction>> {
        if (!isConfigured() || messageIds.isEmpty()) return emptyMap()
        return runCatching {
            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/club_message_reactions",
                query = mapOf(
                    "select" to "id,message_id,user_id,user_name,emoji",
                    "message_id" to "in.(${messageIds.joinToString(",")})",
                    "order" to "created_at.asc",
                    "limit" to "500"
                ),
                accessToken = accessToken
            )
            val rows = SupabaseClient.parseArray(raw)
            val map = mutableMapOf<String, MutableList<ClubMessageReaction>>()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val messageId = row.optString("message_id")
                if (messageId.isBlank()) continue
                map.getOrPut(messageId) { mutableListOf() }.add(
                    ClubMessageReaction(
                        id = row.optString("id"),
                        messageId = messageId,
                        userId = row.optString("user_id"),
                        userName = row.optStringOrNull("user_name") ?: "Member",
                        emoji = row.optStringOrNull("emoji") ?: "❤️"
                    )
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    /** Adds a reaction row (idempotent per (message, user, emoji) unique). */
    suspend fun addClubReaction(
        clubId: String,
        messageId: String,
        userId: String,
        userName: String,
        emoji: String,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/club_message_reactions",
                prefer = "return=minimal,resolution=ignore-duplicates",
                body = JSONObject()
                    .put("message_id", messageId)
                    .put("club_id", clubId)
                    .put("user_id", userId)
                    .put("user_name", userName)
                    .put("emoji", emoji)
                    .toString(),
                accessToken = accessToken
            )
            true
        }.getOrDefault(false)
    }

    /** Removes one user's own reaction row. */
    suspend fun removeClubReaction(
        messageId: String,
        userId: String,
        emoji: String,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "DELETE",
                path = "/rest/v1/club_message_reactions",
                query = mapOf(
                    "message_id" to "eq.$messageId",
                    "user_id" to "eq.$userId",
                    "emoji" to "eq.$emoji"
                ),
                accessToken = accessToken,
                prefer = "return=minimal"
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Owner action (v3.3.4): flips a member's status between ACTIVE and
     * SUSPENDED. Suspended members are blocked from posting by the
     * `club_messages_membership_gate` trigger server-side; the RLS update
     * policy only lets the CLUB OWNER change member rows.
     */
    suspend fun updateClubMemberStatus(
        clubId: String,
        userId: String,
        status: String,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "PATCH",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_MEMBERS}",
                query = mapOf(
                    "club_id" to "eq.$clubId",
                    "user_id" to "eq.$userId"
                ),
                body = JSONObject().put("status", status).toString(),
                accessToken = accessToken,
                prefer = "return=minimal"
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Member report (v3.3.4): flags toxic behaviour to the club owner.
     * A database trigger drops a notification row on the owner's account.
     */
    suspend fun insertClubReport(
        clubId: String,
        reporterId: String,
        reporterName: String,
        reportedUserId: String,
        reportedUserName: String,
        reason: String,
        details: String,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/club_reports",
                prefer = "return=minimal",
                body = JSONObject()
                    .put("club_id", clubId)
                    .put("reporter_id", reporterId)
                    .put("reporter_name", reporterName)
                    .put("reported_user_id", reportedUserId)
                    .put("reported_user_name", reportedUserName)
                    .put("reason", reason)
                    .put("details", details.take(500))
                    .toString(),
                accessToken = accessToken
            )
            true
        }.getOrDefault(false)
    }

    /**
     * The signed-in account's latest notification rows (v3.3.4 mention
     * pushes): feeds the notification badge, the in-app sheet and the
     * local system-notification render for unseen ones.
     */
    suspend fun fetchNotifications(
        userId: String,
        limit: Int = 40,
        accessToken: String?
    ): List<NotificationItem>? {
        if (!isConfigured()) return null
        return runCatching {
            val raw = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_NOTIFICATIONS}",
                query = mapOf(
                    "select" to "id,title,message,type,is_read,club_id,created_at",
                    "user_id" to "eq.$userId",
                    "order" to "created_at.desc",
                    "limit" to limit.toString()
                ),
                accessToken = accessToken
            )
            val rows = SupabaseClient.parseArray(raw)
            (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val iso = normalizeIsoCursor(row.optString("created_at"))
                NotificationItem(
                    id = row.optString("id"),
                    title = row.optString("title").ifBlank { "Quicky" },
                    message = row.optString("message"),
                    type = row.optString("type").ifBlank { "SYSTEM" },
                    timeAgo = iso.let { LudoTime.parseIsoToEpochMs(it) }
                        ?.let { relativeTimeLabel(it) } ?: "Recently",
                    isRead = row.optBoolean("is_read", false),
                    clubId = row.optString("club_id").takeIf { it.isNotBlank() }
                )
            }
        }.getOrNull()
    }

    /** Compact relative label for notification timestamps. */
    private fun relativeTimeLabel(epochMs: Long): String {
        val diff = System.currentTimeMillis() - epochMs
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000}d ago"
            else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                .format(java.util.Date(epochMs))
        }
    }

    /**
     * Uploads a recorded club voice note into the PRIVATE `voice-notes`
     * bucket under `club-voice/<clubId>/…` (v3.3.4). Authenticated club
     * members can read + write that prefix (storage policies), so every
     * member can play the note back later.
     *
     * @return the storage object path to persist on the message row, or
     *         null when the upload failed.
     */
    suspend fun uploadClubVoiceNote(
        clubId: String,
        senderId: String,
        bytes: ByteArray,
        accessToken: String?
    ): String? {
        if (!isConfigured()) return null
        val objectPath = "club-voice/$clubId/${senderId}_${System.currentTimeMillis()}.m4a"
        // storageUpload returns the object Key (= objectPath) on success,
        // null on failure — failures surface as a "voice not delivered" log.
        val key = SupabaseClient.storageUpload(
            bucket = SupabaseConfig.BUCKET_VOICE_NOTES,
            objectPath = objectPath,
            bytes = bytes,
            contentType = "audio/mp4",
            accessToken = accessToken
        )
        if (key == null) {
            android.util.Log.w(
                "QuickyClubs",
                "Club voice-note upload failed (club=$clubId sender=$senderId)"
            )
        }
        return key
    }

    /**
     * Downloads a club voice note for playback (private bucket, rides
     * the user JWT). Returns null on any failure.
     */
    suspend fun downloadClubVoiceNote(
        objectPath: String,
        accessToken: String?
    ): ByteArray? {
        if (!isConfigured()) return null
        return SupabaseClient.storageDownload(
            bucket = SupabaseConfig.BUCKET_VOICE_NOTES,
            objectPath = objectPath,
            accessToken = accessToken
        )
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
     * Fetches every ACTIVE club + its member list (one batched read each)
     * so the Clubs tab shows clubs created by ANY account — the core of
     * club discovery. The caller derives the user's own membership (and
     * therefore activeClubId) from the returned member lists.
     *
     * @return null when not configured or the request failed (caller keeps
     *         its local state); an empty list when no clubs exist yet.
     */
    suspend fun fetchClubs(accessToken: String?): List<Club>? {
        if (!isConfigured()) return null
        return runCatching {
            // 1) All active clubs, newest first (discovery order).
            val rawClubs = SupabaseClient.rest(
                method = "GET",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUBS}",
                query = mapOf(
                    "select" to "id,owner_id,name,description,logo_emoji,max_members,status,category,created_at",
                    "status" to "eq.ACTIVE",
                    "order" to "created_at.desc",
                    "limit" to "200"
                ),
                accessToken = accessToken
            )
            val clubRows = SupabaseClient.parseArray(rawClubs)
            if (clubRows.length() == 0) return@runCatching emptyList()

            // 2) Member rows (one batched read; the table is small and the
            //    RLS policy allows reads for any authenticated user).
            val membersByClub = mutableMapOf<String, MutableList<ClubMember>>()
            runCatching {
                val rawMembers = SupabaseClient.rest(
                    method = "GET",
                    path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_MEMBERS}",
                    query = mapOf(
                        "select" to "club_id,user_id,user_name,character_badge,is_verified,role,status,joined_at",
                        "order" to "joined_at.asc"
                    ),
                    accessToken = accessToken
                )
                val memberRows = SupabaseClient.parseArray(rawMembers)
                for (i in 0 until memberRows.length()) {
                    val row = memberRows.optJSONObject(i) ?: continue
                    val clubId = row.optString("club_id")
                    if (clubId.isBlank()) continue
                    membersByClub.getOrPut(clubId) { mutableListOf() }.add(
                        ClubMember(
                            id = "${clubId}_${row.optString("user_id")}",
                            userId = row.optString("user_id"),
                            userName = row.optString("user_name").ifBlank { "Member" },
                            characterBadge = row.optString("character_badge").ifBlank { "The Explorer" },
                            isVerified = row.optBoolean("is_verified", true),
                            role = row.optString("role").ifBlank { "MEMBER" },
                            status = row.optString("status").ifBlank { "ACTIVE" },
                            joinedAt = isoToClubLabel(row.optString("joined_at"))
                        )
                    )
                }
            } // member-list failures are non-fatal — clubs still list

            // 2b) v3.3.7 — one batched read of every member's
            //     profiles.allow_club_dm flag so each member's 3-dot menu
            //     knows whether "Chat personally" may be offered. A member
            //     whose profile row is missing defaults to allowed.
            //     Failures are non-fatal (defaults apply).
            val allowDmByUserId = mutableMapOf<String, Boolean>()
            runCatching {
                val memberIds = membersByClub.values.flatten()
                    .map { it.userId }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (memberIds.isNotEmpty()) {
                    val idFilter = memberIds.joinToString(",") { "\"$it\"" }
                    val rawFlags = SupabaseClient.rest(
                        method = "GET",
                        path = "/rest/v1/${SupabaseConfig.TABLE_PROFILES}",
                        query = mapOf(
                            "select" to "id,allow_club_dm",
                            "id" to "in.($idFilter)"
                        ),
                        accessToken = accessToken
                    )
                    val flagRows = SupabaseClient.parseArray(rawFlags)
                    for (i in 0 until flagRows.length()) {
                        val row = flagRows.optJSONObject(i) ?: continue
                        val pid = row.optString("id")
                        if (pid.isNotBlank()) allowDmByUserId[pid] = row.optBoolean("allow_club_dm", true)
                    }
                }
            }

            buildList {
                for (i in 0 until clubRows.length()) {
                    val row = clubRows.optJSONObject(i) ?: continue
                    val id = row.optString("id")
                    if (id.isBlank()) continue
                    add(
                        Club(
                            id = id,
                            ownerId = row.optString("owner_id"),
                            name = row.optString("name").ifBlank { "Unnamed Club" },
                            description = row.optString("description"),
                            logoEmoji = row.optString("logo_emoji").ifBlank { "🎮" },
                            maxMembers = row.optInt("max_members", 15),
                            status = row.optString("status").ifBlank { "ACTIVE" },
                            createdAt = isoToClubLabel(row.optString("created_at")),
                            category = row.optString("category").ifBlank { "Casual Gaming" },
                            members = (membersByClub[id] ?: mutableListOf()).map { member ->
                                member.copy(allowClubDm = allowDmByUserId[member.userId] ?: true)
                            }
                        )
                    )
                }
            }
        }.getOrNull()
    }

    /** "Oct 5"-style label for a Supabase timestamptz ("" → placeholder). */
    private fun isoToClubLabel(iso: String): String {
        val epoch = LudoTime.parseIsoToEpochMs(iso.takeIf { it.isNotBlank() } ?: return "Recently")
        return epoch?.let {
            java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date(it))
        } ?: "Recently"
    }

    /**
     * Persists a newly created club: the `clubs` row + the owner's
     * `club_members` row. Both writes are plain inserts (RLS allows them
     * for authenticated users); the 15-member cap is enforced by the
     * database trigger on club_members.
     */
    suspend fun createClub(club: Club, accessToken: String?): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUBS}",
                // v3.3.2 fix: Prefer as a URL query param 400s with
                // PGRST100 — PostgREST only accepts it as a header.
                prefer = "return=minimal",
                body = JSONObject()
                    .put("id", club.id)
                    .put("owner_id", club.ownerId)
                    .put("name", club.name)
                    .put("description", club.description)
                    .put("logo_emoji", club.logoEmoji)
                    .put("max_members", club.maxMembers)
                    .put("status", club.status)
                    .put("category", club.category)
                    .toString(),
                // v3.3.1 fix: WITHOUT the user JWT the insert runs as the
                // anon role and RLS (auth.role() = 'authenticated') silently
                // rejected it — clubs never reached the database.
                accessToken = accessToken
            )
            val owner = club.members.firstOrNull()
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_MEMBERS}",
                prefer = "return=minimal",
                body = JSONObject()
                    .put("club_id", club.id)
                    .put("user_id", owner?.userId ?: club.ownerId)
                    .put("user_name", owner?.userName ?: "")
                    .put("character_badge", owner?.characterBadge ?: "The Explorer")
                    .put("is_verified", owner?.isVerified ?: true)
                    .put("role", "OWNER")
                    .put("status", "ACTIVE")
                    .toString(),
                accessToken = accessToken
            )
            true
        }.getOrDefault(false)
    }

    /** Adds a member row (Join Club / Leave & Join). */
    suspend fun joinClub(clubId: String, member: ClubMember, accessToken: String?): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_MEMBERS}",
                prefer = "return=minimal",
                body = JSONObject()
                    .put("club_id", clubId)
                    .put("user_id", member.userId)
                    .put("user_name", member.userName)
                    .put("character_badge", member.characterBadge)
                    .put("is_verified", member.isVerified)
                    .put("role", member.role)
                    .put("status", member.status)
                    .toString(),
                // v3.3.1 fix: same RLS-anon trap as createClub — the user
                // JWT must ride along or the member row is rejected.
                accessToken = accessToken
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Removes a member row — used for Leave Club AND for the owner
     * removing another member (the RLS delete policy allows both for
     * authenticated users).
     */
    suspend fun removeClubMembership(clubId: String, userId: String, accessToken: String?): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "DELETE",
                path = "/rest/v1/${SupabaseConfig.TABLE_CLUB_MEMBERS}",
                query = mapOf(
                    "club_id" to "eq.$clubId",
                    "user_id" to "eq.$userId"
                ),
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
    suspend fun insertRow(table: String, row: JSONObject, accessToken: String? = null): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/$table",
                prefer = "return=minimal",
                body = row.toString(),
                accessToken = accessToken
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

    // ----------------------------------------------------------------
    // Push notifications (v3.3) — FCM device tokens
    // ----------------------------------------------------------------

    /**
     * Upserts the caller's FCM registration token into `device_tokens`
     * (one row per account — a second device for the same account takes
     * over the row). Called fire-and-forget on every sign-in and on every
     * FCM token refresh (QuickyPushService); returns false when Supabase
     * isn't configured or the call failed.
     */
    suspend fun upsertDeviceToken(
        userId: String,
        fcmToken: String,
        accessToken: String?
    ): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            SupabaseClient.rest(
                method = "POST",
                path = "/rest/v1/${SupabaseConfig.TABLE_DEVICE_TOKENS}",
                body = JSONObject()
                    .put("user_id", userId)
                    .put("fcm_token", fcmToken)
                    .toString(),
                accessToken = accessToken,
                // PK (user_id) conflict -> update instead of error.
                prefer = "resolution=merge-duplicates"
            )
            true
        }.getOrDefault(false)
    }

    /** Plain HTTP client for external (non-Supabase) geocoding calls. */
    private val geoHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
