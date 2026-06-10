package mgks.os.swv.plugins;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import mgks.os.swv.Functions;
import mgks.os.swv.PluginInterface;
import mgks.os.swv.PluginManager;
import mgks.os.swv.R;
import mgks.os.swv.SWVContext;

public class AdMobPlugin implements PluginInterface {
    private static final String TAG = "AdMobPlugin";
    private Activity activity;
    private WebView webView;
    private Map<String, Object> config;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String bannerAdUnitId;
    private String interstitialAdUnitId;
    private String rewardedAdUnitId;

    private AdView bannerAd;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;

    private boolean isInitialized = false;
    private final AtomicBoolean isInterstitialLoading = new AtomicBoolean(false);
    private final AtomicBoolean isRewardedLoading = new AtomicBoolean(false);

    static {
        Map<String, Object> config = new HashMap<>();
        config.put("testMode", false);  
        config.put("enableJsInterface", true);  
        config.put("autoLoadInterstitial", true);  
        config.put("autoLoadRewarded", true);  

        PluginManager.registerPlugin(new AdMobPlugin(), config);
    }

    @Override
    public void initialize(Activity activity, WebView webView, Functions functions, Map<String, Object> config) {
        this.activity = activity;
        this.webView = webView;
        this.config = config;

        try {
            interstitialAdUnitId = activity.getString(R.string.admob_interstitial_id);
            rewardedAdUnitId = activity.getString(R.string.admob_rewarded_id);
            Log.d(TAG, "IDs reais carregados com sucesso do strings.xml");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar strings nativas de ID, usando fallbacks de teste", e);
            interstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712";
            rewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917";
        }

        MobileAds.initialize(activity, this::onMobileAdsInitialized);

        if (Boolean.TRUE.equals(config.getOrDefault("enableJsInterface", true))) {
            webView.addJavascriptInterface(new AdMobJSInterface(), "AdMobInterface");
        }
    }

    private void onMobileAdsInitialized(InitializationStatus initializationStatus) {
        isInitialized = true;
        loadInterstitialAd();
        loadRewardedAd();
    }

    public void showBannerAd(ViewGroup adContainer) {
        if (!isInitialized || activity == null || adContainer == null) return;
        mainHandler.post(() -> {
            if (bannerAd != null) {
                adContainer.removeView(bannerAd);
                bannerAd.destroy();
            }
            bannerAd = new AdView(activity);
            bannerAd.setAdUnitId(bannerAdUnitId);
            bannerAd.setAdSize(AdSize.BANNER);
            adContainer.addView(bannerAd);
            AdRequest adRequest = new AdRequest.Builder().build();
            bannerAd.loadAd(adRequest);
        });
    }

    public void hideBannerAd() {
        mainHandler.post(() -> {
            if (bannerAd != null && bannerAd.getParent() != null) {
                ((ViewGroup) bannerAd.getParent()).removeView(bannerAd);
                bannerAd.destroy();
                bannerAd = null;
            }
        });
    }

