package net.amethystesp;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombiePigmanEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Map;

/**
 * 世界渲染末端的 ESP 绘制。
 *
 * <p>注入点是 WorldRenderer#renderEntities 的 TAIL：此时 modelview 已在相机原点，
 * 先 translate(-camX,-camY,-camZ) 再按绝对世界坐标画。
 *
 * <p>整段绘制只用裸 GL、不碰 GlStateManager：客户端/OptiFine 有些地方直接调原生 GL，
 * GlStateManager 的状态缓存会和真实状态脱同步，用缓存去判断“要不要下发 disable”并不可靠。
 * 进入前把要改的状态用 glIsEnabled/glGetFloat 原样记下来，结束后逐一还原，
 * 而不是假定后续渲染一定是“纹理开、深度开、雾开”那一套。
 */
public final class EspRenderer {

    /** 床头高度（原版 BedBlock 的碰撞箱就是 0.5625）。 */
    private static final double BED_HEIGHT = 0.5625;

    private static final FloatBuffer COLOR_BUF = BufferUtils.createFloatBuffer(4);

    private EspRenderer() {
    }

    public static void render(float tickDelta) {
        if (!EspConfig.anyEnabled()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }

        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            camera = mc.player;
        }

        double camX = camera.prevTickX + (camera.x - camera.prevTickX) * tickDelta;
        double camY = camera.prevTickY + (camera.y - camera.prevTickY) * tickDelta;
        double camZ = camera.prevTickZ + (camera.z - camera.prevTickZ) * tickDelta;

