package net.amethystesp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * 透视开关与颜色。开关和颜色都由 Amethyst Client设置界面里的「透视」分类控制，
 * 这里只负责读写 config/amethystesp.properties，保证重进游戏后设置还在。
 *
 * <p>颜色按「被透视对象」细分：实体分和平/中立/攻击三类，方块分箱子/机关箱/其它三类，
 * 矿石按矿种一种一个颜色，另有床、床周围黑曜石、隐身玩家三个独立颜色。
 */
public final class EspConfig {

    /** 透视实体。 */
    public static boolean entities = false;
    /** 透视实体方块。 */
    public static boolean blocks = false;
    /** 透视资源（矿石）。 */
    public static boolean ores = false;
    /** 透视床（只认 HEAD 半块，框覆盖整张床）。 */
    public static boolean beds = false;
    /** 床周围黑曜石（床卡里的子开关）。 */
    public static boolean showObsidian = true;
    /** 反隐身：把隐身玩家/生物半透明画出来。 */
    public static boolean antiInvis = false;

    /* 颜色下标：渲染和设置界面都用这套下标，避免到处写 key。 */
    public static final int C_PEACEFUL = 0;    // 和平生物（猪牛羊鸡等）：白
    public static final int C_NEUTRAL = 1;     // 中立生物（铁傀儡、狼、末影人、僵尸猪人）：黄
    public static final int C_HOSTILE = 2;     // 攻击生物（僵尸、苦力怕等）与玩家：红
    public static final int C_CHEST = 3;       // 箱子：黄
    public static final int C_TRAP = 4;        // 陷阱箱：橙
    public static final int C_OTHER_BLOCK = 5; // 其它方块（头颅、火把、告示牌等）：白
    public static final int C_DIAMOND = 6;     // 钻石：蓝
    public static final int C_EMERALD = 7;     // 绿宝石：绿
    public static final int C_GOLD = 8;        // 金：金黄
    public static final int C_IRON = 9;        // 铁：浅灰
    public static final int C_COAL = 10;       // 煤：黑
    public static final int C_REDSTONE = 11;   // 红石：红
    public static final int C_LAPIS = 12;      // 青金石：深蓝
    public static final int C_QUARTZ = 13;     // 石英：白
    public static final int C_BED = 14;        // 床：红
    public static final int C_OBSIDIAN = 15;   // 床周围黑曜石：紫
    public static final int C_INVIS = 16;      // 隐身玩家：青

    public static final int COLOR_COUNT = 17;

    private static final int[] COLORS = new int[COLOR_COUNT];

    private static final String[] COLOR_KEYS = {
            "colorPeacefulMob", "colorNeutralMob", "colorHostileMob",
            "colorChest", "colorTrappedChest", "colorOtherBlock",
            "colorDiamondOre", "colorEmeraldOre", "colorGoldOre", "colorIronOre",
            "colorCoalOre", "colorRedstoneOre", "colorLapisOre", "colorQuartzOre",
            "colorBed", "colorObsidian", "colorInvisible"
    };

    static {
        COLORS[C_PEACEFUL] = 0xFFFFFFFF;
        COLORS[C_NEUTRAL] = 0xFFFFD24B;
        COLORS[C_HOSTILE] = 0xFFFF4747;
        COLORS[C_CHEST] = 0xFFFFC62E;
        COLORS[C_TRAP] = 0xFFFF8C1A;
        COLORS[C_OTHER_BLOCK] = 0xFFFFFFFF;
        COLORS[C_DIAMOND] = 0xFF3FA0FF;
        COLORS[C_EMERALD] = 0xFF2FE06A;
        COLORS[C_GOLD] = 0xFFFFD22E;
        COLORS[C_IRON] = 0xFFE0E0E0;
        COLORS[C_COAL] = 0xFF333333;
        COLORS[C_REDSTONE] = 0xFFFF3B30;
        COLORS[C_LAPIS] = 0xFF3D5AFE;
        COLORS[C_QUARTZ] = 0xFFF5F5F5;
        COLORS[C_BED] = 0xFFFF5252;
        COLORS[C_OBSIDIAN] = 0xFFAA00AA;
        COLORS[C_INVIS] = 0xFF00E5FF;
    }

    /** 实体显示距离（方块）。 */
    public static int entityRange = 192;
    /** 实体方块显示距离（方块），只影响已加载区块。 */
    public static int blockRange = 96;
    /** 矿石显示距离（方块），只影响已加载区块。 */
    public static int oreRange = 64;
    /** 床与床头黑曜石显示距离（方块），只影响已加载区块。 */
    public static int bedRange = 64;
    /** 方框填充透明度，0 = 只画描边。 */
    public static float fillAlpha = 0.18f;
    /** 反隐身时模型的整体不透明度（1 = 完全不透明）。 */
    public static float invisAlpha = 0.5f;

    /**
     * 扫描半径上限。区块扫描按 16 格一列、每 tick 3 列推进，范围再放大会让队列堆到几千个区块，
     * 所以统一按 8 区块（128 格）截断；配置里写更大也会静默压到 128。
     */
    public static final int MAX_RANGE = 128;

    private static final String FILE_NAME = "amethystesp.properties";
    private static File configFile;

    /* 拖取色器时颜色每帧都会变，落盘用 400ms 防抖，避免一直写磁盘。 */
    private static boolean dirty;
    private static long lastChange;
    private static final long SAVE_DEBOUNCE_MS = 400L;

    private EspConfig() {
    }

    public static int color(int index) {
        return index >= 0 && index < COLOR_COUNT ? COLORS[index] : 0xFFFFFFFF;
    }

