package com.mihir.tvhome;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class SpaceScene extends FrameLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView front;
    private ImageView back;
    private final FrameLayout clockGroup;
    private final TextView clock;
    private final TextView date;
    private String[] photos;
    private int imageIndex;
    private long changed;
    private long minute = -1;
    private boolean running;

    static void styleClock(TextView clock, int size) {
        clock.setTextSize(size);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setLetterSpacing(-0.035f);
        clock.setTextColor(Color.WHITE);
        clock.setBackground(null);
        clock.setAlpha(1f);
        clock.setIncludeFontPadding(false);
        clock.setSingleLine(true);
        clock.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        clock.post(() -> {
            float width = Math.max(1f, clock.getPaint().measureText("00:00"));
            clock.getPaint().setShader(new LinearGradient(0, 0, width, clock.getTextSize(),
                    new int[]{0xff9ceaff, 0xffc6b2ff, 0xffffc9dc}, null, Shader.TileMode.CLAMP));
            clock.invalidate();
        });
    }

    SpaceScene(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        front = image(context); back = image(context);
        addView(back, new LayoutParams(-1, -1)); addView(front, new LayoutParams(-1, -1));
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x44050918, 0x88050918, 0xb3050918}));
        addView(shade, new LayoutParams(-1, -1));
        clockGroup = new FrameLayout(context);
        clock = new TextView(context); clock.setGravity(Gravity.CENTER); styleClock(clock, 172);
        clockGroup.addView(clock, new LayoutParams(-2, -2, Gravity.CENTER));
        date = new TextView(context); date.setTextSize(23); date.setTextColor(0xffe4e6ff); date.setGravity(Gravity.CENTER);
        date.setLetterSpacing(0.12f);
        date.setTranslationY(dp(106));
        clockGroup.addView(date, new LayoutParams(-2, -2, Gravity.CENTER));
        addView(clockGroup, new LayoutParams(-1, -1));
        try { photos = context.getAssets().list("wallpapers"); } catch (Exception e) { photos = new String[0]; }
    }

    private ImageView image(Context context) { ImageView image = new ImageView(context); image.setScaleType(ImageView.ScaleType.CENTER_CROP); return image; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    void start() { if (!running) { running = true; handler.post(tick); } }
    void stop() { running = false; handler.removeCallbacksAndMessages(null); front.animate().cancel(); back.animate().cancel(); clockGroup.animate().cancel(); }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            if (now / 60000 != minute) {
                minute = now / 60000;
                Date current = new Date(now);
                clock.setText(new SimpleDateFormat("HH:mm", new Locale("ru")).format(current));
                date.setText(new SimpleDateFormat("EEEE, d MMMM", new Locale("ru")).format(current));
            }
            if (changed == 0 || now - changed >= 90000) {
                if (photos.length > 0) try (InputStream input = getContext().getAssets().open("wallpapers/" + photos[imageIndex++ % photos.length])) {
                    back.setImageBitmap(BitmapFactory.decodeStream(input)); back.setAlpha(0f);
                    front.animate().alpha(0f).setDuration(2400).start();
                    back.animate().alpha(1f).setDuration(2400).withEndAction(() -> {
                        front.setImageDrawable(null);
                        ImageView old = front; front = back; back = old;
                    }).start();
                } catch (Exception ignored) { }
                changed = now;
            }
            handler.postDelayed(this, 1000);
        }
    };
}
