package org.eu.suisei.wallc;

public final class ImageGeometry {
    private ImageGeometry() {}

    /** Bound both memory (16 MP) and texture dimensions without upscaling. */
    public static int[] boundedSize(int width, int height, int maxEdge, long maxPixels) {
        if (width <= 0 || height <= 0 || maxEdge <= 0 || maxPixels <= 0)
            throw new IllegalArgumentException("Invalid image dimensions");
        double scale = Math.min(1d, Math.min((double) maxEdge / Math.max(width, height),
                Math.sqrt((double) maxPixels / ((double) width * height))));
        return new int[]{Math.max(1, (int) Math.floor(width * scale)),
                Math.max(1, (int) Math.floor(height * scale))};
    }

    /** left, top, right, bottom in source pixels; centered and inside the source. */
    public static int[] centerCrop(int width, int height, int screenWidth, int screenHeight) {
        if (width <= 0 || height <= 0 || screenWidth <= 0 || screenHeight <= 0)
            throw new IllegalArgumentException("Invalid crop dimensions");
        double aspect = (double) screenWidth / screenHeight;
        int cropWidth = Math.min(width, Math.max(1, (int) Math.round(height * aspect)));
        int cropHeight = Math.min(height, Math.max(1, (int) Math.round(width / aspect)));
        int left = (width - cropWidth) / 2, top = (height - cropHeight) / 2;
        return new int[]{left, top, left + cropWidth, top + cropHeight};
    }
}
