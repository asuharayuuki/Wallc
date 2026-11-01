package org.eu.suisei.wallc;

import org.junit.Test;
import static org.junit.Assert.*;

/** Model the framework's inherited attribution so this exact regression remains covered. */
public class AppContextFactoryTest {
    private static class ThreadState {}
    private static class PackageInfo {
        final String name;
        PackageInfo(String name) { this.name = name; }
    }
    private static class FrameworkContext {
        private final PackageInfo mPackageInfo;
        final String opPackage;
        FrameworkContext(PackageInfo info, FrameworkContext parent) {
            mPackageInfo = info;
            opPackage = parent == null ? info.name : parent.opPackage;
        }
        private static FrameworkContext createAppContext(ThreadState thread, PackageInfo info) {
            return new FrameworkContext(info, null);
        }
        private static FrameworkContext createAppContext(ThreadState thread, PackageInfo info, String override) {
            throw new AssertionError("do not select another factory overload");
        }
    }
    @Test public void derivedContextRetainsAndroidWhileIndependentContextUsesShell() throws Exception {
        FrameworkContext system = new FrameworkContext(new PackageInfo("android"), null);
        FrameworkContext derived = new FrameworkContext(new PackageInfo("com.android.shell"), system);
        assertEquals("android", derived.opPackage);
        FrameworkContext independent = (FrameworkContext) AppContextFactory.create(derived, new ThreadState(), FrameworkContext.class);
        assertEquals("com.android.shell", independent.opPackage);
        assertEquals("android", derived.opPackage);
    }
    @Test public void removesNullSystemAttributionInsteadOfOnlyChangingPackageName() throws Exception {
        FrameworkContext system = new FrameworkContext(new PackageInfo(null), null);
        FrameworkContext derived = new FrameworkContext(new PackageInfo("com.android.shell"), system);
        assertNull(derived.opPackage);
        FrameworkContext independent = (FrameworkContext) AppContextFactory.create(derived, new ThreadState(), FrameworkContext.class);
        assertEquals("com.android.shell", independent.opPackage);
    }
    @Test public void packageInfoMayBeDeclaredOnFrameworkSuperclass() throws Exception {
        FrameworkContext derived = new FrameworkContext(new PackageInfo("com.android.shell"), null) {};
        FrameworkContext independent = (FrameworkContext) AppContextFactory.create(derived, new ThreadState(), FrameworkContext.class);
        assertEquals("com.android.shell", independent.opPackage);
    }
    @Test public void missingPackageInfoStopsBeforeFrameworkCall() {
        assertThrows(NoSuchFieldException.class, () -> AppContextFactory.create(new Object(), new ThreadState(), FrameworkContext.class));
    }
    @Test public void missingCompatibleFactoryFailsExplicitly() {
        FrameworkContext derived = new FrameworkContext(new PackageInfo("com.android.shell"), null);
        assertThrows(NoSuchMethodException.class, () -> AppContextFactory.create(derived, new ThreadState(), Object.class));
    }
}
