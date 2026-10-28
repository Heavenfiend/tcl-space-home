package com.mihir.tvhome;

import android.service.dreams.DreamService;

public final class SpaceDreamService extends DreamService {
    private SpaceScene scene;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        scene = new SpaceScene(this);
        setContentView(scene);
    }
    @Override public void onDreamingStarted() { super.onDreamingStarted(); if (scene != null) scene.start(); }
    @Override public void onDreamingStopped() { if (scene != null) scene.stop(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { if (scene != null) scene.stop(); super.onDetachedFromWindow(); }
}
