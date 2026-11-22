package com.mihir.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SpaceScene extends FrameLayout {
    private static final long PHOTO_INTERVAL_MS = 90_000L;
    private static final long CLOCK_INTERVAL_MS = 60_000L;
    private static final int MAX_PHOTO_PIXELS = 1920 * 1080;
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", RUSSIAN);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", RUSSIAN);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FrameLayout photoLayers;
    private final TextView clock;
    private final TextView date;
    private final String[] photos;
    private ImageView front;
    private ImageView back;
    private ExecutorService loader;
    private int imageIndex;

    static void styleClock(TextView clock, int size) {
        clock.setTextSize(size);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setLetterSpacing(-0.035f);
        clock.setTextColor(0xfff3f7ff);
        clock.setBackground(null);
        clock.getPaint().setShader(null);
        clock.setAlpha(1f);
        clock.setIncludeFontPadding(false);
        clock.setSingleLine(true);
        clock.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
    }

    SpaceScene(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        photoLayers = new FrameLayout(context);
        front = image(context);
        back = image(context);
        photoLayers.addView(front, new LayoutParams(-1, -1));
        photoLayers.addView(back, new LayoutParams(-1, -1));
        addView(photoLayers, new LayoutParams(-1, -1));

        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x60050c17, 0x97050b17, 0xba050b17}));
        addView(shade, new LayoutParams(-1, -1));

        clock = new TextView(context);
        clock.setGravity(Gravity.CENTER);
        styleClock(clock, 172);
        addView(clock, new LayoutParams(-2, -2, Gravity.CENTER));
        date = new TextView(context);
        date.setTextSize(23);
        date.setTextColor(GlassSurface.SECONDARY_TEXT);
        date.setGravity(Gravity.CENTER);
        date.setLetterSpacing(0.08f);
        date.setTranslationY(106 * getResources().getDisplayMetrics().density);
        addView(date, new LayoutParams(-2, -2, Gravity.CENTER));
        photos = listPhotos(context.getAssets());
    }

    private static String[] listPhotos(AssetManager assets) {
        try {
            String[] names = assets.list("wallpapers");
            return names == null ? new String[0] : names;
        } catch (IOException e) {
            Log.w("TvHomeSpace", "Could not list wallpapers", e);
            return new String[0];
        }
    }

    private static ImageView image(Context context) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return image;
    }

    void start() {
        if (loader != null) return;
        loader = Executors.newSingleThreadExecutor(worker -> new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            worker.run();
        }, "space-wallpapers"));
        clockTick.run();
        handler.post(photoTick);
    }

    void stop() {
        if (loader == null) return;
        loader.shutdownNow();
        loader = null;
        handler.removeCallbacksAndMessages(null);
        back.animate().cancel();
        front.setImageDrawable(null);
        back.setImageDrawable(null);
        back.setAlpha(0f);
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (loader == null) return;
            ZonedDateTime now = ZonedDateTime.now();
            clock.setText(TIME.format(now));
            date.setText(DATE.format(now));
            handler.postDelayed(this, CLOCK_INTERVAL_MS - Math.floorMod(System.currentTimeMillis(), CLOCK_INTERVAL_MS));
        }
    };

    private final Runnable photoTick = new Runnable() {
        @Override public void run() {
            ExecutorService currentLoader = loader;
            if (currentLoader == null || photos.length == 0) return;
            String path = "wallpapers/" + photos[imageIndex++ % photos.length];
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int viewWidth = getWidth() > 0 ? getWidth() : metrics.widthPixels;
            int viewHeight = getHeight() > 0 ? getHeight() : metrics.heightPixels;
            double maxScale = Math.min(1.0, Math.sqrt(MAX_PHOTO_PIXELS / ((double) viewWidth * viewHeight)));
            int width = Math.max(1, (int) (viewWidth * maxScale));
            int height = Math.max(1, (int) (viewHeight * maxScale));
            AssetManager assets = getContext().getAssets();
            currentLoader.execute(() -> {
                try {
                    Bitmap photo = decodePhoto(assets, path, width, height);
                    if (photo == null) return;
                    handler.post(() -> {
                        if (loader == currentLoader) showPhoto(photo);
                        else photo.recycle();
                    });
                } catch (IOException e) {
                    Log.w("TvHomeSpace", "Could not decode wallpaper", e);
                }
            });
            handler.postDelayed(this, PHOTO_INTERVAL_MS);
        }
    };

    private static Bitmap decodePhoto(AssetManager assets, String path, int width, int height) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = assets.open(path)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (options.outWidth / (2 * options.inSampleSize) >= width
                && options.outHeight / (2 * options.inSampleSize) >= height) {
            options.inSampleSize *= 2;
        }
        Bitmap original;
        try (InputStream input = assets.open(path)) {
            original = BitmapFactory.decodeStream(input, null, options);
        }
        if (original == null || (original.getWidth() == width && original.getHeight() == height)) return original;

        float scale = Math.max((float) width / original.getWidth(), (float) height / original.getHeight());
        float left = (width - original.getWidth() * scale) / 2f;
        float top = (height - original.getHeight() * scale) / 2f;
        Bitmap display = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(display).drawBitmap(original, null, new RectF(left, top, width - left, height - top),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        original.recycle();
        return display;
    }

    private void showPhoto(Bitmap photo) {
        if (front.getDrawable() == null || !ValueAnimator.areAnimatorsEnabled()) {
            front.setImageBitmap(photo);
            return;
        }
        ImageView previous = front;
        ImageView incoming = back;
        photoLayers.bringChildToFront(incoming);
        incoming.setImageBitmap(photo);
        incoming.setAlpha(0f);
        incoming.animate().alpha(1f).setDuration(1500).withEndAction(() -> {
            previous.setImageDrawable(null);
            front = incoming;
            back = previous;
        }).start();
    }
}
