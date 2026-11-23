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
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CarouselActivity extends MainActivity {
    private static final String INCY_PACKAGE = "llc.itdev.incy";
    private static final String GUARD_PACKAGE = "local.tcl.vpnguard";
    private static final String START_GUARD_PERMISSION = "local.tcl.vpnguard.permission.START_ENABLED_GUARD";
    private Rail rail;
    private LinearLayout originalDock;
    private TextView heading;
    private List<View> pills;
    private ScrollView page;
    private int lastHeader;
    private boolean receiverRegistered;
    private final List<View> quickCards = new ArrayList<>();
    private FrameLayout homeClockArea;
    private VpnStatusIndicator vpnStatus;
    private String resumeGridPackage;
    private boolean launchedIncy;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    && Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) return;
            if (rail != null) {
                rail.removeCallbacks(refreshCatalog);
                rail.postDelayed(refreshCatalog, 160);
            }
        }
    };

    private final Runnable refreshCatalog = () -> {
        if (rail == null) return;
        String gridPackage = focusedGridPackage();
        rail.settle();
        rail.setApps(loadApps(), rail.currentPackage());
        attachRail();
        if (gridPackage != null && !focusGridPackage(gridPackage)) rail.requestFocus();
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
            styleHome();
            attachVpnIndicator();
            page = legacy("scroll");
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
        } catch (RuntimeException e) {
            Log.e("TvHomeCarousel", "Could not attach carousel; original home remains available", e);
            rail = null;
        }
    }

    private void styleHome() {
        ((View) legacy("glow")).setVisibility(View.GONE);
        FrameLayout hero = legacy("hero");
        hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x6b050c17, 0x89071020, 0x6806101e, 0x3006101e}));
        heading.setTextColor(GlassSurface.TEXT);
        heading.setTextSize(29);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        TvIcon.Glyph[] glyphs = {TvIcon.Glyph.INPUTS, TvIcon.Glyph.WIFI, TvIcon.Glyph.SETTINGS};
        for (int i = 0; i < pills.size() && i < glyphs.length; i++) {
            TextView pill = (TextView) pills.get(i);
            TvIcon.set(pill, glyphs[i], GlassSurface.TEXT, 19);
            pill.setTextSize(13);
            pill.setTextColor(GlassSurface.TEXT);
            pill.setSingleLine(true);
            pill.setEllipsize(TextUtils.TruncateAt.END);
            pill.setPadding(dp(14), 0, dp(14), 0);
            pill.getLayoutParams().height = dp(40);
            pill.setOnFocusChangeListener(null);
            GlassSurface.styleControl(pill, GlassSurface.Finish.CONTROL, 18, 1.045f);
            switch (glyphs[i]) {
                case INPUTS:
                    pill.setText("Входы");
                    break;
                case WIFI:
                    pill.setMaxWidth(dp(169));
                    // The legacy activity refreshes the Wi-Fi text on network and clock events.
                    InputFilter stripLegacyGlyph = (source, start, end, dest, replaceStart, replaceEnd) -> {
                        String name = source.subSequence(start, end).toString();
                        if (!name.startsWith("◉  ")) return null;
                        String network = name.substring(3);
                        return "Offline".equals(network) ? "Нет сети" : network;
                    };
                    pill.setFilters(new InputFilter[]{stripLegacyGlyph});
                    pill.setText(pill.getText());
                    break;
                case SETTINGS:
                    pill.setText("Настройки");
                    break;
                default:
                    break;
            }
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
        allLabel.setText("Все приложения");
        allLabel.setTextSize(24);
        allLabel.setTextColor(GlassSurface.TEXT);
        allLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        allLabel.setLetterSpacing(0f);
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
        date.setTextColor(GlassSurface.SECONDARY_TEXT);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, firstPill.getLayoutParams().height);
        params.setMarginEnd(dp(12));
        header.addView(vpnStatus, 0, params);
    }

    @Override protected void onResume() {
        super.onResume();
        if (vpnStatus != null) vpnStatus.start();
        if (launchedIncy) {
            launchedIncy = false;
            resumeVpnProtection();
        }
        if (rail != null) {
            if (!((List<View>) legacy("dockCards")).contains(rail)) {
                // The base activity replaces the views when the system reports a catalog change.
                rail.setApps(loadApps(), rail.currentPackage());
                attachRail();
            }
            if (resumeGridPackage == null || !focusGridPackage(resumeGridPackage)) rail.requestFocus();
            resumeGridPackage = null;
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        resumeGridPackage = null;
    }

    @Override protected void onPause() {
        if (vpnStatus != null) vpnStatus.stop();
        if (rail != null) {
            resumeGridPackage = focusedGridPackage();
            rail.settle();
            SharedPreferences preferences = getSharedPreferences("carousel", MODE_PRIVATE);
            String selected = rail.currentPackage();
            if (!selected.equals(preferences.getString("selected", ""))) {
                preferences.edit().putString("selected", selected).apply();
            }
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (vpnStatus != null) vpnStatus.stop();
        if (receiverRegistered) unregisterReceiver(packagesChanged);
        if (rail != null) {
            rail.removeCallbacks(refreshCatalog);
            rail.settle();
        }
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

    private String focusedGridPackage() {
        View focused = getCurrentFocus();
        return quickCards.contains(focused) ? (String) focused.getTag() : null;
    }

    private boolean focusGridPackage(String pkg) {
        for (View tile : quickCards) if (pkg.equals(tile.getTag())) return tile.requestFocus();
        return false;
    }

    private void buildQuickGrid(LinearLayout grid) {
        quickCards.clear();
        int cols = Math.max(2, Math.min(5, getResources().getDisplayMetrics().widthPixels / dp(172)));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        for (int row = 0; row * cols < rail.apps.size(); row++) {
            LinearLayout line = new LinearLayout(this);
            line.setClipChildren(false);
            grid.addView(line, new LinearLayout.LayoutParams(-1, -2));
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
                tile.setPadding(dp(8), dp(9), dp(8), dp(7));
                tile.setTag(app.pkg);
                GlassSurface.styleControl(tile, GlassSurface.Finish.TILE, 16, 1.035f);

                Drawable artwork = copyAppIcon(app);
                int iconSize = AppArtwork.fittedIconPixels(artwork, dp(50), true);
                FrameLayout iconSlot = new FrameLayout(this);
                ImageView icon = new ImageView(this);
                icon.setImageDrawable(artwork);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                icon.setBackground(roundedColor(0xff122032, 12));
                icon.setClipToOutline(true);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                iconSlot.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
                tile.addView(iconSlot, new LinearLayout.LayoutParams(dp(52), dp(52)));

                TextView label = new TextView(this);
                label.setText(app.label);
                label.setTextColor(GlassSurface.TEXT);
                label.setTextSize(14);
                label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
                labelParams.topMargin = dp(6);
                tile.addView(label, labelParams);

                tile.setContentDescription(app.label);
                tile.setOnClickListener(v -> launch(app));
                tile.setMinimumHeight(dp(111));
                final int itemIndex = index;
                tile.setOnKeyListener((v, key, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_UP && itemIndex < cols) {
                        page.smoothScrollTo(0, 0); rail.requestFocus(); return true;
                    }
                    return false;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.setMargins(dp(6), dp(6), dp(6), dp(6));
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
        if (rail != null) heading.setText(rail.apps.isEmpty() ? "Приложения" : rail.apps.get(rail.index).label);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class App {
        String pkg;
        String label;
        ComponentName component;
        AppArtwork artwork;
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
            app.artwork = AppArtwork.load(manager, info);
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
        Log.i("TvHomeCarousel", "Loaded " + result.size() + " apps");
        return result;
    }

    private Drawable copyArtwork(Drawable artwork) {
        if (artwork == null || artwork.getConstantState() == null) return artwork;
        Drawable copy = artwork.getConstantState().newDrawable(getResources()).mutate();
        if (copy instanceof BitmapDrawable) ((BitmapDrawable) copy).setFilterBitmap(true);
        return copy;
    }

    private Drawable copyAppIcon(App app) {
        return GUARD_PACKAGE.equals(app.pkg) ? TvIcon.guardArtwork() : copyArtwork(app.artwork.icon);
    }

    private void launch(App app) {
        boolean incy = INCY_PACKAGE.equals(app.pkg);
        if (incy) resumeVpnProtection();
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            launchedIncy = incy;
        } catch (RuntimeException e) { Toast.makeText(this, "Не удалось открыть " + app.label, Toast.LENGTH_SHORT).show(); }
    }

    private void resumeVpnProtection() {
        if (checkSelfPermission(START_GUARD_PERMISSION) != PackageManager.PERMISSION_GRANTED) return;
        Intent restore = new Intent("local.tcl.vpnguard.action.START_ENABLED_GUARD")
                .setClassName(GUARD_PACKAGE, GUARD_PACKAGE + ".StartGuardReceiver")
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            // The explicit receiver requires the signature permission declared by Guard.
            sendBroadcast(restore);
        } catch (SecurityException e) {
            Log.w("TvHomeVpn", "Cannot resume optional VPN protection", e);
        }
    }

    private final class Rail extends FrameLayout {
        private static final int FIRST_SLOT = -2;
        private static final int LAST_SLOT = 4;
        private static final int MAX_QUEUED_STEPS = 4;
        private final PathInterpolator easeOut = new PathInterpolator(0.22f, 0.72f, 0.2f, 1f);
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
            setDefaultFocusHighlightEnabled(false);
            setBackground(new GlassSurface(context, GlassSurface.Finish.PANEL, 25));
            setClipToOutline(true);
            setOnClickListener(view -> {
                settle();
                if (!apps.isEmpty()) launch(apps.get(index));
            });
            for (int slot = FIRST_SLOT; slot <= LAST_SLOT; slot++) {
                Card card = new Card(context, slot);
                cards.add(card);
                addView(card);
            }
            counter = new TextView(context);
            counter.setTextColor(GlassSurface.SECONDARY_TEXT);
            counter.setTextSize(12);
            counter.setLetterSpacing(0.045f);
            counter.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            counter.setGravity(Gravity.CENTER);
            counter.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM);
            countParams.bottomMargin = dp(5);
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
                boolean visible = !apps.isEmpty() && (apps.size() > 1 || card.slot == 0);
                card.setVisibility(visible ? VISIBLE : INVISIBLE);
                if (visible) card.bind(apps.get(wrap(index + card.slot)));
            }
            setClickable(!apps.isEmpty());
            counter.setText(apps.isEmpty() ? "Нет приложений" : "‹   " + (index + 1) + " из " + apps.size() + "   ›");
            setContentDescription(apps.isEmpty() ? "Нет приложений" : "Карусель приложений: " + apps.get(index).label + ", " + (index + 1) + " из " + apps.size());
            positionCards();
            updateHeading();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = View.MeasureSpec.getSize(widthSpec);
            int height = View.MeasureSpec.getSize(heightSpec);
            int edgeInset = dp(22);
            int gap = dp(18);
            float widthFromHeight = (height - dp(56)) * 16f / 9f;
            cardWidth = Math.max(1f, Math.min((width - 2 * edgeInset - 2 * gap) / 3f, widthFromHeight));
            step = cardWidth + gap;
            anchor = (width - 3 * cardWidth - 2 * gap) / 2f;
            int cardHeight = Math.round(cardWidth * 9f / 16f);
            for (Card card : cards) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
                params.width = Math.round(cardWidth);
                params.height = cardHeight;
                params.topMargin = dp(22);
                card.setArtworkSize(params.width, cardHeight);
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
            boolean focused = isFocused();
            boolean pressed = isPressed();
            for (Card card : cards) {
                if (card.getVisibility() != VISIBLE) continue;
                float relative = card.slot - offset;
                float emphasis = Math.max(0f, 1f - Math.abs(relative));
                float visibility = Math.max(0f, Math.min(1f, Math.min(relative + 1f, 3f - relative)));
                float scale = 1f + (pressed ? -0.009f : focused ? 0.045f : 0.016f) * emphasis;
                card.setTranslationX(anchor + relative * step);
                card.setTranslationY(focused && !pressed ? -dp(2) * emphasis : 0f);
                card.setScaleX(scale);
                card.setScaleY(scale);
                card.setAlpha(visibility);
                card.setTranslationZ(dp(3) * emphasis);
                card.shade.setAlpha(0.16f * (1f - emphasis) + (pressed ? 0.13f * emphasis : 0f));
                card.outline.setAlpha(focused ? emphasis : 0f);
            }
        }

        void move(int sign, boolean repeated) {
            if (apps.size() < 2) return;
            setPressed(false);
            if (animator != null) {
                int next = queued + sign;
                if (!repeated && Math.abs(next) > MAX_QUEUED_STEPS) {
                    queued = next;
                    settle();
                } else {
                    queued = Math.max(-MAX_QUEUED_STEPS, Math.min(MAX_QUEUED_STEPS, next));
                }
                return;
            }
            if (!ValueAnimator.areAnimatorsEnabled()) {
                finishStep(sign);
                return;
            }
            direction = sign;
            animator = ValueAnimator.ofFloat(0f, sign);
            animator.setDuration(repeated ? 116 : 218);
            animator.setInterpolator(easeOut);
            animator.addUpdateListener(value -> { offset = (float) value.getAnimatedValue(); positionCards(); });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    animator = null;
                    direction = 0;
                    offset = 0;
                    finishStep(sign);
                    if (queued != 0) {
                        int next = Integer.signum(queued);
                        queued -= next;
                        move(next, true);
                    }
                }
            });
            animator.start();
        }

        private void finishStep(int sign) {
            index = wrap(index + sign);
            // Reuse the cards that stayed in the viewport and only bind the incoming edge.
            if (sign > 0) cards.add(cards.remove(0));
            else cards.add(0, cards.remove(cards.size() - 1));
            for (int i = 0; i < cards.size(); i++) cards.get(i).slot = FIRST_SLOT + i;
            bindCards();
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
            setPressed(false);
            bindCards();
        }

        @Override public boolean onKeyDown(int code, KeyEvent event) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT) {
                move(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, event.getRepeatCount() > 0);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (event.getRepeatCount() == 0 && !apps.isEmpty()) {
                    settle();
                    setPressed(true);
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP && !pills.isEmpty()) {
                settle();
                pills.get(Math.min(lastHeader, pills.size() - 1)).requestFocus();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!quickCards.isEmpty()) {
                    settle();
                    quickCards.get(0).requestFocus();
                    page.smoothScrollTo(0, ((View) legacy("hero")).getHeight());
                }
                return true;
            }
            return super.onKeyDown(code, event);
        }

        @Override public boolean onKeyUp(int code, KeyEvent event) {
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                boolean activate = isPressed() && !event.isCanceled();
                setPressed(false);
                if (activate) performClick();
                return true;
            }
            return super.onKeyUp(code, event);
        }

        @Override protected void dispatchSetPressed(boolean pressed) {
            super.dispatchSetPressed(pressed);
            positionCards();
        }

        @Override protected void onFocusChanged(boolean focus, int direction, Rect rect) {
            super.onFocusChanged(focus, direction, rect);
            if (!focus) setPressed(false);
            positionCards();
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.HorizontalScrollView");
            info.setScrollable(apps.size() > 1);
            if (apps.size() > 1) {
                info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            }
        }

        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { move(1, false); return true; }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { move(-1, false); return true; }
            if (action == AccessibilityNodeInfo.ACTION_CLICK && !apps.isEmpty()) return performClick();
            return super.performAccessibilityAction(action, args);
        }

        private final class Card extends FrameLayout {
            int slot;
            final ImageView image;
            final TextView label;
            final View shade;
            final View outline;
            private App bound;
            private int artworkWidth;
            private int artworkHeight;

            Card(Context context, int slot) {
                super(context);
                this.slot = slot;
                setFocusable(false);
                setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                setClipToOutline(true);
                setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                setBackground(new GlassSurface(context, GlassSurface.Finish.TILE, 21));
                image = new ImageView(context);
                addView(image, new FrameLayout.LayoutParams(-1, -1));
                label = new TextView(context);
                label.setTextColor(GlassSurface.TEXT);
                label.setTextSize(17);
                label.setGravity(Gravity.CENTER);
                label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                addView(label, new FrameLayout.LayoutParams(-1, dp(42), Gravity.BOTTOM));
                shade = new View(context);
                shade.setBackgroundColor(Color.BLACK);
                addView(shade, new FrameLayout.LayoutParams(-1, -1));
                outline = new View(context);
                GradientDrawable darkRim = roundedColor(Color.TRANSPARENT, 21);
                darkRim.setStroke(dp(4), 0xff0e1b2d);
                GradientDrawable highlight = roundedColor(Color.TRANSPARENT, 21);
                highlight.setStroke(dp(2), GlassSurface.FOCUS_EDGE);
                outline.setBackground(new LayerDrawable(new Drawable[]{darkRim, highlight}));
                addView(outline, new FrameLayout.LayoutParams(-1, -1));
                setOnClickListener(view -> {
                    Rail.this.requestFocus();
                    if (apps.isEmpty() || getAlpha() == 0f) return;
                    int selectedSlot = slot;
                    int target = wrap(index + selectedSlot);
                    if (selectedSlot == 0 && animator == null) Rail.this.performClick();
                    else if (Math.abs(selectedSlot) == 1 && animator == null) move(selectedSlot, false);
                    else {
                        settle();
                        index = target;
                        bindCards();
                    }
                });
            }

            void bind(App app) {
                if (bound == app) return;
                bound = app;
                updateArtwork();
            }

            void setArtworkSize(int width, int height) {
                if (artworkWidth == width && artworkHeight == height) return;
                artworkWidth = width;
                artworkHeight = height;
                updateArtwork();
            }

            private void updateArtwork() {
                if (bound == null || artworkWidth <= 0 || artworkHeight <= 0) return;
                FrameLayout.LayoutParams imageParams = (FrameLayout.LayoutParams) image.getLayoutParams();
                imageParams.setMarginStart(0);
                image.setPadding(0, 0, 0, 0);
                if (bound.artwork.fillsBanner(artworkWidth, artworkHeight)) {
                    imageParams.width = -1;
                    imageParams.height = -1;
                    imageParams.gravity = Gravity.CENTER;
                    imageParams.topMargin = 0;
                    image.setClipToOutline(false);
                    image.setBackground(null);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setImageDrawable(copyArtwork(bound.artwork.banner));
                    label.setVisibility(GONE);
                } else {
                    Drawable artwork = copyAppIcon(bound);
                    int caption = Math.min(dp(42), artworkHeight / 3);
                    int available = artworkHeight - caption;
                    int target = Math.max(1, Math.min(dp(93), Math.min(available - dp(11), artworkWidth - dp(24))));
                    int size = AppArtwork.fittedIconPixels(artwork, target, false);
                    imageParams.width = size;
                    imageParams.height = size;
                    imageParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    imageParams.topMargin = (available - size) / 2 + dp(3);
                    image.setBackground(roundedColor(Color.TRANSPARENT, 14));
                    image.setClipToOutline(true);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setImageDrawable(artwork);
                    FrameLayout.LayoutParams textParams = (FrameLayout.LayoutParams) label.getLayoutParams();
                    textParams.height = caption;
                    textParams.setMarginStart(dp(10));
                    textParams.setMarginEnd(dp(10));
                    label.setLayoutParams(textParams);
                    label.setVisibility(VISIBLE);
                    label.setText(bound.label);
                }
                image.setLayoutParams(imageParams);
            }
        }
    }

    private GradientDrawable roundedColor(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }
}
