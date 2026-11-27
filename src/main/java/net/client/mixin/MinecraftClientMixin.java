package net.client.mixin;

import net.client.ClientConfig;
import net.client.ClientScreen;
import net.client.util.AfterimageHandler;
import net.client.util.FreelookHandler;
import net.client.util.OldBlockHitSwing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MinecraftClient.class})
public class MinecraftClientMixin {
   @Shadow
   private Screen currentScreen;
   @Shadow
   private ClientPlayerEntity player;
   @Shadow
   private GameOptions options;
   @Shadow
   private int attackCooldown;
   @Shadow
   public BlockHitResult result;
   @Unique
   private boolean client$shiftWasDown;

   @Shadow
   public void setScreen(Screen screen) {
   }

   /**
    * 1.0.1_16：原版 getMaxFramerate() 在「没有世界但有界面」（标题界面、各种菜单）时硬编码返回 30，
    * 导致主菜单只有 30 帧、鼠标移动一顿一顿。这里直接放行到玩家自己设的上限（不设上限则不限制）。
    */
   @Inject(
      method = {"getMaxFramerate"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$unlockMenuFps(CallbackInfoReturnable<Integer> cir) {
      if (this.options != null) {
         cir.setReturnValue(this.options.maxFramerate);
      }
   }

   /**
    * 攻击守卫（HEAD）：1.0.1_29 起「攻击手感优化」改成后台常开、界面上没有开关——
    * 每次点击前清掉攻击冷却（原「无点击冷却」），CPS 限流早已移除，不再拦截任何点击。
    */
   @Inject(
      method = {"doAttack"},
      at = {@At("HEAD")}
   )
   private void client$attackGuard(CallbackInfo ci) {
      this.attackCooldown = 0;
   }

   /**
    * Hit Delay Fix（RETURN）：命中实体后立即清掉攻击冷却，避免上一次空挥残留的 10 tick
    * 冷却延续到下一次有效攻击；空挥/未命中实体时不干预，保持原版冷却行为。
    * 1.0.1_29 起常开，不再看配置开关。
    */
   @Inject(
      method = {"doAttack"},
      at = {@At("RETURN")}
   )
   private void client$hitDelayFix(CallbackInfo ci) {
      if (this.result != null && this.result.type == BlockHitResult.Type.ENTITY && this.result.entity != null) {
         this.attackCooldown = 0;
      }
   }

   @Inject(
      method = {"handleBlockBreaking"},
      at = {@At("HEAD")}
   )
   private void client$oldBlockHitSwing(boolean breaking, CallbackInfo ci) {
      if (breaking) {
         OldBlockHitSwing.keepAlive((MinecraftClient)(Object)this);
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void client$onTick(CallbackInfo ci) {
      ClientConfig.ensureLoaded();
      ClientConfig.flushIfDirty();
      boolean shiftDown = Keyboard.isKeyDown(54);
      if (shiftDown && !this.client$shiftWasDown) {
         if (this.currentScreen instanceof ClientScreen) {
            this.setScreen(null);
         } else if (this.currentScreen == null || this.currentScreen instanceof TitleScreen) {
            this.setScreen(new ClientScreen());
         }
      }

      this.client$shiftWasDown = shiftDown;
      AfterimageHandler.tick();
      FreelookHandler.tick();

      if (ClientConfig.autoSprint
         && this.currentScreen == null
         && this.player != null
         && this.options != null
         && this.options.forwardKey.isPressed()
         && !this.player.isSneaking()
         && this.player.getHungerManager().getFoodLevel() > 6) {
         this.player.setSprinting(true);
      }
   }
}
