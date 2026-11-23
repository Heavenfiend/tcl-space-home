package com.mihir.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

final class TvIcon extends Drawable {
    enum Glyph { INPUTS, WIFI, SETTINGS, VPN_ON, VPN_OFF, VPN_UNKNOWN, GUARD }

    private final Glyph glyph;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private TvIcon(Glyph glyph, int color) {
        this.glyph = glyph;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    static void set(TextView view, Glyph glyph, int color, int sizeDp) {
        float density = view.getResources().getDisplayMetrics().density;
        int size = Math.round(sizeDp * density);
        TvIcon icon = new TvIcon(glyph, color);
        icon.setBounds(0, 0, size, size);
        view.setCompoundDrawablePadding(Math.round(7 * density));
        view.setCompoundDrawablesRelative(icon, null, null, null);
    }

    static Drawable guardArtwork() {
        return new TvIcon(Glyph.GUARD, 0xffa6e9fc);
    }

    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
        switch (glyph) {
            case INPUTS:
                canvas.drawRoundRect(8, 5, 21, 19, 3, 3, paint);
                canvas.drawLine(3, 12, 15, 12, paint);
                path.reset();
                path.moveTo(12, 9);
                path.lineTo(15, 12);
                path.lineTo(12, 15);
                canvas.drawPath(path, paint);
                break;
            case WIFI:
                canvas.drawArc(-1, 5, 25, 29, 224, 92, false, paint);
                canvas.drawArc(3.5f, 9.5f, 20.5f, 25.5f, 224, 92, false, paint);
                canvas.drawArc(8, 14, 16, 21, 225, 90, false, paint);
                canvas.drawPoint(12, 19.5f, paint);
                break;
            case SETTINGS:
                slider(canvas, 6, 9);
                slider(canvas, 12, 15);
                slider(canvas, 18, 10);
                break;
            default:
                drawShield(canvas);
                break;
        }
        canvas.restoreToCount(saved);
    }

    private void slider(Canvas canvas, float y, float x) {
        canvas.drawLine(4, y, x - 2, y, paint);
        canvas.drawLine(x + 2, y, 20, y, paint);
        canvas.drawCircle(x, y, 2, paint);
    }

    private void drawShield(Canvas canvas) {
        path.reset();
        path.moveTo(12, 3);
        path.lineTo(19, 6);
        path.lineTo(19, 11);
        path.quadTo(19, 17, 12, 21);
        path.quadTo(5, 17, 5, 11);
        path.lineTo(5, 6);
        path.close();
        canvas.drawPath(path, paint);
        if (glyph == Glyph.GUARD) {
            canvas.drawArc(9.7f, 7f, 14.3f, 12.9f, 180, 180, false, paint);
            canvas.drawRoundRect(8.5f, 10, 15.5f, 14.7f, 1, 1, paint);
        } else if (glyph == Glyph.VPN_ON) {
            path.reset();
            path.moveTo(9, 12);
            path.lineTo(11, 14);
            path.lineTo(15, 10);
            canvas.drawPath(path, paint);
        } else if (glyph == Glyph.VPN_OFF) {
            canvas.drawLine(9, 12, 15, 12, paint);
        } else {
            canvas.drawLine(12, 8, 12, 12, paint);
            canvas.drawPoint(12, 15, paint);
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
