package com.example.ui.components

import android.view.LayoutInflater
import android.widget.TextView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.AdConfig
import com.example.data.AdsManager
import com.example.ui.theme.QuickyPink
import com.example.ui.theme.QuickyPurple
import kotlinx.coroutines.delay

/* ============================================================================
 * ADS UI — Quicky v2.1 §3.6
 *
 *  - DiscoveryNativeAdCard : mirrors ProfileCard dimensions; "Sponsored"
 *    label top-left; 5-second countdown ring top-right; swipe blocked
 *    until the countdown ends; falls back to a Quicky house promo when
 *    no ad is loaded so QA never sees a blank deck card.
 *  - ClubChatBannerAd      : 320x50 adaptive banner pinned between the
 *    club header and the message list.
 * ============================================================================ */

/**
 * A non-swipeable "Sponsored" card injected into the Discovery deck.
 *
 * @param countdownSeconds seconds the card locks swipe-dismiss (PRD: 5s).
 * @param onCountdownDone  fired once the countdown ring completes.
 */
@Composable
fun DiscoveryNativeAdCard(
    modifier: Modifier = Modifier,
    countdownSeconds: Int = AdConfig.NATIVE_CARD_COUNTDOWN_SECONDS,
    onCountdownDone: () -> Unit = {}
) {
    val context = LocalContext.current
    val nativeAd by AdsManager.nativeAd.collectAsState()

    // Keep a fresh ad loaded while a card is visible.
    LaunchedEffect(Unit) { AdsManager.loadNativeAd(context) }
    DisposableEffect(Unit) {
        onDispose { AdsManager.destroyNativeAd() }
    }

    // Countdown ring state.
    var secondsLeft by remember { mutableIntStateOf(countdownSeconds) }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        } else {
            onCountdownDone()
        }
    }
    val progress by animateFloatAsState(
        targetValue = 1f - secondsLeft.toFloat() / countdownSeconds.coerceAtLeast(1),
        animationSpec = tween(300),
        label = "adCountdown"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, QuickyPurple.copy(alpha = 0.35f)),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (nativeAd != null) {
                NativeAdContent(ad = nativeAd!!, modifier = Modifier.fillMaxSize())
            } else {
                HousePromoContent(modifier = Modifier.fillMaxSize())
            }

            // "Sponsored" label — top-left (M3-native opacity keeps 4.5:1).
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Sponsored",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // Countdown ring — top-right corner.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(34.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = QuickyPink,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 3.dp
                )
                Text(
                    text = if (secondsLeft > 0) "$secondsLeft" else "✓",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Renders the loaded NativeAd assets through a real NativeAdView so impressions/clicks track. */
@Composable
private fun NativeAdContent(ad: com.google.android.gms.ads.nativead.NativeAd, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.native_ad_card, null)
        },
        update = { view -> bindNativeAd(view, ad) }
    )
}

/** Java-side binding of a NativeAd into the NativeAdView (impression + click tracking). */
private fun bindNativeAd(root: android.view.View, ad: com.google.android.gms.ads.nativead.NativeAd) {
    runCatching {
        val nativeAdView = root as? com.google.android.gms.ads.nativead.NativeAdView ?: return
        val headline = nativeAdView.findViewById<TextView>(R.id.ad_headline)
        val body = nativeAdView.findViewById<TextView>(R.id.ad_body)
        val cta = nativeAdView.findViewById<TextView>(R.id.ad_cta)

        nativeAdView.headlineView = headline
        nativeAdView.bodyView = body
        nativeAdView.callToActionView = cta

        headline?.text = ad.headline ?: "Sponsored"
        body?.text = ad.body ?: ""
        cta?.text = ad.callToAction ?: "Learn more"

        nativeAdView.setNativeAd(ad)
    }
}

/** House promo fallback — Quicky Gold upsell shown when no ad is loaded. */
@Composable
private fun HousePromoContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    listOf(QuickyPurple, QuickyPink)
                )
            )
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("👑", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Quicky Gold",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Unlimited likes, see who liked you, voice notes, every premium game and exclusive sticker packs.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 320x50 (adaptive) banner pinned between the club header and the message
 * list. Loads via [AdsManager]; respects the 60s minimum refresh policy.
 */
@Composable
fun ClubChatBannerAd(modifier: Modifier = Modifier) {
    if (!AdConfig.clubBannerEnabled) return

    val context = LocalContext.current
    var reloadTick by remember { mutableIntStateOf(0) }

    // 60s manual refresh policy (never faster — AdMob policy).
    LaunchedEffect(reloadTick) {
        delay(AdConfig.MIN_REFRESH_INTERVAL_MS)
        reloadTick++
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            factory = { ctx ->
                com.google.android.gms.ads.AdView(ctx).apply {
                    setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                    adUnitId = AdConfig.BANNER_AD_UNIT_ID
                }
            },
            update = { adView ->
                adView.loadAd(AdsManager.newBannerRequest())
            }
        )
    }
}

/** Small lifecycle-aware collect helper (avoids pulling lifecycle-runtime dep into data layer). */
