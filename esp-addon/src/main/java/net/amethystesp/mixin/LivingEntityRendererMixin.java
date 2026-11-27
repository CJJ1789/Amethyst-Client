package net.amethystesp.mixin;

import net.amethystesp.AntiInvis;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 反隐身的挂载点：所有生物模型（含玩家，PlayerEntityRenderer#render 也会走 super.render）
 * 都会经过 LivingEntityRenderer#render。
 *
 * <p>在 HEAD 临时取消隐身 + 设半透明，RETURN 还原；如果方法内部抛异常没走到 RETURN，
 * ClientTick 里的 AntiInvis#tick 会兜底还原。
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;DDDFF)V", at = @At("HEAD"))
    private void amethystesp$antiInvisBegin(LivingEntity entity, double x, double y, double z,
                                             float yaw, float tickDelta, CallbackInfo ci) {
        AntiInvis.begin(entity);
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;DDDFF)V", at = @At("RETURN"))
    private void amethystesp$antiInvisEnd(LivingEntity entity, double x, double y, double z,
                                           float yaw, float tickDelta, CallbackInfo ci) {
        AntiInvis.end(entity);
    }
}