        // 记录会被改动的状态，结束时按原值还原（而不是按“标准世界渲染”猜一套）
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean prevFog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean prevTexture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        float prevLineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        COLOR_BUF.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR_BUF);

        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(2.0f);
            GL11.glTranslated(-camX, -camY, -camZ);

            if (EspConfig.blocks) {
                renderBlocks(mc, camX, camY, camZ);
            }
            if (EspConfig.ores) {
                renderOres(mc, camX, camY, camZ);
            }
            if (EspConfig.beds) {
                renderBeds(mc, camX, camY, camZ);
            }
            if (EspConfig.entities) {
                renderEntities(mc);
            }
        } finally {
            GL11.glPopMatrix();

            GL11.glLineWidth(prevLineWidth);
            GL11.glColor4f(COLOR_BUF.get(0), COLOR_BUF.get(1), COLOR_BUF.get(2), COLOR_BUF.get(3));

            setEnabled(GL11.GL_DEPTH_TEST, prevDepthTest);
            GL11.glDepthMask(prevDepthMask);
            setEnabled(GL11.GL_FOG, prevFog);
            setEnabled(GL11.GL_TEXTURE_2D, prevTexture);
            setEnabled(GL11.GL_CULL_FACE, prevCull);
            setEnabled(GL11.GL_ALPHA_TEST, prevAlphaTest);
            // 混合的开关还原；混合函数保持原版默认，后续渲染自己会设
            if (prevBlend) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            setEnabled(GL11.GL_LIGHTING, prevLighting);
        }
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    private static void renderBlocks(MinecraftClient mc, double camX, double camY, double camZ) {
        double rangeSq = (double) EspConfig.blockRange * EspConfig.blockRange;
        for (BlockPos pos : EspScanner.blockEntities()) {
            if (outsideHorizontally(pos, camX, camZ, rangeSq)) {
                continue;
            }

            BlockState state = mc.world.getBlockState(pos);
            displayBox(mc, pos, state, blockColor(state.getBlock()));
        }
    }

    private static void renderOres(MinecraftClient mc, double camX, double camY, double camZ) {
        double rangeSq = (double) EspConfig.oreRange * EspConfig.oreRange;
        for (Map.Entry<BlockPos, Integer> entry : EspScanner.ores().entrySet()) {
            BlockPos pos = entry.getKey();
            if (outsideHorizontally(pos, camX, camZ, rangeSq)) {
                continue;
            }

            int type = entry.getValue();
            drawBox(cube(pos), EspConfig.color(EspConfig.C_DIAMOND + type), EspConfig.fillAlpha);
        }
    }

    /**
     * 床：只缓存 HEAD，按它记录的 FACING 反推 FOOT，两格拼成一个 0.5625 高的框。
     * 床被拆掉一半或区块数据过期时，只画还在的那一格，不整段丢。
     */
    private static void renderBeds(MinecraftClient mc, double camX, double camY, double camZ) {
        double rangeSq = (double) EspConfig.bedRange * EspConfig.bedRange;
        for (BlockPos head : EspScanner.beds()) {
            if (outsideHorizontally(head, camX, camZ, rangeSq)) {
                continue;
            }

            BlockState state = mc.world.getBlockState(head);
            if (!(state.getBlock() instanceof BedBlock)
                    || state.get(BedBlock.BED_TYPE) != BedBlock.BedBlockType.HEAD) {
                continue;
            }

            Direction facing = state.get(BedBlock.FACING);
            BlockPos foot = head.offset(facing.getOpposite());
            boolean hasFoot = mc.world.getBlockState(foot).getBlock() instanceof BedBlock;
            Box box = hasFoot ? bedBox(head, foot) : halfBedBox(head);
            drawBox(box, EspConfig.color(EspConfig.C_BED), EspConfig.fillAlpha);
        }

        if (!EspConfig.showObsidian) {
            return;
        }
        for (BlockPos pos : EspScanner.bedObsidian()) {
            if (outsideHorizontally(pos, camX, camZ, rangeSq)) {
                continue;
            }
            if (mc.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) {
                continue;
            }
            drawBox(cube(pos), EspConfig.color(EspConfig.C_OBSIDIAN), EspConfig.fillAlpha);
        }
    }

    private static void renderEntities(MinecraftClient mc) {
        double rangeSq = (double) EspConfig.entityRange * EspConfig.entityRange;
        PlayerEntity self = mc.player;

        for (Entity entity : mc.world.getLoadedEntities()) {
            if (entity == self || !entity.isAlive() || !(entity instanceof LivingEntity)) {
                continue;
            }

            double dx = entity.x - self.x;
            double dy = entity.y - self.y;
            double dz = entity.z - self.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                continue;
            }

            Box box = entity.getBoundingBox().expand(0.05, 0.05, 0.05);
            drawBox(box, entityColor(entity), EspConfig.fillAlpha);
        }
    }

    /**
     * 方块类只按水平距离裁剪，和 EspScanner 的扫描口径一致（高度不限）。
     * 扫描本来就没算竖直距离，这里再按 3D 距离砍会让“扫到了却永远不画”的地下层白白占内存。
     */
    private static boolean outsideHorizontally(BlockPos pos, double camX, double camZ, double rangeSq) {
        double dx = pos.getX() + 0.5 - camX;
        double dz = pos.getZ() + 0.5 - camZ;
        return dx * dx + dz * dz > rangeSq;
    }

    private static Box cube(BlockPos pos) {
        return new Box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    private static Box halfBedBox(BlockPos pos) {
        return new Box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + BED_HEIGHT, pos.getZ() + 1.0);
    }

    private static Box bedBox(BlockPos head, BlockPos foot) {
        return new Box(
                Math.min(head.getX(), foot.getX()),
                Math.min(head.getY(), foot.getY()),
                Math.min(head.getZ(), foot.getZ()),
                Math.max(head.getX(), foot.getX()) + 1.0,
                Math.max(head.getY(), foot.getY()) + BED_HEIGHT,
                Math.max(head.getZ(), foot.getZ()) + 1.0);
    }

    /**
     * 方块归类：陷阱箱橙、箱子（含末影箱）黄、其余特殊方块（头颅/火把/告示牌等）白。
     * 这些东西都不直接向玩家提供资源，所以除了箱子之外统一用浅色，避免和矿石抢眼。
     */
    private static int blockColor(Block block) {
        if (block == Blocks.TRAPPED_CHEST) {
            return EspConfig.color(EspConfig.C_TRAP);
        }
        if (block == Blocks.CHEST || block == Blocks.ENDERCHEST) {
            return EspConfig.color(EspConfig.C_CHEST);
        }
        return EspConfig.color(EspConfig.C_OTHER_BLOCK);
    }

    /**
     * 实体归类。顺序要先判中立再判敌对：1.8.9 里狼、末影人、僵尸猪人、铁傀儡本身也是 MobEntity，
     * 以前先判 MobEntity 会让它们全部落到「攻击生物」红色，中立分支永远走不到。
     * 隐身的生物在开启反隐身时单独用青色，方便一眼区分“这是隐身的人/怪”。
     */
    private static int entityColor(Entity entity) {
        if (EspConfig.antiInvis && entity.isInvisible()) {
            return EspConfig.color(EspConfig.C_INVIS);
        }
        if (entity instanceof IronGolemEntity || entity instanceof WolfEntity
                || entity instanceof EndermanEntity || entity instanceof ZombiePigmanEntity) {
            return EspConfig.color(EspConfig.C_NEUTRAL);
        }
        if (entity instanceof PlayerEntity || entity instanceof MobEntity) {
            return EspConfig.color(EspConfig.C_HOSTILE);
        }
        return EspConfig.color(EspConfig.C_PEACEFUL);
    }

    /**
     * 按方块自身的选取箱画框（火把小、告示牌薄、箱子近整格）。
     *
     * <p>注意 Block#getSelectionBox 返回的是**世界绝对坐标**（内部已经加上 pos），
     * 所以判断“是否超出本方块 0-1 范围”要先把 pos 减回去。以前直接拿绝对值比 0-1，
     * 除了原点那一格永远不成立，等于这个功能一直没生效。
     *
     * <p>箱子连成大箱子时，原版给的是往相邻那一格偏移的碰撞箱，会凸到隔壁方块里；
     * 只要发现选取箱超出本方块范围，就退回整格方块，保证框永远落在这一格里。
     */
    private static void displayBox(MinecraftClient mc, BlockPos pos, BlockState state, int argb) {
        Box shape = null;
        try {
            shape = state.getBlock().getSelectionBox(mc.world, pos);
        } catch (Throwable ignored) {
            // 少数方块拿不到形状时退回整格
        }
        if (shape != null) {
            double minX = shape.minX - pos.getX();
            double minY = shape.minY - pos.getY();
            double minZ = shape.minZ - pos.getZ();
            double maxX = shape.maxX - pos.getX();
            double maxY = shape.maxY - pos.getY();
            double maxZ = shape.maxZ - pos.getZ();
            if (minX >= -0.001 && minY >= -0.001 && minZ >= -0.001
                    && maxX <= 1.001 && maxY <= 1.001 && maxZ <= 1.001
                    && maxX - minX > 0.01 && maxY - minY > 0.01 && maxZ - minZ > 0.01) {
                drawBox(shape.expand(0.002, 0.002, 0.002), argb, EspConfig.fillAlpha);
                return;
            }
        }
        drawBox(cube(pos), argb, EspConfig.fillAlpha);
    }

    private static void drawBox(BlockPos pos, int argb, float fillAlpha) {
        drawBox(cube(pos), argb, fillAlpha);
    }

    private static void drawBox(Box box, int argb, float fillAlpha) {
        drawBox(box, ((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f, fillAlpha);
    }

    private static void drawBox(Box box, float red, float green, float blue, float fillAlpha) {
        if (fillAlpha > 0.0f) {
            GL11.glColor4f(red, green, blue, fillAlpha);
            GL11.glBegin(GL11.GL_QUADS);
            // 底面
            vertex(box.minX, box.minY, box.minZ);
            vertex(box.maxX, box.minY, box.minZ);
            vertex(box.maxX, box.minY, box.maxZ);
            vertex(box.minX, box.minY, box.maxZ);
            // 顶面
            vertex(box.minX, box.maxY, box.minZ);
            vertex(box.minX, box.maxY, box.maxZ);
            vertex(box.maxX, box.maxY, box.maxZ);
            vertex(box.maxX, box.maxY, box.minZ);
            // 北面
            vertex(box.minX, box.minY, box.minZ);
            vertex(box.minX, box.maxY, box.minZ);
            vertex(box.maxX, box.maxY, box.minZ);
            vertex(box.maxX, box.minY, box.minZ);
            // 南面
            vertex(box.minX, box.minY, box.maxZ);
            vertex(box.maxX, box.minY, box.maxZ);
            vertex(box.maxX, box.maxY, box.maxZ);
            vertex(box.minX, box.maxY, box.maxZ);
            // 西面
            vertex(box.minX, box.minY, box.minZ);
            vertex(box.minX, box.minY, box.maxZ);
            vertex(box.minX, box.maxY, box.maxZ);
            vertex(box.minX, box.maxY, box.minZ);
            // 东面
            vertex(box.maxX, box.minY, box.minZ);
            vertex(box.maxX, box.maxY, box.minZ);
            vertex(box.maxX, box.maxY, box.maxZ);
            vertex(box.maxX, box.minY, box.maxZ);
            GL11.glEnd();
        }

        GL11.glColor4f(red, green, blue, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        // 底部四条边
        vertex(box.minX, box.minY, box.minZ);
        vertex(box.maxX, box.minY, box.minZ);
        vertex(box.maxX, box.minY, box.minZ);
        vertex(box.maxX, box.minY, box.maxZ);
        vertex(box.maxX, box.minY, box.maxZ);
        vertex(box.minX, box.minY, box.maxZ);
        vertex(box.minX, box.minY, box.maxZ);
        vertex(box.minX, box.minY, box.minZ);
        // 顶部四条边
        vertex(box.minX, box.maxY, box.minZ);
        vertex(box.maxX, box.maxY, box.minZ);
        vertex(box.maxX, box.maxY, box.minZ);
        vertex(box.maxX, box.maxY, box.maxZ);
        vertex(box.maxX, box.maxY, box.maxZ);
        vertex(box.minX, box.maxY, box.maxZ);
        vertex(box.minX, box.maxY, box.maxZ);
        vertex(box.minX, box.maxY, box.minZ);
        // 竖直四条边
        vertex(box.minX, box.minY, box.minZ);
        vertex(box.minX, box.maxY, box.minZ);
        vertex(box.maxX, box.minY, box.minZ);
        vertex(box.maxX, box.maxY, box.minZ);
        vertex(box.maxX, box.minY, box.maxZ);
        vertex(box.maxX, box.maxY, box.maxZ);
        vertex(box.minX, box.minY, box.maxZ);
        vertex(box.minX, box.maxY, box.maxZ);
        GL11.glEnd();
    }

    private static void vertex(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }
}
