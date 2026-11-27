package net.client.mixin;

import net.client.replay.ReplayClient;
import net.client.replay.session.ReplayLocalSkin;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.8.9 resolves player skins through the network handler's PlayerListEntry
 * (AbstractClientPlayerEntity#getCapeId -> PlayerListEntry#getSkinTexture).
 * Replay worlds never run GameJoin, so that lookup misses and the renderer
 * falls back to DefaultSkinHelper = Steve.
 *
 * ReplayLocalSkin captures the live player's loaded skin before the replay
 * player replaces it; hand that identifier back for the local replay player.
 */
@Mixin(net.minecraft.client.render.entity.PlayerEntityRenderer.class)
public class ReplayLocalSkinMixin {
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void client$replayLocalSkin(AbstractClientPlayerEntity player,
            CallbackInfoReturnable<Identifier> cir) {
        if (!ReplayClient.isReplayActive() && !ReplayClient.isReplayCaptureActive()) return;
        if (!ReplayLocalSkin.hasSkin()) return;
        if (!ReplayLocalSkin.isLocal(player)) return;
        cir.setReturnValue(ReplayLocalSkin.skin());
    }
}
