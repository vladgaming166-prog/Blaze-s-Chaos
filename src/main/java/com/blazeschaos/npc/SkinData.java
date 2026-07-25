package com.blazeschaos.npc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class SkinData {

    public enum Type {
        PLAYER, URL, TEXTURE, NONE
    }

    private Type type = Type.NONE;
    private @Nullable String value;
    private @Nullable String texture;
    private @Nullable String signature;
    private @Nullable UUID profileId;
    private @Nullable String profileName;

    public @NotNull Type type() {
        return type;
    }

    public void setType(@NotNull Type type) {
        this.type = type;
    }

    public @Nullable String value() {
        return value;
    }

    public void setValue(@Nullable String value) {
        this.value = value;
    }

    public @Nullable String texture() {
        return texture;
    }

    public void setTexture(@Nullable String texture) {
        this.texture = texture;
    }

    public @Nullable String signature() {
        return signature;
    }

    public void setSignature(@Nullable String signature) {
        this.signature = signature;
    }

    public @Nullable UUID profileId() {
        return profileId;
    }

    public void setProfileId(@Nullable UUID profileId) {
        this.profileId = profileId;
    }

    public @Nullable String profileName() {
        return profileName;
    }

    public void setProfileName(@Nullable String profileName) {
        this.profileName = profileName;
    }

    public boolean hasTextures() {
        return texture != null && !texture.isBlank();
    }
}
