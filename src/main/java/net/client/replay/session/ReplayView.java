package net.client.replay.session;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.client.util.ClientLog;

/**
 * 回放里"该不该把本地玩家藏起来"只应看一件事：镜头是不是真的在它脑袋里。
 * 1.8.9 原生用的是 {@code cameraEntity == player && perspective == 0}，但回放的
 * 镜头由 CameraController 驱动，F5 改 perspective 只会让这些门控和实际画面不同步，
 * 于是人"莫名其妙"消失。这里改成按真实相机坐标判定。
 */
public final class ReplayView {

    private ReplayView() {
    }

    /**
     * 回放中是否应当把本地玩家的身体藏起来（= 第一人称，镜头在自己眼睛上）。
     * <p>
     * 相机实体就是玩家时用原生判据 {@code perspective == 0}；相机是独立实体（回放相机）
     * 时用相机实体自身坐标到玩家眼睛的距离判定。
     * 不要用 {@code Camera.getPosition()}：1.8.9 的 {@code Camera.position} 只在
     * {@code Camera.update} 里由 gluUnProject 写入，回放这条路径不调用它，值恒为 (0,0,0)，
     * 判定永远失败——这正是"第一人称被自己的头挡住"的成因。
     */
    public static boolean shouldHideLocalPlayer(Entity player) {
        if (!isLocalPlayer(player)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return false;

        Entity cam = client.getCameraEntity();
        boolean hide;
        if (cam == null) {
            hide = false;
        } else if (cam == player) {
            // 相机实体就是玩家本人：原生判据足够（第三人称时 perspective != 0）。
            hide = client.options.perspective == 0;
        } else {
            // 回放的相机是一个独立的相机实体，原生的 {cameraEntity == player} 永远不成立。
            // 这时只看一件事：相机是不是真的贴在这个玩家的眼睛上。是 -> 第一人称，藏身体；
            // 否则（第三人称 / 自由相机 / 看别人）身体照常画出来。
            double eyeY = player.y + player.getEyeHeight();
            double dx = cam.x - player.x;
            double dy = cam.y - eyeY;
            double dz = cam.z - player.z;
            hide = (dx * dx + dy * dy + dz * dz) < 0.45 * 0.45;
        }
        trace(player, client, hide);
        return hide;
    }

    private static long lastTraceMs;

    private static void trace(Entity player, MinecraftClient client, boolean hide) {
        long now = System.currentTimeMillis();
        if (now - lastTraceMs < 2000L) return;
        lastTraceMs = now;
        Entity cam = client.getCameraEntity();
        ClientLog.info("[replay] view hide=" + hide + " persp=" + client.options.perspective
                + " cam=" + (cam == null ? "null" : cam.getClass().getSimpleName())
                + " camIsPlayer=" + (cam == player));
    }

    public static boolean isLocalPlayer(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && entity == client.player;
    }
}
