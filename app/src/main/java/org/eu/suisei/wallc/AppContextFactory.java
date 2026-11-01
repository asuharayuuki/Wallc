package org.eu.suisei.wallc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Create a Context without the parent system Context's inherited AppOps attribution. */
public final class AppContextFactory {
    private AppContextFactory() {}

    public static Object create(Object packageContext, Object activityThread, Class<?> contextImpl)
            throws ReflectiveOperationException {
        Field packageInfo = findField(packageContext.getClass(), "mPackageInfo");
        packageInfo.setAccessible(true);
        Object loadedApk = packageInfo.get(packageContext);
        if (loadedApk == null) throw new IllegalStateException("Wallpaper package LoadedApk is null");
        for (Method factory : contextImpl.getDeclaredMethods()) {
            Class<?>[] parameters = factory.getParameterTypes();
            if (factory.getName().equals("createAppContext") && Modifier.isStatic(factory.getModifiers())
                    && parameters.length == 2 && parameters[0].isInstance(activityThread)
                    && parameters[1].isInstance(loadedApk)) {
                factory.setAccessible(true);
                Object context = factory.invoke(null, activityThread, loadedApk);
                if (context == null) throw new IllegalStateException("Failed to create an independent wallpaper context");
                return context;
            }
        }
        throw new NoSuchMethodException("ContextImpl.createAppContext(ActivityThread, LoadedApk)");
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
