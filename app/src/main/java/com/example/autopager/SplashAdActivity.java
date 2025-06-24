package com.example.autopager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.VideoView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SplashAdActivity extends Activity {
    private static final String TAG = "SplashAdActivity";
    private static final String AD_CONFIG_URL = "https://dongjingsheng.github.io/ad-assets/ad_config.json";
    private static final int DEFAULT_SPLASH_DURATION = 3500; // ms
    private static final String PREF_AD_JSON = "pref_ad_json";
    private static final String PREF_AD_KEY = "ad_json";

    private Handler handler;
    private Runnable skipRunnable;
    private ImageView imgAd;
    private VideoView videoAd;
    private Button btnSkip;
    private boolean hasJumped = false;
    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_ad);

        handler = new Handler();
        okHttpClient = new OkHttpClient();

        imgAd = findViewById(R.id.img_ad);
        videoAd = findViewById(R.id.video_ad);
        btnSkip = findViewById(R.id.btn_skip);

        btnSkip.setOnClickListener(v -> jumpToMain());

        fetchAdConfig();
    }

    private void fetchAdConfig() {
        Request request = new Request.Builder().url(AD_CONFIG_URL).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "广告配置访问失败: " + e.getMessage());
                loadCachedAdConfigOrFinish();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "广告配置访问失败，code: " + response.code());
                    loadCachedAdConfigOrFinish();
                    return;
                }
                try {
                    String body = response.body().string();
                    if (body == null || body.isEmpty()) {
                        loadCachedAdConfigOrFinish();
                        return;
                    }
                    getSharedPreferences(PREF_AD_JSON, MODE_PRIVATE).edit().putString(PREF_AD_KEY, body).apply();
                    JSONObject json = new JSONObject(body);
                    showRandomAd(json);
                } catch (Exception e) {
                    Log.e(TAG, "广告配置解析异常: " + e.getMessage(), e);
                    loadCachedAdConfigOrFinish();
                }
            }
        });
    }

    private void loadCachedAdConfigOrFinish() {
        runOnUiThread(() -> {
            String cachedJson = getSharedPreferences(PREF_AD_JSON, MODE_PRIVATE).getString(PREF_AD_KEY, null);
            if (cachedJson != null) {
                try {
                    JSONObject json = new JSONObject(cachedJson);
                    showRandomAd(json);
                    return;
                } catch (Exception ex) {
                    Log.e(TAG, "本地缓存广告配置解析异常: " + ex.getMessage(), ex);
                }
            }
            jumpToMain();
        });
    }

    private void showRandomAd(JSONObject json) {
        runOnUiThread(() -> {
            try {
                JSONArray adsArray = json.optJSONArray("ads");
                if (adsArray == null || adsArray.length() == 0) {
                    jumpToMain();
                    return;
                }
                List<JSONObject> ads = new ArrayList<>();
                for (int i = 0; i < adsArray.length(); i++) {
                    JSONObject adObj = adsArray.optJSONObject(i);
                    if (adObj != null) ads.add(adObj);
                }
                if (ads.isEmpty()) {
                    jumpToMain();
                    return;
                }
                int index = new Random().nextInt(ads.size());
                JSONObject ad = ads.get(index);
                showAd(ad, json.optJSONObject("global"));
            } catch (Exception e) {
                Log.e(TAG, "展示广告异常: " + e.getMessage(), e);
                jumpToMain();
            }
        });
    }

    private void showAd(JSONObject ad, JSONObject globalConfig) {
        if (ad == null) {
            jumpToMain();
            return;
        }
        String type = ad.optString("type", "image");
        int duration = DEFAULT_SPLASH_DURATION;
        if (globalConfig != null) {
            duration = globalConfig.optInt("default_display_duration", DEFAULT_SPLASH_DURATION);
        }

        if ("video".equals(type)) {
            showVideoAd(ad, duration);
        } else {
            showImageAd(ad, duration);
        }
    }

    private void showImageAd(JSONObject ad, int duration) {
        imgAd.setVisibility(View.VISIBLE);
        videoAd.setVisibility(View.GONE);
        String imageUrl = ad.optString("image_url");
        if (imageUrl == null || imageUrl.isEmpty()) {
            jumpToMain();
            return;
        }
        try {
            Glide.with(this).load(imageUrl).into(imgAd);
        } catch (Exception e) {
            Log.e(TAG, "图片加载异常: " + e.getMessage());
            jumpToMain();
            return;
        }

        skipRunnable = this::jumpToMain;
        handler.postDelayed(skipRunnable, duration);
    }

    private void showVideoAd(JSONObject ad, int duration) {
        String videoUrl = ad.optString("video_url");
        String coverUrl = ad.optString("image_url", null);

        imgAd.setVisibility(View.GONE);
        videoAd.setVisibility(View.VISIBLE);

        if (coverUrl != null && !coverUrl.isEmpty()) {
            imgAd.setVisibility(View.VISIBLE);
            try {
                Glide.with(this).load(coverUrl).into(imgAd);
            } catch (Exception e) {
                Log.e(TAG, "封面图片加载异常: " + e.getMessage());
            }
        }

        if (videoUrl == null || videoUrl.isEmpty()) {
            jumpToMain();
            return;
        }

        String videoFileName = videoUrl.hashCode() + ".mp4";
        File videoFile = new File(getFilesDir(), videoFileName);

        if (videoFile.exists() && videoFile.length() > 0 && isVideoFileValid(videoFile)) {
            playVideo(android.net.Uri.fromFile(videoFile));
        } else {
            playVideo(android.net.Uri.parse(videoUrl));
            cacheVideoToLocal(videoUrl, videoFile);
        }

        skipRunnable = this::jumpToMain;
        handler.postDelayed(skipRunnable, duration + 2000);
    }

    private void playVideo(android.net.Uri videoUri) {
        try {
            videoAd.setVideoURI(videoUri);
        } catch (Exception e) {
            Log.e(TAG, "设置视频播放路径异常: " + e.getMessage());
            jumpToMain();
            return;
        }
        videoAd.setOnPreparedListener(mp -> {
            imgAd.setVisibility(View.GONE);
            try {
                videoAd.start();
            } catch (Exception e) {
                Log.e(TAG, "视频播放异常: " + e.getMessage());
                jumpToMain();
            }
        });
        videoAd.setOnCompletionListener(mp -> jumpToMain());
        videoAd.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "视频播放出错: what=" + what + ", extra=" + extra);
            jumpToMain();
            return true;
        });
    }

    private void cacheVideoToLocal(String videoUrl, File destFile) {
        if (videoUrl == null || videoUrl.isEmpty() || destFile == null) return;
        Request request = new Request.Builder().url(videoUrl).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "视频缓存失败: " + e.getMessage());
                if (destFile.exists()) destFile.delete();
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "视频缓存失败, 响应码: " + response.code());
                    if (destFile.exists()) destFile.delete();
                    return;
                }
                try (InputStream in = response.body().byteStream();
                     FileOutputStream out = new FileOutputStream(destFile)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "视频缓存写入异常: " + e.getMessage());
                    if (destFile.exists()) destFile.delete();
                }
            }
        });
    }

    private boolean isVideoFileValid(File file) {
        if (file == null || !file.exists() || file.length() == 0) return false;
        String mime = URLConnection.guessContentTypeFromName(file.getName());
        return mime != null && mime.startsWith("video");
    }

    private void jumpToMain() {
        if (hasJumped) return;
        hasJumped = true;
        if (handler != null && skipRunnable != null) {
            handler.removeCallbacks(skipRunnable);
        }
        try {
            startActivity(new Intent(this, MainActivity.class));
        } catch (Exception e) {
            Log.e(TAG, "跳转主界面异常: " + e.getMessage());
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && skipRunnable != null) {
            handler.removeCallbacks(skipRunnable);
        }
    }
}