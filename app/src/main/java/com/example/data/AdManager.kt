package com.example.data

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ============================================================================
 * ADS MANAGER — Quicky v2.1 §3.6 (revised by PRD v2.3 §29/§30)
 *
 * Central AdMob surface for the app:
 *  - Discovery  : native ad card injected after a RANDOM 1–6 swipe
 *                 threshold (regenerated after every ad — PRD v2.3 §17/§18;
 *                 the cadence lives in SparkViewModel's session state, NOT
 *                 here, because it must re-roll per account/session §45).
 *  - Club chat   : 320x50 adaptive banner under the club header.
 *  - Chat        : anchored adaptive banner fixed under the personal-chat
 *                 header (free users only — PRD v2.3 §26–§28).
 *
 * ALL ad unit ids live here — the single point of configuration. To switch
 * to production traffic, replace ONLY the values in [AdConfig] below (never
 * the screens). The ids are Google's OFFICIAL TEST units (PRD v2.3 §30):
 * keep test ads enabled during development, swap before publishing.
 * ============================================================================
 */
object AdConfig {
    // --- Remote-Config-shaped knobs (v2.2: fetch from Firebase Remote Config) ---

    /** Banner ads enabled in Club Chat. */
    var clubBannerEnabled: Boolean = true

    /** Banner ads enabled in the personal chat header (PRD v2.3 §26). */
    var chatBannerEnabled: Boolean = true

    /** Minimum gap between ad loads — AdMob policy forbids refreshing faster than 60s. */
    const val MIN_REFRESH_INTERVAL_MS: Long = 60_000L

    /** Countdown (seconds) the native card blocks swipes before it can be dismissed. */
    const val NATIVE_CARD_COUNTDOWN_SECONDS: Int = 5

    // --- Test ad unit ids (Google-published, safe for development/QA) ---
    // PRD v2.3 §30 — replace ONLY these values with production ids before
    // publishing; every screen references AdConfig, never a raw id.
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    const val ANCHORED_ADAPTIVE_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    const val INLINE_ADAPTIVE_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    const val NATIVE_VIDEO_AD_UNIT_ID = "ca-app-pub-3940256099942544/1044960115"
    const val PICTURE_IN_PICTURE_AD_UNIT_ID = "ca-app-pub-3940256099942544/9657123429"

    /** Personal-chat header banner (PRD v2.3 §28 — anchored adaptive test unit). */
    const val CHAT_BANNER_AD_UNIT_ID = ANCHORED_ADAPTIVE_BANNER_AD_UNIT_ID
}

object AdsManager {

    private val initialized = AtomicBoolean(false)

    /** The currently held native ad (null while none is loaded / after destroy). */
    private val _nativeAd = MutableStateFlow<NativeAd?>(null)
    val nativeAd: StateFlow<NativeAd?> = _nativeAd.asStateFlow()

    private var lastNativeLoadAt: Long = 0L

    /** Idempotent MobileAds bootstrap — called from MainActivity.onCreate. */
    fun initialize(context: Context) {
        if (initialized.getAndSet(true)) return
        runCatching {
            MobileAds.initialize(context) { /* status — nothing to do */ }
        }
    }

    /**
     * Loads a fresh native ad for the Discovery card. Respects the 60s
     * minimum refresh policy. The exposed [nativeAd] flow updates when the
     * load completes; a failed load leaves the previous value (or null),
     * letting the UI fall back to a house promo card.
     */
    fun loadNativeAd(context: Context) {
        if (System.currentTimeMillis() - lastNativeLoadAt < AdConfig.MIN_REFRESH_INTERVAL_MS) return
        lastNativeLoadAt = System.currentTimeMillis()

        runCatching {
            val loader = AdLoader.Builder(context, AdConfig.NATIVE_AD_UNIT_ID)
                .forNativeAd { ad -> _nativeAd.value = ad }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // Keep null — the Discovery card renders its house fallback.
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_LEFT)
                        .build()
                )
                .build()
            loader.loadAd(AdRequest.Builder().build())
        }
    }

    /**
     * Releases the held native ad — call when the ad card scrolls off /
     * is dismissed so no memory is leaked and no ghost impressions count.
     */
    fun destroyNativeAd() {
        _nativeAd.value?.destroy()
        _nativeAd.value = null
    }

    /** Convenience for banner slots: a plain request object. */
    fun newBannerRequest(): AdRequest = AdRequest.Builder().build()
}
