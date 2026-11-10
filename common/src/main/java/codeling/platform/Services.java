package codeling.platform;

import java.util.ServiceLoader;

/** Finds the loader's implementation of a platform interface (named in META-INF/services in the loader module). */
public final class Services {
    private Services() {
    }

    public static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + type.getName() + " for this mod loader"));
    }
}
