package com.example.model

import com.example.BuildConfig

/**
 * ============================================================================
 * PREMIUM GATE — Quicky v2.1 §3.7
 *
 * Single, central choke-point for EVERY premium feature gate in the app.
 * No screen or ViewModel may check `entitlements.isPremium` inline any more;
 * they all route through [isPremium] so QA can validate every feature
 * end-to-end before monetization is re-enabled in v2.2.
 *
 *  - `FORCE_UNLOCKED` is true on debug + internal QA builds
 *    (BuildConfig.FORCE_PREMIUM_UNLOCK — see app/build.gradle.kts).
 *  - Production release builds keep `FORCE_UNLOCKED = false`, so the real
 *    entitlement check applies again without touching any call-site.
 *
 * v2.2 TODO: flip `buildConfigField FORCE_PREMIUM_UNLOCK` back to `false`
 * in the debug build type as well (or remove the flag entirely).
 * ============================================================================
 */
object PremiumGate {

    /**
     * v2.1: forced true for QA.
     * v2.2: revert to the real check (`entitlements.isPremium` only).
     */
    // NOTE: `const` is impossible here — AGP emits BuildConfig.DEBUG as a
    // `Boolean.parseBoolean(...)` initializer, which is not a compile-time
    // constant. A plain val is evaluated once at class-load and is safe
    // because BuildConfig fields never change at runtime.
    val FORCE_UNLOCKED: Boolean = BuildConfig.DEBUG || BuildConfig.FORCE_PREMIUM_UNLOCK

    /** True while the temporary QA unlock is active (drives "Testing: All unlocked" copy). */
    val isTestingUnlockActive: Boolean get() = FORCE_UNLOCKED

    /**
     * The one premium check every gate in the app must call.
     * Accepts the user's [Entitlements] snapshot.
     */
    fun isPremium(entitlements: Entitlements): Boolean =
        FORCE_UNLOCKED || entitlements.isPremium

    /** Convenience overload for gates that only know the subscription flag. */
    fun isPremium(subscribed: Boolean): Boolean = FORCE_UNLOCKED || subscribed
}
