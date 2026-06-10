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
        // MUDANÇA 1: Desativa o testMode nativo para aceitar chaves reais de produção
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

        // MUDANÇA 2: Força o plugin a ler as chaves reais diretamente do seu strings.xml
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

    // =========================================================================
    // CARREGAMENTO E EXIBIÇÃO DE ANÚNCIOS (INTERSTITIAL / REWARDED)
    // =========================================================================

    public void loadInterstitialAd() {
        if (!isInitialized || isInterstitialLoading.get() || interstitialAdUnitId == null) return;
        isInterstitialLoading.set(true);

        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(activity, interstitialAdUnitId, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd ad) {
                interstitialAd = ad;
                isInterstitialLoading.set(false);
                Log.d(TAG, "Anúncio Intersticial carregado com sucesso.");
            }
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                interstitialAd = null;
                isInterstitialLoading.set(false);
                Log.e(TAG, "Falha ao carregar Intersticial: " + error.getMessage());
            }
        });
    }

    public void showInterstitial() {
        if (interstitialAd != null) {
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    loadInterstitialAd(); // Recarrega para a próxima vez
                }
                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                    interstitialAd = null;
                }
            });
            interstitialAd.show(activity);
        } else {
            Log.w(TAG, "Intersticial ainda não está pronto. Tentando carregar...");
            loadInterstitialAd();
        }
    }

    public void loadRewardedAd() {
        if (!isInitialized || isRewardedLoading.get() || rewardedAdUnitId == null) return;
        isRewardedLoading.set(true);

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(activity, rewardedAdUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewardedAd = ad;
                isRewardedLoading.set(false);
                Log.d(TAG, "Anúncio Premiado carregado com sucesso.");
            }
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                rewardedAd = null;
                isRewardedLoading.set(false);
                Log.e(TAG, "Falha ao carregar Premiado: " + error.getMessage());
            }
        });
    }

    public void showRewarded() {
        if (rewardedAd != null) {
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    rewardedAd = null;
                    loadRewardedAd();
                }
                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                    rewardedAd = null;
                }
            });

            rewardedAd.show(activity, new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                    Log.d(TAG, "Utilizador assistiu ao vídeo! Disparando recompensa para o React...");
                    // MUDANÇA 3: EXECUTA A FUNÇÃO DO SEU INDEX.HTML LOGO APÓS O VÍDEO CONCLUIR
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            evaluateJavascript("if(typeof adMobVideoPremiadoConcluido === 'function') { adMobVideoPremiadoConcluido(); }");
                        }
                    });
                }
            });
        } else {
            Log.w(TAG, "Anúncio Premiado não está pronto. Carregando...");
            loadRewardedAd();
        }
    }

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
                        "        showRewarded: function() { if(window.AdMobInterface) return window.AdMobInterface.showRewardedAd(); }\n" +
                        "    };\n" +
                        "}\n";
        evaluateJavascript(adSupportJs);
    }

    @Override public void onResume() {}
    @Override public void onPause() {}
    @Override public void onDestroy() { bannerAd = null; interstitialAd = null; rewardedAd = null; }
    @Override public void evaluateJavascript(String script) { if (webView != null) webView.evaluateJavascript(script, null); }

    // Interface interna para pontes JavaScript diretas do SmartWebView
    public class AdMobJSInterface {
        @JavascriptInterface public void showInterstitialAd() { activity.runOnUiThread(() -> showInterstitial()); }
        @JavascriptInterface public void showRewardedAd() { activity.runOnUiThread(() -> showRewarded()); }
    }
}

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
            Log.d(TAG, "Requested banner ad.");
        });
    }

    public void hideBannerAd() {
        mainHandler.post(() -> {
            if (bannerAd != null && bannerAd.getParent() != null) {
                ((ViewGroup) bannerAd.getParent()).removeView(bannerAd);
                bannerAd.destroy();
                bannerAd = null;
                Log.d(TAG, "Banner ad hidden and destroyed.");
            }
        });
    }

    public void loadInterstitialAd() {
        if (!isInitialized || activity == null || isInterstitialLoading.getAndSet(true)) {
            return;
        }

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
                        if (Boolean.TRUE.equals(config.getOrDefault("autoLoadInterstitial", true))) {
                            loadInterstitialAd();
                        }
                    }
                });
                Log.d(TAG, "Interstitial ad loaded.");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                interstitialAd = null;
                isInterstitialLoading.set(false);
                Log.e(TAG, "Failed to load interstitial ad: " + loadAdError.getMessage());

                // If the error is due to the JS engine, schedule a retry after a delay.
                if (loadAdError.getCode() == 0) { // Code 0 is often an internal error
                    mainHandler.postDelayed(() -> {
                        Log.d(TAG, "Retrying to load interstitial ad after JS engine failure.");
                        loadInterstitialAd();
                    }, 15000); // Retry after 15 seconds
                }
            }
        });
    }

    public boolean showInterstitialAd() {
        if (interstitialAd == null || activity == null) {
            Log.w(TAG, "Interstitial ad not ready.");
            if (!isInterstitialLoading.get()) loadInterstitialAd();
            return false;
        }

        mainHandler.post(() -> interstitialAd.show(activity));
        return true;
    }

    public void loadRewardedAd() {
        if (!isInitialized || activity == null || isRewardedLoading.getAndSet(true)) {
            return;
        }

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
                        if (Boolean.TRUE.equals(config.getOrDefault("autoLoadRewarded", true))) {
                            loadRewardedAd();
                        }
                    }
                });
                Log.d(TAG, "Rewarded ad loaded.");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                rewardedAd = null;
                isRewardedLoading.set(false);
                Log.e(TAG, "Failed to load rewarded ad: " + loadAdError.getMessage());

                // If the error is due to the JS engine, schedule a retry after a delay.
                if (loadAdError.getCode() == 0) {
                    mainHandler.postDelayed(() -> {
                        Log.d(TAG, "Retrying to load rewarded ad after JS engine failure.");
                        loadRewardedAd();
                    }, 15000); // Retry after 15 seconds
                }
            }
        });
    }

        public boolean showRewardedAd() {
        if (rewardedAd == null || activity == null) {
            Log.w(TAG, "Rewarded ad not loaded yet.");
            if (!isRewardedLoading.get()) loadRewardedAd();
            return false;
        }

        mainHandler.post(() -> rewardedAd.show(activity, rewardItem -> {
            Log.d(TAG, "User earned reward: " + rewardItem.getAmount() + " " + rewardItem.getType());
            try {
                JSONObject rewardData = new JSONObject();
                rewardData.put("amount", rewardItem.getAmount());
                rewardData.put("type", rewardItem.getType());
                
                // 1. Mantém o aviso original do Smart WebView por segurança
                evaluateJavascript("if (window.AdMob && window.AdMob.onUserEarnedReward) window.AdMob.onUserEarnedReward(" + rewardData.toString() + ");");
                
                // 2. DISPARA O AVISO DIRETO PARA O SEU APP.TSX (MECÂNICA DA LOJA)
                evaluateJavascript("if (typeof window.adMobVideoPremiadoConcluido === 'function') { window.adMobVideoPremiadoConcluido(); }");
                
            } catch (JSONException e) {
                Log.e(TAG, "Error creating reward JSON", e);
            }
        }));

        return true;
    }

    public boolean isInterstitialAdReady() {
        return interstitialAd != null;
    }

    public boolean isRewardedAdReady() {
        return rewardedAd != null;
    }

    // Métodos adicionais para corrigir o erro de compilação do JSInterfacePlugin
    public boolean showInterstitial() {
        return showInterstitialAd();
    }

    public boolean showRewarded() {
        return showRewardedAd();
    }

    public class AdMobJSInterface {
        @JavascriptInterface
        public void showBannerAd() {
            mainHandler.post(() -> {
                // Correção dinâmica para suportar múltiplos IDs de container comuns do SmartWebView
                int containerId = activity.getResources().getIdentifier("msw_ad_container", "id", activity.getPackageName());
                if (containerId == 0) {
                    containerId = activity.getResources().getIdentifier("swv_ad_container", "id", activity.getPackageName());
                }
                
                if (containerId != 0) {
                    ViewGroup adContainer = activity.findViewById(containerId);
                    if (adContainer != null) {
                        AdMobPlugin.this.showBannerAd(adContainer);
                        return;
                    }
                }
                Log.e(TAG, "Ad container (msw_ad_container/swv_ad_container) not found in layout! Cannot show banner ad.");
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