    public static void setColor(int index, int argb) {
        if (index >= 0 && index < COLOR_COUNT) {
            COLORS[index] = argb;
            requestSave();
        }
    }

    /** 标记配置已变，由 tick 的 {@link #flushIfDirty()} 统一落盘。 */
    public static void requestSave() {
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    /** 每 tick 调用：改动后静置超过防抖时间就写一次。 */
    public static void flushIfDirty() {
        if (dirty && System.currentTimeMillis() - lastChange >= SAVE_DEBOUNCE_MS) {
            save();
        }
    }

    public static void load(File gameDir) {
        File dir = new File(gameDir, "config");
        configFile = new File(dir, FILE_NAME);

        // 改名后第一次启动：读旧的 legacyesp.properties，再按新文件名写回去
        File legacy = new File(dir, "legacyesp.properties");
        File readFrom = configFile.isFile() || !legacy.isFile() ? configFile : legacy;

        Properties props = new Properties();
        if (readFrom.isFile()) {
            try (InputStream in = new FileInputStream(readFrom)) {
                props.load(in);
            } catch (IOException e) {
                AmethystEspAddon.LOGGER.warn("[AmethystESP] failed to read config", e);
            }
        }

        // 兼容旧键名：1.1.0 的 players / chests，1.2.0-1.4.0 的统一三色。
        entities = getBoolean(props, "entities", getBoolean(props, "players", entities));
        blocks = getBoolean(props, "blocks", getBoolean(props, "chests", blocks));
        ores = getBoolean(props, "ores", ores);
        beds = getBoolean(props, "beds", beds);
        showObsidian = getBoolean(props, "showObsidian", showObsidian);
        antiInvis = getBoolean(props, "antiInvis", antiInvis);

        for (int i = 0; i < COLOR_COUNT; i++) {
            COLORS[i] = getColor(props, COLOR_KEYS[i], COLORS[i]);
        }
        if (!props.containsKey(COLOR_KEYS[C_HOSTILE])) {
            COLORS[C_HOSTILE] = getColor(props, "entityColor", COLORS[C_HOSTILE]);
        }
        if (!props.containsKey(COLOR_KEYS[C_CHEST])) {
            COLORS[C_CHEST] = getColor(props, "blockColor", COLORS[C_CHEST]);
        }
        if (!props.containsKey(COLOR_KEYS[C_DIAMOND])) {
            COLORS[C_DIAMOND] = getColor(props, "oreColor", COLORS[C_DIAMOND]);
        }

        // 实体不走区块扫描，直接从已加载实体列表里取，可以把范围放宽到 256
        entityRange = clamp(getInt(props, "entityRange", entityRange), 8, 256);
        blockRange = clamp(getInt(props, "blockRange", blockRange), 8, MAX_RANGE);
        oreRange = clamp(getInt(props, "oreRange", oreRange), 8, MAX_RANGE);
        bedRange = clamp(getInt(props, "bedRange", bedRange), 8, MAX_RANGE);
        fillAlpha = clamp(getFloat(props, "fillAlpha", fillAlpha), 0.0f, 1.0f);
        invisAlpha = clamp(getFloat(props, "invisAlpha", invisAlpha), 0.05f, 1.0f);

        if (!configFile.isFile()) {
            save();
        }
    }

    public static void save() {
        dirty = false;
        if (configFile == null) {
            return;
        }
        File dir = configFile.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            AmethystEspAddon.LOGGER.warn("[AmethystESP] could not create config directory {}", dir);
            return;
        }

        Properties props = new Properties();
        props.setProperty("entities", Boolean.toString(entities));
        props.setProperty("blocks", Boolean.toString(blocks));
        props.setProperty("ores", Boolean.toString(ores));
        props.setProperty("beds", Boolean.toString(beds));
        props.setProperty("showObsidian", Boolean.toString(showObsidian));
        props.setProperty("antiInvis", Boolean.toString(antiInvis));
        for (int i = 0; i < COLOR_COUNT; i++) {
            props.setProperty(COLOR_KEYS[i], String.format("%08X", COLORS[i]));
        }
        props.setProperty("entityRange", Integer.toString(entityRange));
        props.setProperty("blockRange", Integer.toString(blockRange));
        props.setProperty("oreRange", Integer.toString(oreRange));
        props.setProperty("bedRange", Integer.toString(bedRange));
        props.setProperty("fillAlpha", Float.toString(fillAlpha));
        props.setProperty("invisAlpha", Float.toString(invisAlpha));

        try (OutputStream out = new FileOutputStream(configFile)) {
            props.store(out, "AmethystESP settings (toggled from Amethyst Client settings)");
        } catch (IOException e) {
            AmethystEspAddon.LOGGER.warn("[AmethystESP] failed to write config", e);
        }
    }

    /** 方框类透视是否开着（反隐身走实体渲染管线，不在这里）。 */
    public static boolean anyEnabled() {
        return entities || blocks || ores || beds;
    }

    private static boolean getBoolean(Properties props, String key, boolean def) {
        String value = props.getProperty(key);
        return value == null ? def : Boolean.parseBoolean(value.trim());
    }

    private static int getInt(Properties props, String key, int def) {
        String value = props.getProperty(key);
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int getColor(Properties props, String key, int def) {
        String value = props.getProperty(key);
        if (value == null) {
            return def;
        }
        try {
            String hex = value.trim();
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            int parsed = (int) Long.parseLong(hex, 16);
            // 老版本写的是 6 位 RGB（entityColor=FF4747 这种），没有 alpha 位会变成全透明，补成不透明。
            return hex.length() <= 6 ? (parsed | 0xFF000000) : parsed;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float getFloat(Properties props, String key, float def) {
        String value = props.getProperty(key);
        if (value == null) {
            return def;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
