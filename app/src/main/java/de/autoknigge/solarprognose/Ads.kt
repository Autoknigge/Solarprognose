package de.autoknigge.solarprognose

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Einwilligung (UMP), Initialisierung und App-Start-Anzeige. Das Banner liegt weiter unten als Composable. */
object Ads {
    private var initialized = false
    private var appOpen: AppOpenAd? = null
    private var appOpenTried = false // höchstens einmal pro App-Start, nicht bei jeder Drehung

    /**
     * Fragt bei Bedarf die Einwilligung ab und startet danach das Werbe-SDK.
     * [onReady] kommt, sobald Anzeigen angefragt werden dürfen.
     */
    fun start(activity: Activity, showAppOpen: Boolean, onReady: () -> Unit) {
        val ci = UserMessagingPlatform.getConsentInformation(activity)
        fun proceed() {
            if (!ci.canRequestAds()) return
            if (!initialized) {
                initialized = true
                MobileAds.initialize(activity.applicationContext) {}
            }
            onReady()
            if (showAppOpen) loadAppOpen(activity)
        }
        ci.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> proceed() }
            },
            { _ -> proceed() }
        )
    }

    fun privacyOptionsRequired(context: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> }
    }

    private fun loadAppOpen(activity: Activity) {
        if (appOpenTried) return
        appOpenTried = true
        AppOpenAd.load(
            activity.applicationContext,
            BuildConfig.ADMOB_APPOPEN_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpen = ad
                    showAppOpen(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpen = null
                }
            }
        )
    }

    private fun showAppOpen(activity: Activity) {
        val ad = appOpen ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpen = null
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpen = null
            }
        }
        ad.show(activity)
        appOpen = null
    }
}

/** Banner am unteren Rand, in der Breite des Bildschirms. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { c ->
            AdView(c).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(c, widthDp))
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() }
    )
}
