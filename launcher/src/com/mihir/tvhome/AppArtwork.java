package com.mihir.tvhome;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

final class AppArtwork {
    final Drawable icon;
    final Drawable banner;

    private AppArtwork(Drawable icon, Drawable banner) {
        this.icon = icon;
        this.banner = banner;
    }

    static AppArtwork load(PackageManager manager, ResolveInfo info) {
        ActivityInfo activity = info.activityInfo;
        Drawable icon = null;
        Drawable banner = null;
        try {
            Resources resources = manager.getResourcesForApplication(activity.applicationInfo);
            if (info.resolvePackageName == null || info.resolvePackageName.equals(activity.packageName)) {
                icon = loadSharpest(resources, info.getIconResource());
            }
            banner = loadSharpest(resources, activity.banner);
            if (banner == null && activity.banner != activity.applicationInfo.banner) {
                banner = loadSharpest(resources, activity.applicationInfo.banner);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // The package may have been replaced while the catalog was being refreshed.
        }
        if (icon == null) icon = info.loadIcon(manager);
        return new AppArtwork(icon, banner);
    }

    private static Drawable loadSharpest(Resources resources, int id) {
        if (id == 0) return null;
        try {
            TypedValue selected = new TypedValue();
            resources.getValueForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, selected, true);
            // Decode the best installed variant at its own density to avoid baked-in enlargement.
            if (selected.density > 0 && selected.density <= DisplayMetrics.DENSITY_XXXHIGH) {
                return resources.getDrawableForDensity(id, selected.density, null);
            }
            return resources.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null);
        } catch (Resources.NotFoundException ignored) {
            return null;
        }
    }

    boolean fillsBanner(int width, int height) {
        if (banner == null || banner.getIntrinsicWidth() <= banner.getIntrinsicHeight()) return false;
        if (banner instanceof VectorDrawable) return true;
        if (!(banner instanceof BitmapDrawable)) return false;
        Bitmap pixels = ((BitmapDrawable) banner).getBitmap();
        // Standard 320×180 TV banners are still useful in a slightly larger card.
        return pixels != null && pixels.getWidth() >= width * 0.8f && pixels.getHeight() >= height * 0.8f;
    }

    static int fittedIconPixels(Drawable icon, int targetPixels, boolean crop) {
        if (icon instanceof AdaptiveIconDrawable) {
            Drawable foreground = ((AdaptiveIconDrawable) icon).getForeground();
            Drawable background = ((AdaptiveIconDrawable) icon).getBackground();
            int expandedTarget = Math.round(targetPixels * (1f + 2f * AdaptiveIconDrawable.getExtraInsetFraction()));
            int safe = Math.min(fittedIconPixels(foreground, expandedTarget, crop),
                    fittedIconPixels(background, expandedTarget, crop));
            return Math.max(1, Math.min(targetPixels, Math.round(targetPixels * (float) safe / expandedTarget)));
        }
        if (icon instanceof BitmapDrawable) {
            Bitmap pixels = ((BitmapDrawable) icon).getBitmap();
            if (pixels != null) {
                int usable = crop ? Math.min(pixels.getWidth(), pixels.getHeight())
                        : Math.max(pixels.getWidth(), pixels.getHeight());
                return Math.max(1, Math.min(targetPixels, usable));
            }
        }
        return targetPixels;
    }
}
