package net.client.mixin;

import net.client.replay.ReplayClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.sound.SoundManager.class)
public abstract class ReplaySoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void client$recordSound(net.minecraft.client.sound.SoundInstance sound, CallbackInfo ci) {
        // A dual-world background export must not leak replay sounds into the
        // live game's sound manager (and must not record them into the live
        // replay either).
        if (ReplayClient.isReplayCaptureActive()) {
            ci.cancel();
            return;
        }
        if (Boolean.getBoolean("client.replay.soundDebug")) {
            System.out.println("[SoundHook] play " + sound.getIdentifier());
        }
        try {
            net.client.replay.ReplayClient.noteSoundSeen();
            if (ReplayClient.getRecordingManager() != null) {
                ReplayClient.getRecordingManager().onSound(sound);
            }
        } catch (Throwable ignored) {
        }
    }
}
