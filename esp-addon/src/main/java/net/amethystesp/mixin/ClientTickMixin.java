package net.amethystesp.mixin;

import net.amethystesp.AntiInvis;
import net.amethystesp.ClientApiProbe;
import net.amethystesp.EspConfig;
import net.amethystesp.EspScanner;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端每个 tick：
 * <ul>
 *   <li>推进一点区块扫描（分帧做，避免一次性扫描造成卡顿）；</li>
 *   <li>把有防抖的配置改动落盘；</li>
 *   <li>兜底还原反隐身临时改过的隐身标志位；</li>
 *   <li>客户端设置界面打开后把 net.client.* 的真实签名打一份进日志（诊断用）。</li>
 * </ul>
 */
@Mixin(MinecraftClient.class)
public class ClientTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void amethystesp$tick(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        ClientApiProbe.tick(mc);
        EspScanner.tick(mc);
        EspConfig.flushIfDirty();
        AntiInvis.tick();
    }
}
