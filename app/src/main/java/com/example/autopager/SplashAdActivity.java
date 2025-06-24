package com.example.autopager;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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
    private boolean hasJumped = false; // 防止多次跳转
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

    /**
     * 拉取广告配置，拉取失败时会尝试加载本地缓存
     */
    private void fetchAdConfig() {
        Request request = new Request.Builder().url(AD_CONFIG_URL).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "广告配置访问失败: " + e.getMessage() + "，尝试加载本地缓存");
                loadCachedAdConfigOrFinish();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "广告配置访问失败，code: " + response.code() + "，尝试加载本地缓存");
                    loadCachedAdConfigOrFinish();
                    return;
                }
                try {
                    String body = response.body().string();
                    // 拉取成功，保存到本地缓存
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

    /**
     * 尝试加载本地缓存广告配置，否则直接跳转主界面
     */
    private void loadCachedAdConfigOrFinish() {
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
        runOnUiThread(this::jumpToMain);
    }

    /**
     * 随机展示一条广告
     */
    private void showRandomAd(JSONObject json) {
        runOnUiThread(() -> {
            try {
                JSONArray adsArray = json.getJSONArray("ads");
                if (adsArray.length() == 0) {
                    Log.e(TAG, "广告列表为空");
                    jumpToMain();
                    return;
                }
                List<JSONObject> ads = new ArrayList<>();
                for (int i = 0; i < adsArray.length(); i++) {
                    ads.add(adsArray.getJSONObject(i));
                }
                int index = new Random().nextInt(ads.size());
                JSONObject ad = ads.get(index);
                String adType = ad.optString("type", "image");
                Log.d(TAG, "随机到第 " + index + " 个广告，类型: " + adType);
                showAd(ad, json.optJSONObject("global"));
            } catch (Exception e) {
                Log.e(TAG, "展示广告异常: " + e.getMessage(), e);
                jumpToMain();
            }
        });
    }

    /**
     * 展示广告（图片或视频）
     */
    private void showAd(JSONObject ad, JSONObject globalConfig) {
        String type = ad.optString("type", "image");
        int duration = globalConfig != null ? globalConfig.optInt("default_display_duration", DEFAULT_SPLASH_DURATION) : DEFAULT_SPLASH_DURATION;

        if ("video".equals(type)) {
            showVideoAd(ad, duration);
        } else {
            showImageAd(ad, duration);
        }
    }

    /**
     * 展示图片广告
     */
    private void showImageAd(JSONObject ad, int duration) {
        imgAd.setVisibility(View.VISIBLE);
        videoAd.setVisibility(View.GONE);
        String imageUrl = ad.optString("image_url");
        Glide.with(this).load(imageUrl).into(imgAd);

        skipRunnable = this::jumpToMain;
        handler.postDelayed(skipRunnable, duration);
    }

    /**
     * 展示视频广告，支持本地缓存
     */
    private void showVideoAd(JSONObject ad, int duration) {
        String videoUrl = ad.optString("video_url");
        String coverUrl = ad.optString("image_url", null);

        imgAd.setVisibility(View.GONE);
        videoAd.setVisibility(View.VISIBLE);

        // 展示封面图直到视频准备好
        if (coverUrl != null && !coverUrl.isEmpty()) {
            imgAd.setVisibility(View.VISIBLE);
            Glide.with(this).load(coverUrl).into(imgAd);
        }

        // 生成本地缓存文件
        String videoFileName = String.valueOf(videoUrl.hashCode()) + ".mp4";
        File videoFile = new File(getFilesDir(), videoFileName);

        if (videoFile.exists() && videoFile.length() > 0 && isVideoFileValid(videoFile)) {
            Log.d(TAG, "本地已缓存视频，直接播放: " + videoFile.getAbsolutePath());
            playVideo(Uri.fromFile(videoFile));
        } else {
            Log.d(TAG, "本地无缓存或缓存损坏，使用网络播放并尝试缓存: " + videoUrl);
            playVideo(Uri.parse(videoUrl));
            cacheVideoToLocal(videoUrl, videoFile);
        }
    }

    /**
     * 播放视频文件
     */
    private void playVideo(Uri videoUri) {
        videoAd.setVideoURI(videoUri);
        videoAd.setOnPreparedListener(mp -> {
            imgAd.setVisibility(View.GONE);
            videoAd.start();
        });
        videoAd.setOnCompletionListener(mp -> jumpToMain());
        videoAd.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "视频播放出错: what=" + what + ", extra=" + extra);
            jumpToMain();
            return true;
        });
    }

    /**
     * 下载视频文件并缓存到本地
     */
    private void cacheVideoToLocal(String videoUrl, File destFile) {
        Request request = new Request.Builder().url(videoUrl).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "视频缓存失败: " + e.getMessage());
                // 下载失败可考虑删除损坏的文件
                if (destFile.exists()) destFile.delete();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
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
                    Log.d(TAG, "视频缓存成功: " + destFile.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "视频缓存写入异常: " + e.getMessage());
                    if (destFile.exists()) destFile.delete();
                }
            }
        });
    }

    /**
     * 简单检查视频文件有效性
     */
    private boolean isVideoFileValid(File file) {
        if (file == null || !file.exists() || file.length() == 0) return false;
        String mime = URLConnection.guessContentTypeFromName(file.getName());
        return mime != null && mime.startsWith("video");
    }

    /**
     * 跳转到主界面
     */
    private void jumpToMain() {
        if (hasJumped) return;
        hasJumped = true;
        handler.removeCallbacks(skipRunnable);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(skipRunnable);
    }
}