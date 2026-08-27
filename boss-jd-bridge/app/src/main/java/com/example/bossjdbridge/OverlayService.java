package com.example.bossjdbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private LinearLayout overlay;
    private TextView sendButton;
    private TextView pasteButton;
    private volatile String latestGreeting = "";
    private volatile String activeTaskId = "";
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification notification = new Notification.Builder(this, "boss_jd_bridge")
                .setContentTitle("BOSS JD Bridge")
                .setContentText("悬浮按钮运行中")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .build();
        startForeground(1001, notification);
        showOverlay();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("boss_jd_bridge", "BOSS JD Bridge", NotificationManager.IMPORTANCE_LOW));
    }

    private TextView makeButton(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(13f);
        int h = (int)(22 * getResources().getDisplayMetrics().density);
        int w = (int)(18 * getResources().getDisplayMetrics().density);
        v.setPadding(h, w, h, w);
        v.setBackgroundColor(0xEE222222);
        v.setTextColor(0xFFFFFFFF);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void showOverlay() {
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.END);

        sendButton = makeButton("发送JD");
        pasteButton = makeButton("粘贴招呼语");
        pasteButton.setAlpha(0.55f);
        overlay.addView(sendButton);
        overlay.addView(pasteButton);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        p.x = 12;
        p.y = 0;
        windowManager.addView(overlay, p);

        sendButton.setOnClickListener(v -> sendClipboardJD());
        pasteButton.setOnClickListener(v -> copyGreeting());
    }

    private String serverUrl() {
        String s = getSharedPreferences("bridge", MODE_PRIVATE).getString("serverUrl", "http://192.168.0.228:17891");
        return s == null ? "" : s.replaceAll("/+$", "");
    }

    private void sendClipboardJD() {
        ClipboardManager cb = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        String text = "";
        if (cb.getPrimaryClip() != null && cb.getPrimaryClip().getItemCount() > 0) {
            CharSequence cs = cb.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (cs != null) text = cs.toString().trim();
        }
        if (text.isEmpty()) {
            Toast.makeText(this, "剪贴板里没有岗位 JD", Toast.LENGTH_SHORT).show();
            return;
        }
        final String jd = text;
        latestGreeting = "";
        activeTaskId = "";
        sendButton.setText("发送中…");
        pasteButton.setText("生成中…");
        pasteButton.setAlpha(0.55f);

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("text", jd);
                body.put("source", "boss-jd-copier");
                String response = request("POST", serverUrl() + "/api/jd", body.toString());
                String taskId = new JSONObject(response).optString("taskId", "");
                if (taskId.isEmpty()) throw new Exception("没有收到 taskId");
                activeTaskId = taskId;
                main.post(() -> {
                    sendButton.setText("发送JD");
                    Toast.makeText(this, "JD 已发送，等待 ChatGPT 回复", Toast.LENGTH_SHORT).show();
                });
                pollTask(taskId);
            } catch (Exception e) {
                main.post(() -> {
                    sendButton.setText("发送JD");
                    pasteButton.setText("粘贴招呼语");
                    pasteButton.setAlpha(0.55f);
                    Toast.makeText(this, "发送失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void pollTask(String taskId) {
        long deadline = System.currentTimeMillis() + 190000;
        try {
            while (System.currentTimeMillis() < deadline && taskId.equals(activeTaskId)) {
                String response = request("GET", serverUrl() + "/api/task/" + taskId, null);
                JSONObject task = new JSONObject(response).optJSONObject("task");
                String status = task == null ? "" : task.optString("status", "");
                String greeting = task == null ? "" : task.optString("greeting", "").trim();
                if ("completed".equals(status) && !greeting.isEmpty()) {
                    latestGreeting = greeting;
                    main.post(() -> {
                        pasteButton.setText("粘贴招呼语 ✓");
                        pasteButton.setAlpha(1f);
                        Toast.makeText(this, "招呼语已生成", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                if ("failed".equals(status)) {
                    String msg = task == null ? "" : task.optString("message", "");
                    main.post(() -> {
                        pasteButton.setText("生成失败");
                        Toast.makeText(this, "生成失败：" + msg, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                Thread.sleep(1000);
            }
            main.post(() -> {
                if (latestGreeting.isEmpty()) {
                    pasteButton.setText("生成超时");
                    Toast.makeText(this, "等待 ChatGPT 回复超时", Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            main.post(() -> {
                pasteButton.setText("获取失败");
                Toast.makeText(this, "获取回复失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private String request(String method, String endpoint, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) sb.append(line);
            }
        }
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("电脑端返回 " + code + "：" + sb);
        return sb.toString();
    }

    private void copyGreeting() {
        if (latestGreeting.isEmpty()) {
            Toast.makeText(this, "招呼语还没有生成完成", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cb = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("BOSS招呼语", latestGreeting));
        Toast.makeText(this, "招呼语已复制，可直接在 BOSS 粘贴", Toast.LENGTH_SHORT).show();
    }

    @Override public void onDestroy() {
        if (overlay != null && windowManager != null) windowManager.removeView(overlay);
        overlay = null;
        super.onDestroy();
    }
}
