package com.example.data

import com.example.model.GameDefinition
import com.example.model.TruthOrDarePrompt
import org.json.JSONObject

/**
 * Data-access layer for Supabase (database + storage).
 *
 * While credentials are placeholders (see [SupabaseConfig]) every
 * function short-circuits and the app runs in a clean, empty state.
 *
 * Expected table shapes (create in Supabase → Table Editor + enable RLS):
 *
 *  games:           id TEXT PK, name TEXT, description TEXT,
 *                   is_free BOOLEAN, tag TEXT, players_count TEXT
 *
 *  game_prompts:    id TEXT PK, category TEXT, type TEXT,
 *                   text TEXT, difficulty TEXT
 *
 *  messages:        id UUID PK DEFAULT gen_random_uuid(),
 *                   conversation_id TEXT, sender_id TEXT,
 *                   text TEXT, created_at TIMESTAMPTZ DEFAULT now()
 *
 *  club_messages:   id UUID PK DEFAULT gen_random_uuid(),
 *                   club_id TEXT, sender_id TEXT, sender_name TEXT,
 *                   message_type TEXT, text TEXT, created_at TIMESTAMPTZ
 *
 *  profiles / matches / clubs / club_members / notifications:
 *  TODO(Supabase) — map these once image handling moves from local
 *  drawable res IDs to remote URLs (Coil is already a dependency).
 */
object SupabaseRepository {

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

    // ==============================================================
    // WRITES
    // ==============================================================

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
     */
    suspend fun uploadProfilePhoto(
        userId: String,
        bytes: ByteArray,
        ext: String = "jpg"
    ): String? {
        if (!isConfigured()) return null
        val objectPath = "$userId/avatar_${System.currentTimeMillis()}.$ext"
        val key = SupabaseClient.storageUpload(
            bucket = SupabaseConfig.BUCKET_PROFILE_PHOTOS,
            objectPath = objectPath,
            bytes = bytes,
            contentType = "image/$ext"
        ) ?: return null
        return SupabaseClient.storagePublicUrl(SupabaseConfig.BUCKET_PROFILE_PHOTOS, objectPath)
            .ifEmpty { key }
    }
}
