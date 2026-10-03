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
 * ADS MANAGER — Quicky v2.1 §3.6
 *
 * Central AdMob surface for the app:
 *  - Discovery  : native ad card injected every N swipes (see AdConfig)
 *  - Club chat   : 320x50 adaptive banner under the club header
 *
 * All cadence / format / unit-id values live in [AdConfig], which is the
 * single seam designed to be re-wired to Firebase Remote Config in v2.2
 * (google-services.json is not shipped yet, so Remote Config cannot be
 * initialized at runtime today — constants keep the same contract).
 *
 * Unit ids below are Google's OFFICIAL TEST units. Replace via AdConfig
 * before enabling production traffic.
 * ============================================================================
 */
object AdConfig {
    // --- Remote-Config-shaped knobs (v2.2: fetch from Firebase Remote Config) ---

    /** Inject a native ad card after every N swipes in Discovery (PRD recommends N=6). */
    var discoveryNativeEveryNSwipes: Int = 6

    /** Banner ads enabled in Club Chat. */
    var clubBannerEnabled: Boolean = true

    /** Minimum gap between ad loads — AdMob policy forbids refreshing faster than 60s. */
    const val MIN_REFRESH_INTERVAL_MS: Long = 60_000L

    /** Countdown (seconds) the native card blocks swipes before it can be dismissed. */
    const val NATIVE_CARD_COUNTDOWN_SECONDS: Int = 5

    // --- Test ad unit ids (Google-published, safe for development/QA) ---
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
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
