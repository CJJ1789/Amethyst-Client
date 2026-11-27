package net.client.mixin;

import net.client.replay.ReplayClient;
import net.client.replay.command.ReplayCommand;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.entity.player.ClientPlayerEntity.class)
public class ReplayClientPlayerEntityMixin {
    /**
     * 原版 ClientPlayerEntity.isSneaking() 只读实时键盘输入（input.sneaking），
     * 回放里没有键盘，所以蹲下永远读不出来。回放期间改成读实体 flag。
     */
    @Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true)
    private void client$isSneaking(CallbackInfoReturnable<Boolean> cir) {
        if (ReplayClient.isReplayActive() || ReplayClient.isReplayCaptureActive()) {
            boolean sneaking = ((EntityFlagInvoker) (Object) this).client$getFlag(1);
            if (Boolean.getBoolean("client.replay.soundDebug")) {
                System.out.println("[Sneak] replay isSneaking=" + sneaking);
            }
            cir.setReturnValue(sneaking);
        }
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void client$sendChatMessage(String message, CallbackInfo ci) {
        if (ReplayCommand.handle(message)) {
            ci.cancel();
        }
    }
}
