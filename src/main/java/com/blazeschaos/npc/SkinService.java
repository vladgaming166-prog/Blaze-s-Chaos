package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads and caches player skins (name, URL, texture/signature).
 */
public final class SkinService {

    private final BlazesChaosPlugin plugin;
    private final Map<String, SkinData> cache = new ConcurrentHashMap<>();
    private final File cacheFolder;

    public SkinService(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.cacheFolder = new File(plugin.getDataFolder(), "skins");
        if (!cacheFolder.exists() && !cacheFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create skins cache folder.");
        }
        loadDiskCache();
    }

    public void applyToSkull(@NotNull ItemStack skull, @NotNull SkinData skin) {
        if (skull.getType() != Material.PLAYER_HEAD) {
            return;
        }
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) {
            return;
        }
        PlayerProfile profile = createProfile(skin);
        if (profile != null) {
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
        }
    }

    public @NotNull ItemStack createSkull(@NotNull SkinData skin) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        applyToSkull(skull, skin);
        return skull;
    }

    public @Nullable PlayerProfile createProfile(@NotNull SkinData skin) {
        UUID id = skin.profileId() != null ? skin.profileId() : UUID.nameUUIDFromBytes(
                ("BlazeNpc:" + (skin.value() == null ? "default" : skin.value())).getBytes(StandardCharsets.UTF_8));
        String name = skin.profileName() != null ? skin.profileName() : "NPC";
        PlayerProfile profile = Bukkit.createProfile(id, name);
        if (skin.hasTextures()) {
            if (skin.signature() != null && !skin.signature().isBlank()) {
                profile.setProperty(new ProfileProperty("textures", skin.texture(), skin.signature()));
            } else {
                profile.setProperty(new ProfileProperty("textures", skin.texture()));
            }
        }
        return profile;
    }

    public void resolveAsync(@NotNull SkinData skin, @NotNull Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                resolveSync(skin);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to resolve NPC skin", ex);
            }
            Bukkit.getScheduler().runTask(plugin, onComplete);
        });
    }

    public void resolveSync(@NotNull SkinData skin) throws Exception {
        if (skin.type() == SkinData.Type.NONE) {
            return;
        }
        if (skin.hasTextures() && skin.type() != SkinData.Type.PLAYER) {
            return;
        }
        String cacheKey = cacheKey(skin);
        SkinData cached = cache.get(cacheKey);
        if (cached != null && cached.hasTextures()) {
            skin.setTexture(cached.texture());
            skin.setSignature(cached.signature());
            skin.setProfileId(cached.profileId());
            skin.setProfileName(cached.profileName());
            return;
        }

        if (skin.type() == SkinData.Type.PLAYER && skin.value() != null) {
            fetchPlayerSkin(skin, skin.value());
        } else if (skin.type() == SkinData.Type.URL && skin.value() != null) {
            fetchUrlSkin(skin, skin.value());
        }

        if (skin.hasTextures()) {
            cache.put(cacheKey, copy(skin));
            saveToDisk(cacheKey, skin);
        }
    }

    private void fetchPlayerSkin(@NotNull SkinData skin, @NotNull String playerName) throws Exception {
        String uuidJson = httpGet("https://api.mojang.com/users/profiles/minecraft/" + playerName);
        if (uuidJson == null || !uuidJson.contains("\"id\"")) {
            return;
        }
        String id = extractJson(uuidJson, "id");
        String name = extractJson(uuidJson, "name");
        if (id == null) {
            return;
        }
        UUID uuid = fromUndashed(id);
        skin.setProfileId(uuid);
        skin.setProfileName(name == null ? playerName : name);

        String profileJson = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/"
                + id + "?unsigned=false");
        if (profileJson == null) {
            return;
        }
        String texture = extractNestedValue(profileJson, "value");
        String signature = extractNestedValue(profileJson, "signature");
        if (texture != null) {
            skin.setTexture(texture);
            skin.setSignature(signature);
            skin.setType(SkinData.Type.TEXTURE);
        }
    }

    private void fetchUrlSkin(@NotNull SkinData skin, @NotNull String url) {
        // Encode a minimal textures JSON for custom skin URLs (unsigned)
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url.replace("\"", "") + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        skin.setTexture(encoded);
        skin.setSignature(null);
        skin.setProfileName("Custom");
        skin.setProfileId(UUID.nameUUIDFromBytes(("url:" + url).getBytes(StandardCharsets.UTF_8)));
        skin.setType(SkinData.Type.URL);
    }

    private @Nullable String httpGet(@NotNull String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "BlazesChaos/1.0");
        int code = connection.getResponseCode();
        if (code != 200) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private @Nullable String extractJson(@NotNull String json, @NotNull String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\": \"";
            idx = json.indexOf(search);
        }
        if (idx < 0) {
            return null;
        }
        int start = idx + search.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private @Nullable String extractNestedValue(@NotNull String json, @NotNull String key) {
        return extractJson(json, key);
    }

    private @NotNull UUID fromUndashed(@NotNull String id) {
        String dashed = id.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }

    private @NotNull String cacheKey(@NotNull SkinData skin) {
        return skin.type().name().toLowerCase() + ":" + (skin.value() == null ? "none" : skin.value().toLowerCase());
    }

    private @NotNull SkinData copy(@NotNull SkinData src) {
        SkinData copy = new SkinData();
        copy.setType(src.type());
        copy.setValue(src.value());
        copy.setTexture(src.texture());
        copy.setSignature(src.signature());
        copy.setProfileId(src.profileId());
        copy.setProfileName(src.profileName());
        return copy;
    }

    private void saveToDisk(@NotNull String key, @NotNull SkinData skin) {
        try {
            File file = new File(cacheFolder, key.replace(':', '_').replace('/', '_') + ".skin");
            String content = (skin.texture() == null ? "" : skin.texture()) + "\n"
                    + (skin.signature() == null ? "" : skin.signature()) + "\n"
                    + (skin.profileId() == null ? "" : skin.profileId()) + "\n"
                    + (skin.profileName() == null ? "" : skin.profileName());
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            if (plugin.configs().debug()) {
                plugin.getLogger().log(Level.WARNING, "Failed to cache skin to disk", ex);
            }
        }
    }

    private void loadDiskCache() {
        File[] files = cacheFolder.listFiles((dir, name) -> name.endsWith(".skin"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String[] parts = raw.split("\n", -1);
                if (parts.length < 1 || parts[0].isBlank()) {
                    continue;
                }
                SkinData data = new SkinData();
                data.setType(SkinData.Type.TEXTURE);
                data.setTexture(parts[0]);
                if (parts.length > 1 && !parts[1].isBlank()) {
                    data.setSignature(parts[1]);
                }
                if (parts.length > 2 && !parts[2].isBlank()) {
                    data.setProfileId(UUID.fromString(parts[2]));
                }
                if (parts.length > 3 && !parts[3].isBlank()) {
                    data.setProfileName(parts[3]);
                }
                String key = file.getName().replace('_', ':').replace(".skin", "");
                // stored key used underscore; rebuild approximate
                cache.put(key, data);
            } catch (Exception ignored) {
            }
        }
    }
}