    public void loadInterstitialAd() {
        if (!isInitialized || activity == null || isInterstitialLoading.getAndSet(true)) return;
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(activity, interstitialAdUnitId, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd ad) {
                interstitialAd = ad;
                isInterstitialLoading.set(false);
                interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        interstitialAd = null;
                        loadInterstitialAd();
                    }
                });
            }
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                interstitialAd = null;
                isInterstitialLoading.set(false);
                if (loadAdError.getCode() == 0) {
                    mainHandler.postDelayed(() -> loadInterstitialAd(), 15000);
                }
            }
        });
    }

    public boolean showInterstitialAd() {
        if (interstitialAd == null || activity == null) {
            if (!isInterstitialLoading.get()) loadInterstitialAd();
            return false;
        }
        mainHandler.post(() -> interstitialAd.show(activity));
        return true;
    }

    public void loadRewardedAd() {
        if (!isInitialized || activity == null || isRewardedLoading.getAndSet(true)) return;
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(activity, rewardedAdUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewardedAd = ad;
                isRewardedLoading.set(false);
                rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        rewardedAd = null;
                        loadRewardedAd();
                    }
                });
            }
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                rewardedAd = null;
                isRewardedLoading.set(false);
                if (loadAdError.getCode() == 0) {
                    mainHandler.postDelayed(() -> loadRewardedAd(), 15000);
                }
            }
        });
    }

    public boolean showRewardedAd() {
        if (rewardedAd == null || activity == null) {
            if (!isRewardedLoading.get()) loadRewardedAd();
            return false;
        }
        mainHandler.post(() -> rewardedAd.show(activity, rewardItem -> {
            try {
                JSONObject rewardData = new JSONObject();
                rewardData.put("amount", rewardItem.getAmount());
                rewardData.put("type", rewardItem.getType());
                evaluateJavascript("if (window.AdMob && window.AdMob.onUserEarnedReward) window.AdMob.onUserEarnedReward(" + rewardData.toString() + ");");
                evaluateJavascript("if (typeof window.adMobVideoPremiadoConcluido === 'function') { window.adMobVideoPremiadoConcluido(); }");
            } catch (JSONException e) {
                Log.e(TAG, "Error creating reward JSON", e);
            }
        }));
        return true;
    }

    public boolean isInterstitialAdReady() { return interstitialAd != null; }
    public boolean isRewardedAdReady() { return rewardedAd != null; }
    public boolean showInterstitial() { return showInterstitialAd(); }
    public boolean showRewarded() { return showRewardedAd(); }

    @Override public String getPluginName() { return "AdMobPlugin"; }
    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {}
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {}
    @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
    @Override public void onPageStarted(String url) {}
    @Override public void onPageFinished(String url) { if (Boolean.TRUE.equals(config.getOrDefault("enableJsInterface", true))) injectAdSupportJs(); }

    private void injectAdSupportJs() {
        String adSupportJs =
                "if (!window.AdMob) {\n" +
                        "    window.AdMob = {\n" +
                        "        showInterstitial: function() { if(window.AdMobInterface) return window.AdMobInterface.showInterstitialAd(); },\n" +
                        "        showRewarded: function() { if(window.AdMobInterface) return window.AdMobInterface.showRewardedAd(); },\n" +
                        "        isInterstitialReady: function() { if(window.AdMobInterface) return window.AdMobInterface.isInterstitialAdReady(); },\n" +
                        "        isRewardedReady: function() { if(window.AdMobInterface) return window.AdMobInterface.isRewardedAdReady(); }\n" +
                        "    };\n" +
                        "}\n";
        evaluateJavascript(adSupportJs);
    }

    @Override public void onResume() {}
    @Override public void onPause() {}
    @Override 
    public void onDestroy() { 
        bannerAd = null; 
        interstitialAd = null; 
        rewardedAd = null; 
    }

    @Override 
    public void evaluateJavascript(String script) { 
        if (webView != null) {
            webView.evaluateJavascript(script, null); 
        }
    }

    // Interface interna para pontes JavaScript diretas do SmartWebView
    public class AdMobJSInterface {
        @JavascriptInterface
        public void showBannerAd() {
            mainHandler.post(() -> {
                int containerId = activity.getResources().getIdentifier("msw_ad_container", "id", activity.getPackageName());
                if (containerId == 0) {
                    containerId = activity.getResources().getIdentifier("swv_ad_container", "id", activity.getPackageName());
                }
                if (containerId != 0) {
                    ViewGroup adContainer = activity.findViewById(containerId);
                    if (adContainer != null) {
                        AdMobPlugin.this.showBannerAd(adContainer);
                    }
                }
            });
        }

        @JavascriptInterface 
        public void hideBannerAd() { 
            AdMobPlugin.this.hideBannerAd(); 
        }

        @JavascriptInterface 
        public boolean showInterstitialAd() { 
            return AdMobPlugin.this.showInterstitialAd(); 
        }

        @JavascriptInterface 
        public boolean showRewardedAd() { 
            return AdMobPlugin.this.showRewardedAd(); 
        }

        @JavascriptInterface 
        public boolean isInterstitialAdReady() { 
            return AdMobPlugin.this.isInterstitialAdReady(); 
        }

        @JavascriptInterface 
        public boolean isRewardedAdReady() { 
            return AdMobPlugin.this.isRewardedAdReady(); 
        }
    }
}
