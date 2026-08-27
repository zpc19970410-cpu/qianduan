package com.example.bossjdbridge;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String DEFAULT_URL = "http://192.168.0.228:17891";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("BOSS JD → ChatGPT");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setText("电脑端地址");
        label.setTextSize(15);
        label.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpLabel.topMargin = pad;
        root.addView(label, lpLabel);

        EditText serverUrl = new EditText(this);
        SharedPreferences prefs = getSharedPreferences("bridge", MODE_PRIVATE);
        serverUrl.setSingleLine(true);
        serverUrl.setText(prefs.getString("serverUrl", DEFAULT_URL));
        root.addView(serverUrl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button start = new Button(this);
        start.setText("保存并启动悬浮按钮");
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpBtn.topMargin = pad / 2;
        root.addView(start, lpBtn);

        TextView note = new TextView(this);
        note.setText("使用：BOSS JD复制器复制岗位JD → 点悬浮“发送JD” → 等待生成 → 点“粘贴招呼语”。");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams lpNote = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpNote.topMargin = pad;
        root.addView(note, lpNote);

        setContentView(root);

        start.setOnClickListener(v -> {
            String url = serverUrl.getText().toString().trim();
            while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                Toast.makeText(this, "请输入完整地址，例如 http://192.168.0.228:17891", Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putString("serverUrl", url).apply();

            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "请允许“显示在其他应用上层”，返回后再点一次启动。", Toast.LENGTH_LONG).show();
                return;
            }

            Intent service = new Intent(this, OverlayService.class);
            startForegroundService(service);
            Toast.makeText(this, "悬浮按钮已启动", Toast.LENGTH_SHORT).show();
        });
    }
}
