package dev.slc.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.slc.SecretLabCubed;
import dev.slc.role.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only asynchronous cache for the OP-approved world role skin endpoints. */
public final class ClientRoleSkinCache {
    private static final int MAX_PNG_BYTES = 2 * 1024 * 1024;
    private static final Map<Role, String> URLS = new ConcurrentHashMap<>();
    private static final Map<Role, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final Set<Role> LOADING = ConcurrentHashMap.newKeySet();

    private ClientRoleSkinCache() {}

    public static void replace(Map<Role, String> replacement) {
        for (Role role : Role.values()) {
            if (!role.isHuman()) continue;
            String fresh = replacement.getOrDefault(role, "");
            if (!fresh.equals(URLS.getOrDefault(role, ""))) {
                URLS.put(role, fresh);
                TEXTURES.remove(role);
                LOADING.remove(role);
            }
        }
    }

    public static String url(Role role) {
        return role != null && role.isHuman() ? URLS.getOrDefault(role, "") : "";
    }

    /** Returns null until the approved PNG has downloaded and passed its strict vanilla 64×64 check. */
    public static ResourceLocation texture(Role role) {
        if (role == null || !role.isHuman()) return null;
        ResourceLocation cached = TEXTURES.get(role);
        if (cached != null) return cached;
        String endpoint = url(role);
        if (!endpoint.isEmpty() && LOADING.add(role)) download(role, endpoint);
        return null;
    }

    private static void download(Role role, String endpoint) {
        CompletableFuture.supplyAsync(() -> readSkin(endpoint)).thenAccept(image -> {
            if (image == null) {
                LOADING.remove(role);
                return;
            }
            Minecraft.getInstance().execute(() -> {
                try {
                    // Ignore a late download after an administrator changed this role's URL.
                    if (!endpoint.equals(url(role))) {
                        image.close();
                        return;
                    }
                    ResourceLocation id = new ResourceLocation(SecretLabCubed.MOD_ID, "role_skin/" + role.name().toLowerCase(java.util.Locale.ROOT));
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                    TEXTURES.put(role, id);
                } finally {
                    LOADING.remove(role);
                }
            });
        });
    }

    private static NativeImage readSkin(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return null;
            URL url = uri.toURL();
            if (!(url.openConnection() instanceof HttpsURLConnection connection)) return null;
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(7_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "SLC-role-skin/1");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = in.read(buffer)) >= 0) {
                    total += count;
                    if (total > MAX_PNG_BYTES) return null;
                    bytes.write(buffer, 0, count);
                }
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes.toByteArray()));
                // URL skins must be standard modern Minecraft PNG skins. A malformed image is discarded silently.
                if (image.getWidth() != 64 || image.getHeight() != 64) {
                    image.close();
                    return null;
                }
                return image;
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
