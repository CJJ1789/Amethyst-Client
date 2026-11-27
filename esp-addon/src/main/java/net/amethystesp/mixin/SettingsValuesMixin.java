package net.amethystesp.mixin;

import net.amethystesp.AmethystSettings;
import net.amethystesp.EspConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Amethyst Client 1.0.2_24 把所有设置的取值/写入集中到了
 * {@code net.client.settings.SettingsValues} 的四个静态方法（都按 key 分派）。
 *
 * <p>界面上点开关走 {@code toggle}，绘制开关走 {@code isOn}，颜色块走 {@code colorOf} /
 * {@code setColor}，所以只要在这四个方法头上把我们的 key 截下来，
 * 透视的行就能像客户端自己的设置一样工作，不需要再去改 ClientScreen。
 *
 * <p>目标类不是 MC 的映射类，全部 remap = false；require = 0 保证客户端改签名时不影响启动。
 */
@Mixin(targets = "net.client.settings.SettingsValues", remap = false)
public class SettingsValuesMixin {

    @Inject(method = "isOn", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void amethystesp$isOn(int key, CallbackInfoReturnable<Boolean> cir) {
        Boolean on = AmethystSettings.toggleState(key);
        if (on != null) {
            cir.setReturnValue(on);
        }
    }

    @Inject(method = "toggle", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void amethystesp$toggle(int key, CallbackInfo ci) {
        if (AmethystSettings.toggle(key)) {
            // 开关是低频操作，直接写一次；颜色拖动才需要防抖
            EspConfig.save();
            ci.cancel();
        }
    }

    @Inject(method = "colorOf", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void amethystesp$colorOf(int key, CallbackInfoReturnable<Integer> cir) {
        int index = AmethystSettings.colorIndex(key);
        if (index >= 0) {
            cir.setReturnValue(EspConfig.color(index));
        }
    }

    @Inject(method = "setColor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void amethystesp$setColor(int key, int argb, CallbackInfo ci) {
        int index = AmethystSettings.colorIndex(key);
        if (index >= 0) {
            EspConfig.setColor(index, argb);
            EspConfig.requestSave();
            ci.cancel();
        }
    }
}
