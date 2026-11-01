package org.eu.suisei.wallc;

import android.app.Application;

public final class WallcApplication extends Application {
    private WallpaperController controller;
    @Override public void onCreate() {
        super.onCreate();
        controller = new WallpaperController(this);
    }
    public WallpaperController controller() { return controller; }
}
