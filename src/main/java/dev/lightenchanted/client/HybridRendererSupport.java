package dev.lightenchanted.client;

import dev.lightenchanted.LightEnchanted;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Optional shader-loader probe kept deliberately free of Oculus/Iris linkage.
 *
 * <p>Oculus owns the deferred shader pipeline while a shader pack is active.
 * Directly linking a Forge mod to its API would make a normal Light Enchanted
 * install fail to load. Reflection lets the renderer preserve its Java fallback
 * on every other client, including dedicated servers.</p>
 */
public final class HybridRendererSupport {
    private static final String[] IRIS_API_NAMES = {
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.api.v0.IrisApi"
    };

    public enum ShaderBackend {
        OCULUS,
        IRIS,
        NONE
    }

    private HybridRendererSupport() {
    }

    public static ShaderBackend backend() {
        if (ModList.get().isLoaded("oculus")) {
            return ShaderBackend.OCULUS;
        }
        if (ModList.get().isLoaded("iris")) {
            return ShaderBackend.IRIS;
        }
        return ShaderBackend.NONE;
    }

    /**
     * Best-effort active-pack state. An unavailable or changed optional API is
     * intentionally treated as inactive; it must never prevent fallback beams.
     */
    public static boolean isShaderPackActive() {
        if (backend() == ShaderBackend.NONE) {
            return false;
        }
        for (String className : IRIS_API_NAMES) {
            try {
                Class<?> apiClass = Class.forName(className, false, HybridRendererSupport.class.getClassLoader());
                Method getInstance = apiClass.getMethod("getInstance");
                Object api = getInstance.invoke(null);
                Method inUse = apiClass.getMethod("isShaderPackInUse");
                Object result = inUse.invoke(api);
                if (result instanceof Boolean enabled) {
                    return enabled;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Different Oculus generations expose either Iris API namespace or no public API.
            }
        }
        return false;
    }

    public static void logCompatibilityStatus() {
        ShaderBackend backend = backend();
        if (backend == ShaderBackend.NONE) {
            LightEnchanted.LOGGER.info("Light Enchanted: no Oculus/Iris shader loader found; using the built-in fallback beam renderer.");
            return;
        }
        if (isShaderPackActive()) {
            LightEnchanted.LOGGER.info("Light Enchanted: {} shader pipeline is active. Select the Light Enchanted Hybrid Renderer pack for the supported high-end path; fallback beams stay available.", backend);
        } else {
            LightEnchanted.LOGGER.info("Light Enchanted: {} detected without an active shader pack. The built-in fallback remains active; install lightenchanted-shaders-*.zip to enable the hybrid path.", backend);
        }
    }
}
