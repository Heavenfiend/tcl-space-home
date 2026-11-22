package com.mihir.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.StateSet;
import android.view.View;

/** A dark, reflective surface with a readable fill even over bright wallpapers. */
final class GlassSurface extends Drawable {
    enum Finish {
        PANEL(0xb92a3c55, 0xe008101f, 0x9fc4dcf1, 0x6d61819c),
        CONTROL(0xeb283951, 0xf0131d2d, 0xaec7def3, 0x74587b98),
        TILE(0xef24364d, 0xf2101b2c, 0x9ba9c9e5, 0x664a6b8a);

        final int top;
        final int bottom;
        final int highlight;
        final int rim;

        Finish(int top, int bottom, int highlight, int rim) {
            this.top = top;
            this.bottom = bottom;
            this.highlight = highlight;
            this.rim = rim;
        }
    }

    static final int TEXT = 0xfff1f6ff;
    static final int SECONDARY_TEXT = 0xffcedded;
    static final int FOCUS_EDGE = 0xff9eeaff;
    private static final int[] FOCUSED = {android.R.attr.state_focused};
    private static final int[] PRESSED = {android.R.attr.state_pressed};
    private static final float[] STOPS = {0f, 0.48f, 1f};

    private final Finish finish;
    private final float density;
    private final float radius;
    private final RectF rect = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float stroke;
    private int alpha = 255;
    private boolean focused;
    private boolean pressed;

    GlassSurface(Context context, Finish finish, float radius) {
        this.finish = finish;
        this.density = context.getResources().getDisplayMetrics().density;
        this.radius = radius * density;
        edge.setStyle(Paint.Style.STROKE);
    }

    static void styleControl(View view, Finish finish, float radius, float focusScale) {
        view.setBackground(new GlassSurface(view.getContext(), finish, radius));
        view.setDefaultFocusHighlightEnabled(false);
        StateListAnimator motion = new StateListAnimator();
        motion.addState(PRESSED, scale(view, 0.98f, 80));
        motion.addState(FOCUSED, scale(view, focusScale, 120));
        motion.addState(StateSet.WILD_CARD, scale(view, 1f, 120));
        view.setStateListAnimator(motion);
    }

    private static ObjectAnimator scale(View view, float scale, long duration) {
        return ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, scale),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scale)).setDuration(duration);
    }

    @Override public boolean isStateful() {
        return finish != Finish.PANEL;
    }

    @Override protected boolean onStateChange(int[] state) {
        boolean hasFocus = StateSet.stateSetMatches(FOCUSED, state);
        boolean isPressed = StateSet.stateSetMatches(PRESSED, state);
        if (hasFocus == focused && isPressed == pressed) return false;
        focused = hasFocus;
        pressed = isPressed;
        updatePaints(getBounds());
        invalidateSelf();
        return true;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        updatePaints(bounds);
    }

    private void updatePaints(Rect bounds) {
        if (bounds.isEmpty()) return;
        int top = pressed ? 0xff253d59 : focused ? 0xff315779 : finish.top;
        int bottom = pressed ? 0xff0b1a2d : focused ? 0xff152f4d : finish.bottom;
        int rimTop = pressed ? 0xffcef7ff : focused ? 0xffe1fbff : finish.highlight;
        int rimMiddle = pressed || focused ? FOCUS_EDGE : 0x486b86a3;
        int rimBottom = pressed || focused ? 0xff67bddf : finish.rim;
        stroke = density * (pressed ? 1.75f : focused ? 2.25f : 1f);
        fill.setShader(new LinearGradient(bounds.left, bounds.top,
                bounds.left + bounds.width() * 0.55f, bounds.bottom, top, bottom,
                Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{rimTop, rimMiddle, rimBottom}, STOPS, Shader.TileMode.CLAMP));
        edge.setStrokeWidth(stroke);
    }

    @Override public void draw(Canvas canvas) {
        rect.set(getBounds());
        canvas.drawRoundRect(rect, radius, radius, fill);
        rect.inset(stroke * 0.5f, stroke * 0.5f);
        if (!rect.isEmpty()) {
            float edgeRadius = Math.max(0f, radius - stroke * 0.5f);
            canvas.drawRoundRect(rect, edgeRadius, edgeRadius, edge);
        }
    }

    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
    }

    @Override public void setAlpha(int alpha) {
        this.alpha = alpha;
        fill.setAlpha(alpha);
        edge.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public int getAlpha() {
        return alpha;
    }

    @Override public void setColorFilter(ColorFilter filter) {
        fill.setColorFilter(filter);
        edge.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
