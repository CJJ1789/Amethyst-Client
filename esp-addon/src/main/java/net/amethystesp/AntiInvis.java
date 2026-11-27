package net.amethystesp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * 反隐身：把喝了隐身药水的人/怪在渲染时临时“取消隐身”，用半透明画出来。
 *
 * <p>移植自 Meowtils 的 Anti-Invis，但那边挂在 RenderPlayer 的 PRE/POST，
 * 这里挂在 LivingEntityRenderer#render 的头尾（玩家的 PlayerEntityRenderer#render
 * 也会走到 super.render，所以人和怪都能覆盖）。
 *
 * <p>1.8.9 的隐身在 LivingEntityRenderer#renderModel 里靠 entity.isInvisible() 判断，
 * 所以真正“露出来”的办法只能是渲染期间把标志位翻掉，画完立刻还原。
 * 万一中途异常让 TAIL 没跑到，{@link #tick()} 会把残留的实体补回隐身，避免它永久显形。
 */
public final class AntiInvis {

    private static final FloatBuffer COLOR_BUF = BufferUtils.createFloatBuffer(4);

    /** 当前这一次渲染被临时显形的实体，null 表示没有。 */
    private static LivingEntity active;
    private static boolean previousInvisible;
    private static boolean previousBlend;
    private static boolean previousAlphaTest;

    private AntiInvis() {
    }

    public static boolean shouldReveal(LivingEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return EspConfig.antiInvis
                && entity != null
                && entity.isInvisible()
                && entity != mc.player
                && entity.isAlive();
    }

    public static void begin(LivingEntity entity) {
        if (active != null) {
            // 上一次没还原（渲染异常），先收尾
            end(active);
        }
        if (!shouldReveal(entity)) {
            return;
        }

        active = entity;
        previousInvisible = entity.isInvisible();
        entity.setInvisible(false);

        previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        previousAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        COLOR_BUF.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR_BUF);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // 关掉 AlphaTest，0.5 的半透明像素才不会被 0.1 的阈值裁掉
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, EspConfig.invisAlpha);
    }

    public static void end(LivingEntity entity) {
        if (active == null || active != entity) {
            return;
        }
        active.setInvisible(previousInvisible);
        active = null;

        GL11.glColor4f(COLOR_BUF.get(0), COLOR_BUF.get(1), COLOR_BUF.get(2), COLOR_BUF.get(3));
        if (previousBlend) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        if (previousAlphaTest) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        } else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
    }

    /** 每 tick 兜底：上一次渲染留下的残留状态一律还原。 */
    public static void tick() {
        if (active != null) {
            LivingEntity entity = active;
            end(entity);
        }
    }
}
