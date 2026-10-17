package dev.slc.appearance;

import dev.slc.role.Role;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

/** Per-world approved HTTPS PNG endpoints for human SLC roles. SCP roles intentionally have no skin URL. */
public final class RoleSkinSavedData extends SavedData {
    private static final String DATA_NAME = "slc_role_skins";
    private static final int MAX_URL_LENGTH = 512;
    private final Map<Role, String> urls = new EnumMap<>(Role.class);

    public static RoleSkinSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                RoleSkinSavedData::load, RoleSkinSavedData::new, DATA_NAME
        );
    }

    /** Empty means that role retains the player's normal Minecraft skin. */
    public String url(Role role) {
        return role != null && role.isHuman() ? urls.getOrDefault(role, "") : "";
    }

    public Map<Role, String> copyUrls() {
        Map<Role, String> copy = new EnumMap<>(Role.class);
        for (Role role : Role.values()) if (role.isHuman()) copy.put(role, url(role));
        return copy;
    }

    /** Only HTTPS endpoints are accepted, before any client is ever asked to download a skin. */
    public boolean setUrl(Role role, String candidate) {
        if (role == null || !role.isHuman() || candidate == null || candidate.length() > MAX_URL_LENGTH) return false;
        String normalized = candidate.trim();
        if (!normalized.isEmpty()) {
            try {
                URI uri = URI.create(normalized);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
                String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(java.util.Locale.ROOT);
                if (!path.endsWith(".png")) return false;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        if (normalized.isEmpty()) urls.remove(role); else urls.put(role, normalized);
        setDirty();
        return true;
    }

    public static RoleSkinSavedData load(CompoundTag tag) {
        RoleSkinSavedData data = new RoleSkinSavedData();
        for (Role role : Role.values()) {
            if (role.isHuman() && tag.contains(role.name())) data.setUrl(role, tag.getString(role.name()));
        }
        data.setDirty(false);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Role role : Role.values()) if (role.isHuman() && !url(role).isEmpty()) tag.putString(role.name(), url(role));
        return tag;
    }
}
