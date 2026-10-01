package com.example.data

/**
 * ============================================================
 *  SUPABASE CREDENTIALS — PLACEHOLDERS
 * ============================================================
 *
 *  ⚠️ TODO: Fill these in from your local environment:
 *
 *    1. Open your Supabase dashboard
 *       → Project Settings → API
 *    2. Copy the "Project URL" into [SUPABASE_URL]
 *    3. Copy the "anon / public" API key into [SUPABASE_ANON_KEY]
 *
 *  The anon key is safe to ship in a mobile client ONLY when your
 *  tables are protected with Row Level Security (RLS) policies.
 *  Never put the "service_role" key in a mobile app.
 * ============================================================
 */
object SupabaseConfig {

    /** e.g. "https://abcdefghij1234.supabase.co" */
    const val SUPABASE_URL = "https://orcukgfdwludlmfxgdpp.supabase.co"

    /** anon public key from Dashboard → Settings → API */
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9yY3VrZ2Zkd2x1ZGxtZnhnZHBwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA4NDY5NzAsImV4cCI6MjEwNjQyMjk3MH0.0NrH1eZmeMJjo77gZMJ_2PeLUAFWqMkPO7JX8GOpadA"

    // ------------------------------------------------------------
    // Storage buckets (create these in Supabase → Storage)
    // ------------------------------------------------------------
    const val BUCKET_PROFILE_PHOTOS = "profile-photos"
    const val BUCKET_VOICE_NOTES = "voice-notes"
    const val BUCKET_STICKERS = "stickers"

    // ------------------------------------------------------------
    // Database table names (public schema, accessed via PostgREST)
    // ------------------------------------------------------------
    const val TABLE_PROFILES = "profiles"
    const val TABLE_MATCHES = "matches"
    const val TABLE_MESSAGES = "messages"
    const val TABLE_CLUBS = "clubs"
    const val TABLE_CLUB_MEMBERS = "club_members"
    const val TABLE_CLUB_MESSAGES = "club_messages"
    const val TABLE_NOTIFICATIONS = "notifications"
    const val TABLE_GAMES = "games"
    const val TABLE_GAME_PROMPTS = "game_prompts"

    /**
     * True once real credentials have been filled in above.
     * The app stays fully offline / empty until then.
     */
    val isConfigured: Boolean
        get() = !SUPABASE_URL.contains("YOUR_") &&
                !SUPABASE_ANON_KEY.contains("YOUR_")
}
