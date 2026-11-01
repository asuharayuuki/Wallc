package org.eu.suisei.wallc;

/** Deliberately reject combined flags: each operation changes one destination. */
public final class WallpaperTarget {
    public static final int HOME = 1; // WallpaperManager.FLAG_SYSTEM
    public static final int LOCK = 2; // WallpaperManager.FLAG_LOCK
    private WallpaperTarget() {}
    public static int requireSingle(int target) {
        if (target != HOME && target != LOCK) throw new IllegalArgumentException("Expected a single home or lock wallpaper target");
        return target;
    }
    public static String label(int target) { return requireSingle(target) == HOME ? "Home" : "Lock"; }
}
