package org.eu.suisei.wallc;

/** Image bytes travel separately through stdin. */
public final class RootProtocol {
    public static final String RESULT = "WALLC_RESULT=";
    public static final String ROOT = "WALLC_ROOT_UID=0";
    private RootProtocol() {}
    public static String quote(String value) {
        if (value == null || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid command argument");
        return "'" + value.replace("'", "'\\''") + "'";
    }
    public static String command(String launcher, String apk, int userId, String operation, int target) {
        if (!launcher.startsWith("/") || !apk.startsWith("/") || userId < 0 || userId > 21474)
            throw new IllegalArgumentException("Invalid root launcher arguments");
        if ("set".equals(operation)) WallpaperTarget.requireSingle(target);
        else if (!"diagnose".equals(operation) || target != 0) throw new IllegalArgumentException("Invalid wallpaper operation");
        return "exec " + quote(launcher) + " " + quote(apk) + " " + userId + " " + operation + " " + target;
    }
    public static String resultJson(String output) {
        String found = null;
        for (String line : output.split("\\r?\\n")) if (line.startsWith(RESULT)) found = line.substring(RESULT.length());
        return found;
    }
    public static boolean rootGranted(String output) {
        for (String line : output.split("\\r?\\n")) if (ROOT.equals(line)) return true;
        return false;
    }
}
