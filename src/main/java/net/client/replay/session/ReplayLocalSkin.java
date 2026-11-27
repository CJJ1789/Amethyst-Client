package net.client.replay.session;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.client.util.ClientLog;
import net.client.mixin.ReplayClientPlayNetworkHandlerAccessor;

/**
 * Replay worlds are built without going through GameJoin, so the local player
 * profile often has no PlayerListEntry -- and 1.8.9 resolves every skin through
 * that entry (getCapeId -> PlayerListEntry#getSkinTexture, falling back to
 * DefaultSkinHelper, i.e. Steve).  Capture the live player's already loaded
 * skin once, before the replay player replaces it, and reuse it both for the
 * renderer redirect and for a synthetic player-list entry.
 */
public final class ReplayLocalSkin {
    private static Identifier skin;
    private static String model;

    private ReplayLocalSkin() {
    }

    /** @return true when a real (non-default) skin was captured. */
    public static boolean capture(Entity live) {
        if (skin != null) return true;                  // 已经拿到过，不要被回放玩家自己覆盖
        if (!(live instanceof AbstractClientPlayerEntity)) return false;
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) live;
        try {
            if (!player.hasSkinTexture()) return false; // 拿不到就保持上一次的结果
            Identifier id = player.getCapeId();
            if (id == null) return false;
            skin = id;
            model = player.getModel();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void clear() {
        skin = null;
        model = null;
    }

    public static Identifier skin() {
        return skin;
    }

    public static boolean hasSkin() {
        return skin != null;
    }

    /**
     * 用回放文件里保存的录制者身份（uuid + textures 属性）建一条 PlayerListEntry，
     * 这样即使回放从主菜单启动、世界里没有"真实玩家"，皮肤也能正常解析。
     */
    public static void installEntry(ClientPlayNetworkHandler handler, String uuid, String name, String textures) {
        if (handler == null) return;
        try {
            com.mojang.authlib.GameProfile base = handler.getProfile();
            java.util.UUID id = base == null ? null : base.getId();
            if (uuid != null && !uuid.isEmpty()) {
                try {
                    id = java.util.UUID.fromString(uuid);
                } catch (Throwable ignored) {
                }
            }
            if (id == null) return;
            String playerName = (name == null || name.isEmpty())
                    ? (base == null ? "Player" : base.getName()) : name;
            com.mojang.authlib.GameProfile profile =
                    new com.mojang.authlib.GameProfile(id, playerName);
            if (textures != null && !textures.isEmpty()) {
                profile.getProperties().put("textures",
                        new com.mojang.authlib.properties.Property("textures", textures));
            }
            PlayerListEntry entry = new PlayerListEntry(profile);
            java.util.Map<java.util.UUID, PlayerListEntry> list =
                    ((ReplayClientPlayNetworkHandlerAccessor) handler).client$getPlayerListEntries();
            list.put(id, entry);
            // 回放玩家自身的 uuid 来自 handler 的 profile，两个 key 都放上，避免对不上。
            if (base != null && base.getId() != null) list.put(base.getId(), entry);
            ClientLog.info("[replay] skin entry installed uuid=" + id
                    + " textures=" + (textures != null && !textures.isEmpty()));
        } catch (Throwable t) {
            ClientLog.warn("[replay] skin entry failed: " + t);
        }
    }

    /**
     * 1.8.9 的第二层皮肤（帽子/外套/袖子/裤子）不在纹理里开关，而是读实体上
     * DataTracker#id=10 的模型部件位；这个值正常由服务器同步，合成出来的回放
     * 玩家是 0，所以只剩单层。这里把七位全开。
     */
    public static void applyModelParts(Entity player) {
        if (player == null) return;
        try {
            player.getDataTracker().setProperty(10, Byte.valueOf((byte) 0x7F));
        } catch (Throwable t) {
            ClientLog.warn("[replay] model parts failed: " + t);
        }
    }

    /** True when the given entity is the replay/local player currently being drawn. */
    public static boolean isLocal(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && entity == client.player;
    }
}
