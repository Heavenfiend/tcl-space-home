package com.mihir.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CarouselActivity extends MainActivity {
    private Rail rail;
    private LinearLayout originalDock;
    private TextView heading;
    private List<View> pills;
    private ScrollView page;
    private Method glow;
    private int lastHeader;
    private boolean receiverRegistered;
    private final List<View> quickCards = new ArrayList<>();
    private FrameLayout homeClockArea;
    private VpnStatusIndicator vpnStatus;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (rail != null) rail.post(() -> { rail.setApps(loadApps(), rail.currentPackage()); attachRail(); });
        }
    };

    @SuppressWarnings("unchecked")
    private <T> T legacy(String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(this);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
    }

    @Override public SharedPreferences getPreferences(int mode) {
        return getSharedPreferences("com.mihir.tvhome.MainActivity", mode);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            originalDock = legacy("dockRow");
            heading = legacy("title");
            pills = legacy("pills");
            attachVpnIndicator();
            page = legacy("scroll");
            glow = MainActivity.class.getDeclaredMethod("animateGlow", int.class);
            glow.setAccessible(true);
            rail = new Rail(this);
            rail.setId(View.generateViewId());
            String selected = getSharedPreferences("carousel", MODE_PRIVATE).getString("selected", "com.google.android.youtube.tv");
            rail.setApps(loadApps(), selected);
            attachRail();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            registerReceiver(packagesChanged, filter);
            receiverRegistered = true;
        } catch (RuntimeException | ReflectiveOperationException e) {
            Log.e("TvHomeCarousel", "Could not attach carousel; original home remains available", e);
            rail = null;
        }
    }

    private void attachRail() {
        if (rail == null) return;
        originalDock.setVisibility(View.GONE);
        originalDock.removeAllViews();
        LinearLayout originalGrid = legacy("grid");
        originalGrid.setVisibility(View.VISIBLE);
        originalGrid.removeAllViews();
        ((List<View>) legacy("gridCards")).clear();
        TextView allLabel = legacy("appsLabel");
        allLabel.setText("ВСЕ ПРИЛОЖЕНИЯ");
        allLabel.setVisibility(View.VISIBLE);
        buildQuickGrid(originalGrid);
        centerHomeClock();
        ((View) legacy("gridShade")).setAlpha(0f);
        ViewGroup parent = (ViewGroup) originalDock.getParent();
        if (rail.getParent() == null) {
            parent.addView(rail, parent.indexOfChild(originalDock) + 1,
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(204)));
        }
        List<View> dock = legacy("dockCards");
        dock.clear();
        dock.add(rail);
        for (View pill : pills) pill.setNextFocusDownId(rail.getId());
        page.scrollTo(0, 0);
        updateHeading();
    }

    private void centerHomeClock() {
        ((View) legacy("greeting")).setVisibility(View.GONE);
        ((View) legacy("ampm")).setVisibility(View.GONE);
        if (homeClockArea != null) return;
        TextView clock = legacy("clock");
        TextView date = legacy("date");
        ViewGroup oldClockParent = (ViewGroup) clock.getParent();
        ViewGroup oldDateParent = (ViewGroup) date.getParent();
        oldClockParent.removeView(clock);
        oldDateParent.removeView(date);
        oldDateParent.setVisibility(View.GONE);
        homeClockArea = new FrameLayout(this);
        FrameLayout hero = legacy("hero");
        FrameLayout.LayoutParams area = new FrameLayout.LayoutParams(-1, -1);
        area.topMargin = dp(76);
        area.bottomMargin = dp(282);
        hero.addView(homeClockArea, area);
        SpaceScene.styleClock(clock, 140);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(0, 0, 0, 0);
        homeClockArea.addView(clock, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        date.setTextSize(17);
        date.setTextColor(0xffe4e6ff);
        date.setLetterSpacing(0.1f);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, 0, 0, 0);
        date.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        date.setTranslationY(dp(78));
        homeClockArea.addView(date, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    private void attachVpnIndicator() {
        if (vpnStatus != null || pills.isEmpty()) return;
        View firstPill = pills.get(0);
        LinearLayout header = (LinearLayout) firstPill.getParent();
        vpnStatus = new VpnStatusIndicator(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(125), firstPill.getLayoutParams().height);
        params.rightMargin = dp(10);
        header.addView(vpnStatus, 0, params);
    }

    @Override protected void onResume() {
        super.onResume();
        if (vpnStatus != null) vpnStatus.start();
        if (rail != null) {
            attachRail();
            rail.requestFocus();
        }
    }

    @Override protected void onPause() {
        if (vpnStatus != null) vpnStatus.stop();
        if (rail != null) {
            rail.settle();
            getSharedPreferences("carousel", MODE_PRIVATE).edit().putString("selected", rail.currentPackage()).apply();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (vpnStatus != null) vpnStatus.stop();
        if (receiverRegistered) unregisterReceiver(packagesChanged);
        if (rail != null) rail.settle();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (rail != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && pills.contains(focused)) {
                lastHeader = pills.indexOf(focused);
                rail.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (rail == null) super.onBackPressed();
        else { page.smoothScrollTo(0, 0); rail.requestFocus(); }
    }

    private void buildQuickGrid(LinearLayout grid) {
        quickCards.clear();
        int cols = 5;
        for (int row = 0; row * cols < rail.apps.size(); row++) {
            LinearLayout line = new LinearLayout(this);
            line.setClipChildren(false);
            grid.addView(line, new LinearLayout.LayoutParams(-1, dp(130)));
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= rail.apps.size()) { line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1)); continue; }
                App app = rail.apps.get(index);
                LinearLayout tile = new LinearLayout(this);
                tile.setId(View.generateViewId());
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setFocusable(true);
                tile.setFocusableInTouchMode(true);
                tile.setPadding(dp(8), dp(8), dp(8), dp(8));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xb3182135); bg.setCornerRadius(dp(13));
                tile.setBackground(bg);
                ImageView icon = new ImageView(this);
                icon.setImageDrawable(app.art.getConstantState() == null ? app.art : app.art.getConstantState().newDrawable(getResources()));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                tile.addView(icon, new LinearLayout.LayoutParams(-1, dp(76)));
                TextView label = new TextView(this);
                label.setText(app.label); label.setTextColor(Color.WHITE); label.setTextSize(14); label.setGravity(Gravity.CENTER); label.setMaxLines(1);
                tile.addView(label, new LinearLayout.LayoutParams(-1, dp(30)));
                tile.setContentDescription(app.label);
                tile.setOnClickListener(v -> launch(app));
                tile.setOnFocusChangeListener((v, focused) -> {
                    bg.setColor(focused ? 0xee344e7c : 0xb3182135);
                    bg.setStroke(dp(focused ? 2 : 0), 0xff8de5ff);
                    tile.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(180).start();
                    if (focused) page.smoothScrollTo(0, ((View) legacy("hero")).getHeight());
                });
                final int itemIndex = index;
                tile.setOnKeyListener((v, key, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_UP && itemIndex < cols) {
                        page.smoothScrollTo(0, 0); rail.requestFocus(); return true;
                    }
                    return false;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(116), 1); lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                line.addView(tile, lp); quickCards.add(tile);
            }
        }
        for (int i = 0; i < quickCards.size(); i++) {
            View v = quickCards.get(i);
            v.setNextFocusLeftId(quickCards.get(i % cols == 0 ? i : i - 1).getId());
            v.setNextFocusRightId(quickCards.get(i % cols == cols - 1 || i + 1 == quickCards.size() ? i : i + 1).getId());
            v.setNextFocusUpId(i < cols ? rail.getId() : quickCards.get(i - cols).getId());
            v.setNextFocusDownId(quickCards.get(i + cols < quickCards.size() ? i + cols : i).getId());
        }
        List<View> originalCards = legacy("gridCards"); originalCards.addAll(quickCards);
    }

    private void updateHeading() {
        if (rail == null || rail.apps.isEmpty()) return;
        App app = rail.apps.get(rail.index);
        heading.setText(app.label);
        try { glow.invoke(this, app.color); } catch (ReflectiveOperationException ignored) { }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class App {
        String pkg;
        String label;
        ComponentName component;
        Drawable art;
        boolean banner;
        int color;
    }

    private List<App> loadApps() {
        PackageManager manager = getPackageManager();
        Set<String> hidden = new HashSet<>((Set<String>) legacy("hidden"));
        hidden.addAll(Arrays.asList(getPackageName(), "com.tcl.partnercustomizer", "com.tcl.assigncode", "com.tcl.messagebox"));
        Map<String, App> unique = new LinkedHashMap<>();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo info : manager.queryIntentActivities(intent, 0)) {
            ActivityInfo activity = info.activityInfo;
            if (hidden.contains(activity.packageName) || unique.containsKey(activity.packageName)) continue;
            App app = new App();
            app.pkg = activity.packageName;
            app.label = info.loadLabel(manager).toString();
            app.component = new ComponentName(app.pkg, activity.name);
            app.art = activity.loadBanner(manager);
            if (app.art == null) app.art = activity.applicationInfo.loadBanner(manager);
            app.banner = app.art != null;
            if (app.art == null) app.art = info.loadIcon(manager);
            app.color = sample(app.art);
            unique.put(app.pkg, app);
        }
        List<App> result = new ArrayList<>();
        for (String pkg : new String[]{"com.google.android.youtube.tv", "ru.more.play", "ru.ivi.client"}) {
            App app = unique.remove(pkg);
            if (app != null) result.add(app);
        }
        List<App> rest = new ArrayList<>(unique.values());
        rest.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        result.addAll(rest);
        Log.i("TvHomeCarousel", "Loaded " + result.size() + " apps: " + result.stream().map(a -> a.pkg).reduce((a, b) -> a + "," + b).orElse(""));
        return result;
    }

    private int sample(Drawable drawable) {
        if (drawable == null) return Color.rgb(55, 70, 95);
        Bitmap bitmap = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
        Rect old = new Rect(drawable.getBounds());
        drawable.setBounds(0, 0, 24, 14);
        drawable.draw(new Canvas(bitmap));
        drawable.setBounds(old);
        int red = 0, green = 0, blue = 0, count = 0;
        for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
            int color = bitmap.getPixel(x, y);
            if (Color.alpha(color) < 128) continue;
            int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
            int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
            if (max - min < 35 || max < 55) continue;
            red += Color.red(color); green += Color.green(color); blue += Color.blue(color); count++;
        }
        bitmap.recycle();
        return count == 0 ? Color.rgb(55, 70, 95) : Color.rgb(red / count, green / count, blue / count);
    }

    private void launch(App app) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) { Toast.makeText(this, "Не удалось открыть " + app.label, Toast.LENGTH_SHORT).show(); }
    }

    private final class Rail extends FrameLayout {
        private final List<Card> cards = new ArrayList<>();
        private final TextView counter;
        private List<App> apps = new ArrayList<>();
        private int index;
        private int direction;
        private int queued;
        private ValueAnimator animator;
        private float offset;
        private float cardWidth;
        private float step;
        private float anchor;

        Rail(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            setClipChildren(true);
            setClipToPadding(true);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0x20ffffff);
            background.setCornerRadius(dp(26));
            background.setStroke(dp(1), 0x14ffffff);
            setBackground(background);
            for (int slot = -2; slot <= 4; slot++) {
                Card card = new Card(context, slot);
                cards.add(card);
                addView(card);
            }
            counter = new TextView(context);
            counter.setTextColor(0xb8ffffff);
            counter.setTextSize(11);
            counter.setLetterSpacing(0.14f);
            counter.setGravity(Gravity.CENTER);
            counter.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM);
            countParams.bottomMargin = dp(3);
            addView(counter, countParams);
        }

        String currentPackage() { return apps.isEmpty() ? "" : apps.get(index).pkg; }

        void setApps(List<App> values, String selected) {
            settle();
            apps = values;
            index = 0;
            for (int i = 0; i < apps.size(); i++) if (apps.get(i).pkg.equals(selected)) index = i;
            bindCards();
            requestLayout();
        }

        private int wrap(int value) { return apps.isEmpty() ? 0 : Math.floorMod(value, apps.size()); }

        private void bindCards() {
            for (Card card : cards) {
                card.setVisibility(apps.isEmpty() ? INVISIBLE : VISIBLE);
                if (!apps.isEmpty()) card.bind(apps.get(wrap(index + card.slot)));
            }
            counter.setText(apps.isEmpty() ? "Нет приложений" : String.format(Locale.ROOT, "‹   %d / %d   ›", index + 1, apps.size()));
            setContentDescription(apps.isEmpty() ? "Нет приложений" : "Карусель приложений: " + apps.get(index).label + ", " + (index + 1) + " из " + apps.size());
            positionCards();
            updateHeading();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = View.MeasureSpec.getSize(widthSpec);
            anchor = dp(22);
            cardWidth = Math.max(dp(100), (width - dp(72)) / 3f);
            step = cardWidth + dp(20);
            int cardHeight = Math.round(cardWidth * 9f / 16f);
            for (Card card : cards) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
                if (params.width != Math.round(cardWidth) || params.height != cardHeight || params.topMargin != dp(23)) {
                    params.width = Math.round(cardWidth);
                    params.height = cardHeight;
                    params.topMargin = dp(23);
                    card.setLayoutParams(params);
                }
                card.setPivotX(cardWidth / 2f);
                card.setPivotY(cardHeight / 2f);
            }
            super.onMeasure(widthSpec, heightSpec);
            positionCards();
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            positionCards();
        }

        private void positionCards() {
            if (cardWidth <= 0) return;
            for (Card card : cards) {
                float relative = card.slot - offset;
                float emphasis = Math.max(0f, 1f - Math.abs(relative));
                float scale = 1f + 0.075f * emphasis;
                card.setTranslationX(anchor + relative * step);
                card.setScaleX(scale);
                card.setScaleY(scale);
                card.setElevation(dp(4 + 8 * emphasis));
                card.shade.setAlpha(0.13f * (1f - emphasis));
                card.outline.setAlpha(isFocused() ? emphasis : emphasis * 0.25f);
            }
        }

        void move(int sign, boolean repeated) {
            if (apps.size() < 2) return;
            if (animator != null) { queued = Math.max(-3, Math.min(3, queued + sign)); return; }
            direction = sign;
            animator = ValueAnimator.ofFloat(0f, sign);
            animator.setDuration(repeated ? 290 : 460);
            animator.setInterpolator(new PathInterpolator(0.22f, 0.7f, 0.25f, 1f));
            animator.addUpdateListener(value -> { offset = (float) value.getAnimatedValue(); positionCards(); });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    index = wrap(index + direction);
                    animator = null;
                    direction = 0;
                    offset = 0;
                    bindCards();
                    Log.i("TvHomeCarousel", "Selected " + index + "/" + apps.size() + " " + currentPackage());
                    if (queued != 0) {
                        int next = Integer.signum(queued);
                        queued -= next;
                        move(next, true);
                    }
                }
            });
            animator.start();
        }

        void settle() {
            int target = wrap(index + direction + queued);
            if (animator != null) {
                animator.removeAllListeners();
                animator.cancel();
                animator = null;
            }
            index = target;
            direction = 0;
            queued = 0;
            offset = 0;
            if (counter != null) bindCards();
        }

        @Override public boolean onKeyDown(int code, KeyEvent event) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT) {
                move(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, event.getRepeatCount() > 0);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (event.getRepeatCount() == 0 && !apps.isEmpty()) { settle(); launch(apps.get(index)); }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP && !pills.isEmpty()) {
                settle();
                pills.get(Math.min(lastHeader, pills.size() - 1)).requestFocus();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) { if (!quickCards.isEmpty()) { settle(); quickCards.get(0).requestFocus(); } return true; }
            return super.onKeyDown(code, event);
        }

        @Override protected void onFocusChanged(boolean focus, int direction, Rect rect) {
            super.onFocusChanged(focus, direction, rect);
            positionCards();
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.HorizontalScrollView");
            info.setScrollable(apps.size() > 1);
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { move(1, false); return true; }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { move(-1, false); return true; }
            if (action == AccessibilityNodeInfo.ACTION_CLICK && !apps.isEmpty()) { settle(); launch(apps.get(index)); return true; }
            return super.performAccessibilityAction(action, args);
        }

        private final class Card extends FrameLayout {
            final int slot;
            final ImageView image;
            final TextView label;
            final View shade;
            final View outline;

            Card(Context context, int slot) {
                super(context);
                this.slot = slot;
                setFocusable(false);
                setClipToOutline(true);
                setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                GradientDrawable background = new GradientDrawable();
                background.setCornerRadius(dp(12));
                background.setColor(0xff18212e);
                setBackground(background);
                image = new ImageView(context);
                addView(image, new FrameLayout.LayoutParams(-1, -1));
                label = new TextView(context);
                label.setTextColor(Color.WHITE);
                label.setTextSize(16);
                label.setGravity(Gravity.CENTER);
                label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                label.setMaxLines(2);
                label.setPadding(dp(8), 0, dp(8), dp(8));
                addView(label, new FrameLayout.LayoutParams(-1, dp(44), Gravity.BOTTOM));
                shade = new View(context);
                shade.setBackgroundColor(Color.BLACK);
                addView(shade, new FrameLayout.LayoutParams(-1, -1));
                outline = new View(context);
                GradientDrawable border = new GradientDrawable();
                border.setCornerRadius(dp(12));
                border.setStroke(dp(2), 0xeeffffff);
                outline.setBackground(border);
                addView(outline, new FrameLayout.LayoutParams(-1, -1));
                setOnClickListener(view -> {
                    Rail.this.requestFocus();
                    if (slot == 0 && !apps.isEmpty()) { settle(); launch(apps.get(index)); }
                    else { settle(); for (int i = 0; i < Math.abs(slot); i++) move(Integer.signum(slot), false); }
                });
            }

            void bind(App app) {
                setContentDescription(app.label);
                image.setImageDrawable(app.art == null ? null : app.art.getConstantState() == null ? app.art : app.art.getConstantState().newDrawable(getResources()).mutate());
                image.setScaleType(app.banner ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.CENTER_INSIDE);
                int inset = app.banner ? 0 : dp(33);
                image.setPadding(inset, app.banner ? 0 : dp(19), inset, app.banner ? 0 : dp(43));
                label.setVisibility(app.banner ? GONE : VISIBLE);
                label.setText(app.label);
            }
        }
    }
}
