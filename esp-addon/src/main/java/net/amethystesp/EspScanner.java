package net.amethystesp;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 扫描已加载区块里的矿石、「特殊方块」（箱子、告示牌、火把、红石线……）以及床。
 *
 * <p>不在渲染线程里每帧扫：分成若干个 tick，每次只扫几列区块，
 * 扫完再整体替换缓存，避免在手机上产生卡顿。玩家移动超过 24 格或超过 2 秒会重新扫。
 *
 * <p>床按原版 BedBlock 的 part 属性只记 HEAD 半块（一张床两格，FOOT 由渲染时按朝向反推），
 * 床周围的黑曜石不逐方块缓存，而是在扫描收尾时按床的位置做局部查找（一张床一次 5×5×5）。
 */
public final class EspScanner {

    /** 每个 tick 最多扫几个区块（16×16×256）。 */
    private static final int CHUNK_BUDGET = 3;
    private static final long RESCAN_MS = 2000L;
    private static final double MOVE_THRESHOLD_SQ = 24.0 * 24.0;
    /** 床头黑曜石查找半径（格），和 Meowtils 的 distanceSq <= 4 一致。 */
    private static final int OBSIDIAN_SEARCH_RADIUS = 2;

    private static Object worldRef;

    /** 矿石位置 → 矿种下标（对应 EspConfig.C_DIAMOND 起的顺序）。 */
    private static final Map<BlockPos, Integer> ORE_RESULT = new HashMap<BlockPos, Integer>();
    private static final Set<BlockPos> BLOCK_RESULT = new HashSet<BlockPos>();
    /** 床 HEAD 半块的位置。 */
    private static final Set<BlockPos> BED_RESULT = new HashSet<BlockPos>();
    /** 床周围的黑曜石，扫描收尾时从世界中现查。 */
    private static final Set<BlockPos> OBSIDIAN_RESULT = new HashSet<BlockPos>();

    private static Map<BlockPos, Integer> buildingOres;
    private static Set<BlockPos> buildingBlocks;
    private static Set<BlockPos> buildingBeds;
    private static final List<int[]> QUEUE = new ArrayList<int[]>();
    private static int cursor;

    private static double centerX;
    private static double centerZ;
    private static long lastComplete;

    private EspScanner() {
    }

    public static Map<BlockPos, Integer> ores() {
        return ORE_RESULT;
    }

    public static Set<BlockPos> blockEntities() {
        return BLOCK_RESULT;
    }

    /** 床 HEAD 位置（渲染时再按朝向补出 FOOT，拼成一张床的框）。 */
    public static Set<BlockPos> beds() {
        return BED_RESULT;
    }

