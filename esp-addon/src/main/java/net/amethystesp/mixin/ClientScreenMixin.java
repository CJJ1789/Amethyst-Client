package net.amethystesp.mixin;

import net.amethystesp.AmethystSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 接进 Amethyst Client 1.0.2_24 的设置界面：
 * <ul>
 *   <li>{@code buildGroups} 末尾往 groups 里插「透视」分类；</li>
 *   <li>{@code label(int)} 给我们自己的 key 提供文案（中文/英文跟客户端界面语言走）。</li>
 * </ul>
 *
 * <p>开关与颜色的读写不在这里——这个版本的客户端已经把它们挪到
 * {@code net.client.settings.SettingsValues}，见 {@link SettingsValuesMixin}。
 *
 * <p>用 targets 字符串而不是直接引类，这样编译期不依赖客户端 jar；
 * 两个注入都带 require = 0，客户端再改版时只是不生效，不会把启动打死。
 */
@Mixin(targets = "net.client.ClientScreen", remap = false)
public class ClientScreenMixin {

    @Inject(method = "buildGroups", at = @At("TAIL"), remap = false, require = 0)
    private void amethystesp$addGroup(CallbackInfo ci) {
        AmethystSettings.addGroup(this);
    }

    @Inject(method = "label", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void amethystesp$label(int key, CallbackInfoReturnable<String> cir) {
        String text = AmethystSettings.labelFor(key);
        if (text != null) {
            cir.setReturnValue(text);
        }
    }
}
