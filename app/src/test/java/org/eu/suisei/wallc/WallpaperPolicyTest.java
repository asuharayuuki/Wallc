package org.eu.suisei.wallc;

import org.junit.Test;
import static org.junit.Assert.*;

public class WallpaperPolicyTest {
    @Test public void independentDestinations() {
        assertEquals(1, WallpaperTarget.requireSingle(1));
        assertEquals(2, WallpaperTarget.requireSingle(2));
    }
    @Test public void rejectsCombinedOrUnknownFlags() {
        for (int target : new int[]{0, 3, 4, 5, -1}) {
            assertThrows(IllegalArgumentException.class, () -> WallpaperTarget.requireSingle(target));
        }
    }
    @Test public void smallImagesAreNotUpscaled() {
        assertArrayEquals(new int[]{640, 480}, ImageGeometry.boundedSize(640, 480, 8192, 16_000_000));
    }
    @Test public void hugeImagesRespectMemoryBudget() {
        int[] size = ImageGeometry.boundedSize(30_000, 20_000, 8192, 16_000_000);
        assertTrue((long) size[0] * size[1] <= 16_000_000);
        assertEquals(1.5, (double) size[0] / size[1], .001);
    }
    @Test public void panoramaRespectsDimensionLimit() {
        int[] size = ImageGeometry.boundedSize(100_000, 100, 8192, 16_000_000);
        assertEquals(8192, size[0]); assertTrue(size[1] > 0);
    }
    @Test public void extremeDimensionsDoNotOverflow() {
        int[] size = ImageGeometry.boundedSize(Integer.MAX_VALUE, Integer.MAX_VALUE, 8192, 16_000_000);
        assertArrayEquals(new int[]{4000, 4000}, size);
    }
    @Test public void cropsLandscapeToPortraitFromCenter() {
        assertArrayEquals(new int[]{1000, 0, 2000, 2000}, ImageGeometry.centerCrop(3000, 2000, 1000, 2000));
    }
    @Test public void cropsPortraitToLandscapeFromCenter() {
        assertArrayEquals(new int[]{0, 1000, 2000, 2000}, ImageGeometry.centerCrop(2000, 3000, 2000, 1000));
    }
    @Test public void matchingAspectRetainsAllPixels() {
        assertArrayEquals(new int[]{0, 0, 1440, 3120}, ImageGeometry.centerCrop(1440, 3120, 1440, 3120));
    }
    @Test public void tinyCropNeverHasZeroDimension() {
        int[] crop = ImageGeometry.centerCrop(1, 1, 8192, 1);
        assertArrayEquals(new int[]{0, 0, 1, 1}, crop);
    }
    @Test public void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ImageGeometry.boundedSize(0, 2, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> ImageGeometry.centerCrop(2, 2, -1, 2));
    }
}
