package net.client.mixin;

import net.client.replay.session.CameraController;
import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Target-player first person: render the observed player's own arm / held item
 * instead of the local recorder's hand.  The observed player remains invisible
 * in the world while this hand layer is drawn.
 */
@Mixin(net.minecraft.client.render.item.HeldItemRenderer.class)
public class ReplayHeldItemRendererMixin {

    @Shadow
    private float equipProgress;
    @Shadow
    private float lastEquipProgress;
    @Shadow
    private net.minecraft.item.ItemStack mainHand;
    @Shadow
    private void applyPlayerLighting(net.minecraft.client.network.AbstractClientPlayerEntity player) {
    }
    @Shadow
    private void renderArm(net.minecraft.client.network.AbstractClientPlayerEntity player, float equipProgress, float swingProgress) {
    }
    @Shadow
    private void renderMap(net.minecraft.client.network.AbstractClientPlayerEntity player, float pitch, float equipProgress, float swingProgress) {
    }
    @Shadow
    private void translateSwingProgress(float swingProgress) {
    }
    @Shadow
    private void applyEquipAndSwingOffset(float equipProgress, float swingProgress) {
    }
    @Shadow
    private void applyEatOrDrinkTransformation(net.minecraft.client.network.AbstractClientPlayerEntity player, float tickDelta) {
    }
    @Shadow
    private void applySwordBlockTransformation() {
    }
    @Shadow
    private void applyBowTransformation(float tickDelta, net.minecraft.client.network.AbstractClientPlayerEntity player) {
    }
    @Shadow
    private void renderItem(net.minecraft.entity.LivingEntity entity, net.minecraft.item.ItemStack stack, net.minecraft.client.render.model.json.ModelTransformation.Mode mode) {
    }

    /**
     * OtherClientPlayerEntity is not a ClientPlayerEntity.  Vanilla
     * renderArmHoldingItem() checkcasts the local to ClientPlayerEntity before
     * applyPlayerRotation(), so replacing the local variable is not enough:
     * the CHECKCAST still throws before our redirect runs.  For replay targets
     * cancel vanilla and draw the target's bare arm here.
     */
    @Inject(method = "renderArmHoldingItem", at = @At("HEAD"), cancellable = true)
    private void client$renderTargetArm(float tickDelta, CallbackInfo ci) {
        net.minecraft.client.network.AbstractClientPlayerEntity target = CameraController.getFirstPersonHandPlayer();
        if (target == null) return;
        float f = 1.0F - (this.lastEquipProgress + (this.equipProgress - this.lastEquipProgress) * tickDelta);
        float pitch = target.prevPitch + (target.pitch - target.prevPitch) * tickDelta;
        float swing = target.getHandSwingProgress(tickDelta);
        this.applyPlayerLighting(target);
        com.mojang.blaze3d.platform.GlStateManager.enableRescaleNormal();
        com.mojang.blaze3d.platform.GlStateManager.pushMatrix();
        net.minecraft.item.ItemStack stack = this.mainHand;
        if (stack != null) {
            if (stack.getItem() == net.minecraft.item.Items.FILLED_MAP) {
                this.renderMap(target, pitch, f, swing);
            } else if (target.getItemUseTicks() > 0) {
                net.minecraft.util.UseAction action = stack.getUseAction();
                if (action == net.minecraft.util.UseAction.EAT || action == net.minecraft.util.UseAction.DRINK) {
                    this.applyEatOrDrinkTransformation(target, tickDelta);
                    this.applyEquipAndSwingOffset(f, 0.0F);
                } else if (action == net.minecraft.util.UseAction.BLOCK) {
                    this.applyEquipAndSwingOffset(f, 0.0F);
                    this.applySwordBlockTransformation();
                } else if (action == net.minecraft.util.UseAction.BOW) {
                    this.applyEquipAndSwingOffset(f, 0.0F);
                    this.applyBowTransformation(tickDelta, target);
                } else {
                    this.applyEquipAndSwingOffset(f, 0.0F);
                }
                this.renderItem(target, stack, net.minecraft.client.render.model.json.ModelTransformation.Mode.FIRST_PERSON);
            } else {
                this.translateSwingProgress(swing);
                this.applyEquipAndSwingOffset(f, swing);
                this.renderItem(target, stack, net.minecraft.client.render.model.json.ModelTransformation.Mode.FIRST_PERSON);
            }
        } else {
            this.renderArm(target, f, swing);
        }
        com.mojang.blaze3d.platform.GlStateManager.popMatrix();
        com.mojang.blaze3d.platform.GlStateManager.disableRescaleNormal();
        net.minecraft.client.render.DiffuseLighting.disable();
        ci.cancel();
    }

    @ModifyVariable(method = "updateHeldItems", at = @At("STORE"), index = 1)
    private net.minecraft.entity.player.PlayerEntity client$targetHeldItemPlayer(net.minecraft.entity.player.PlayerEntity original) {
        net.minecraft.client.network.AbstractClientPlayerEntity target = CameraController.getFirstPersonHandPlayer();
        return target != null ? target : original;
    }

    @ModifyVariable(method = "renderArmHoldingItem", at = @At("STORE"), index = 3)
    private net.minecraft.client.network.AbstractClientPlayerEntity client$targetHandPlayer(net.minecraft.client.network.AbstractClientPlayerEntity original) {
        net.minecraft.client.network.AbstractClientPlayerEntity target = CameraController.getFirstPersonHandPlayer();
        return target != null ? target : original;
    }

    @ModifyVariable(method = "renderArm", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private net.minecraft.client.network.AbstractClientPlayerEntity client$targetArmSkin(net.minecraft.client.network.AbstractClientPlayerEntity original) {
        net.minecraft.client.network.AbstractClientPlayerEntity target = CameraController.getFirstPersonHandPlayer();
        return target != null ? target : original;
    }

    @Redirect(method = "renderArmHoldingItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isInvisible()Z"))
    private boolean client$armIsVisible(net.minecraft.client.network.AbstractClientPlayerEntity player) {
        // The target's body is hidden from the world in first person; that flag
        // must not suppress the first-person arm layer.
        return CameraController.getFirstPersonHandPlayer() != null ? false : player.isInvisible();
    }

    @Redirect(method = "renderArm",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;getRenderer(Lnet/minecraft/entity/Entity;)Lnet/minecraft/client/render/entity/EntityRenderer;"))
    private net.minecraft.client.render.entity.EntityRenderer client$targetArmRenderer(net.minecraft.client.render.entity.EntityRenderDispatcher dispatcher, net.minecraft.entity.Entity entity) {
        net.minecraft.client.network.AbstractClientPlayerEntity target = CameraController.getFirstPersonHandPlayer();
        return dispatcher.getRenderer(target != null ? target : entity);
    }

    @Redirect(method = "renderArm",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderRightArm(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)V"))
    private void client$renderTargetRightArm(net.minecraft.client.render.entity.PlayerEntityRenderer renderer, net.minecraft.client.network.AbstractClientPlayerEntity player) {
        net.minecraft.client.network.AbstractClientPlayerEntity target = CameraController.getFirstPersonHandPlayer();
        renderer.renderRightArm(target != null ? target : player);
    }
}