    public static Set<BlockPos> bedObsidian() {
        return OBSIDIAN_RESULT;
    }

    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.world == null || mc.player == null) {
            clear();
            return;
        }
        if (worldRef != mc.world) {
            clear();
            worldRef = mc.world;
        }
        if (!EspConfig.ores && !EspConfig.blocks && !EspConfig.beds) {
            return;
        }
        if (buildingOres != null || buildingBlocks != null) {
            scanBudget(mc);
            return;
        }

        long now = System.currentTimeMillis();
        double dx = mc.player.x - centerX;
        double dz = mc.player.z - centerZ;
        boolean moved = dx * dx + dz * dz > MOVE_THRESHOLD_SQ;
        if (now - lastComplete > RESCAN_MS || moved || QUEUE.isEmpty()) {
            startScan(mc);
        }
    }

    public static void clear() {
        ORE_RESULT.clear();
        BLOCK_RESULT.clear();
        BED_RESULT.clear();
        OBSIDIAN_RESULT.clear();
        buildingOres = null;
        buildingBlocks = null;
        buildingBeds = null;
        QUEUE.clear();
        cursor = 0;
        worldRef = null;
        lastComplete = 0L;
    }

    private static void startScan(MinecraftClient mc) {
        World world = mc.world;
        centerX = mc.player.x;
        centerZ = mc.player.z;

        int radius = 0;
        if (EspConfig.ores) {
            radius = Math.max(radius, chunksFor(EspConfig.oreRange));
        }
        if (EspConfig.blocks) {
            radius = Math.max(radius, chunksFor(EspConfig.blockRange));
        }
        if (EspConfig.beds) {
            radius = Math.max(radius, chunksFor(EspConfig.bedRange));
        }
        // 范围已经在 EspConfig 里按 MAX_RANGE 截断，这里再兜一次底
        radius = Math.max(0, Math.min(radius, EspConfig.MAX_RANGE / 16));

        int pcx = (int) Math.floor(centerX) >> 4;
        int pcz = (int) Math.floor(centerZ) >> 4;

        QUEUE.clear();
        cursor = 0;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (world.getChunkProvider().chunkExists(cx, cz)) {
                    QUEUE.add(new int[]{cx, cz});
                }
            }
        }

        buildingOres = new HashMap<BlockPos, Integer>();
        buildingBlocks = new HashSet<BlockPos>();
        buildingBeds = new HashSet<BlockPos>();
    }

    private static int chunksFor(int range) {
        return (range + 15) / 16;
    }

    private static void scanBudget(MinecraftClient mc) {
        int budget = CHUNK_BUDGET;
        while (budget > 0 && cursor < QUEUE.size()) {
            int[] cc = QUEUE.get(cursor++);
            scanChunk(mc.world, cc[0], cc[1]);
            budget--;
        }
        if (cursor >= QUEUE.size()) {
            ORE_RESULT.clear();
            ORE_RESULT.putAll(buildingOres);
            BLOCK_RESULT.clear();
            BLOCK_RESULT.addAll(buildingBlocks);
            BED_RESULT.clear();
            BED_RESULT.addAll(buildingBeds);
            resolveBedObsidian(mc.world);

            buildingOres = null;
            buildingBlocks = null;
            buildingBeds = null;
            QUEUE.clear();
            cursor = 0;
            lastComplete = System.currentTimeMillis();
        }
    }

    private static void scanChunk(World world, int cx, int cz) {
        Chunk chunk;
        try {
            chunk = world.getChunk(cx, cz);
        } catch (Throwable t) {
            return;
        }
        if (chunk == null) {
            return;
        }
        ChunkSection[] sections = chunk.getBlockStorage();
        if (sections == null) {
            return;
        }

        double oreRangeSq = (double) EspConfig.oreRange * EspConfig.oreRange;
        double blockRangeSq = (double) EspConfig.blockRange * EspConfig.blockRange;
        double bedRangeSq = (double) EspConfig.bedRange * EspConfig.bedRange;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) {
                continue;
            }
            int baseY = section.getYOffset();
            for (int ly = 0; ly < 16; ly++) {
                int y = baseY + ly;
                if (y < 0 || y > 255) {
                    continue;
                }
                for (int lz = 0; lz < 16; lz++) {
                    int z = (cz << 4) + lz;
                    double dz = z + 0.5 - centerZ;
                    double dzSq = dz * dz;
                    for (int lx = 0; lx < 16; lx++) {
                        Block block = section.getBlock(lx, ly, lz);
                        if (block == Blocks.AIR) {
                            continue;
                        }
                        int x = (cx << 4) + lx;
                        double dx = x + 0.5 - centerX;
                        double dxzSq = dx * dx + dzSq;

                        if (EspConfig.ores && dxzSq <= oreRangeSq) {
                            int oreType = oreType(block);
                            if (oreType >= 0) {
                                buildingOres.put(new BlockPos(x, y, z), oreType);
                            }
                        }
                        if (EspConfig.blocks && dxzSq <= blockRangeSq && isSpecialBlock(block)) {
                            buildingBlocks.add(new BlockPos(x, y, z));
                        }
                        if (EspConfig.beds && dxzSq <= bedRangeSq && block == Blocks.BED) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = world.getBlockState(pos);
                            if (state.getBlock() instanceof BedBlock
                                    && state.get(BedBlock.BED_TYPE) == BedBlock.BedBlockType.HEAD) {
                                buildingBeds.add(pos);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 扫描收尾时按床的位置查黑曜石：床的两格各向外扩 2 格，命中就记下。
     * 一张床最多 250 次查方块，床一般只有几张，比在扫描时缓存全图黑曜石便宜得多。
     */
    private static void resolveBedObsidian(World world) {
        OBSIDIAN_RESULT.clear();
        if (!EspConfig.showObsidian) {
            return;
        }
        for (BlockPos head : BED_RESULT) {
            BlockState headState = world.getBlockState(head);
            if (!(headState.getBlock() instanceof BedBlock)
                    || headState.get(BedBlock.BED_TYPE) != BedBlock.BedBlockType.HEAD) {
                continue;
            }
            BlockPos foot = head.offset(headState.get(BedBlock.FACING).getOpposite());
            collectObsidianAround(world, head);
            if (world.getBlockState(foot).getBlock() instanceof BedBlock) {
                collectObsidianAround(world, foot);
            }
        }
    }

    private static void collectObsidianAround(World world, BlockPos center) {
        for (int dy = -OBSIDIAN_SEARCH_RADIUS; dy <= OBSIDIAN_SEARCH_RADIUS; dy++) {
            for (int dz = -OBSIDIAN_SEARCH_RADIUS; dz <= OBSIDIAN_SEARCH_RADIUS; dz++) {
                for (int dx = -OBSIDIAN_SEARCH_RADIUS; dx <= OBSIDIAN_SEARCH_RADIUS; dx++) {
                    if (dx * dx + dy * dy + dz * dz > OBSIDIAN_SEARCH_RADIUS * OBSIDIAN_SEARCH_RADIUS) {
                        continue;
                    }
                    BlockPos pos = center.add(dx, dy, dz);
                    if (world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN) {
                        OBSIDIAN_RESULT.add(pos);
                    }
                }
            }
        }
    }

    /**
     * 矿种下标，顺序与 EspConfig.C_DIAMOND 起的颜色下标一致：
     * 0 钻石、1 绿宝石、2 金、3 铁、4 煤、5 红石、6 青金石、7 石英；不是矿石返回 -1。
     */
    private static int oreType(Block block) {
        if (block == Blocks.DIAMOND_ORE) {
            return 0;
        }
        if (block == Blocks.EMERALD_ORE) {
            return 1;
        }
        if (block == Blocks.GOLD_ORE) {
            return 2;
        }
        if (block == Blocks.IRON_ORE) {
            return 3;
        }
        if (block == Blocks.COAL_ORE) {
            return 4;
        }
        if (block == Blocks.REDSTONE_ORE || block == Blocks.LIT_REDSTONE_ORE) {
            return 5;
        }
        if (block == Blocks.LAPIS_LAZULI_ORE) {
            return 6;
        }
        if (block == Blocks.NETHER_QUARTZ_ORE) {
            return 7;
        }
        return -1;
    }

    private static boolean isSpecialBlock(Block block) {
        return block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.ENDERCHEST
                || block == Blocks.STANDING_SIGN
                || block == Blocks.WALL_SIGN
                || block == Blocks.STANDING_BANNER
                || block == Blocks.WALL_BANNER
                || block == Blocks.FURNACE
                || block == Blocks.LIT_FURNACE
                || block == Blocks.HOPPER
                || block == Blocks.DROPPER
                || block == Blocks.DISPENSER
                || block == Blocks.BREWING_STAND
                || block == Blocks.BEACON
                || block == Blocks.SPAWNER
                || block == Blocks.JUKEBOX
                || block == Blocks.NOTEBLOCK
                || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.ANVIL
                || block == Blocks.CAULDRON
                || block == Blocks.CAKE
                || block == Blocks.END_PORTAL_FRAME
                || block == Blocks.SKULL
                || block == Blocks.TORCH
                || block == Blocks.REDSTONE_TORCH
                || block == Blocks.UNLIT_REDSTONE_TORCH
                || block == Blocks.REDSTONE_WIRE
                || block == Blocks.LEVER
                || block == Blocks.STONE_BUTTON
                || block == Blocks.WOODEN_BUTTON
                || block == Blocks.STONE_PRESSURE_PLATE
                || block == Blocks.WOODEN_PRESSURE_PLATE
                || block == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE
                || block == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE
                || block == Blocks.RAIL
                || block == Blocks.POWERED_RAIL
                || block == Blocks.DETECTOR_RAIL
                || block == Blocks.ACTIVATOR_RAIL;
    }
}
