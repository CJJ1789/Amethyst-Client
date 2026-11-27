package net.amethystesp;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 把「透视」分类接进 Amethyst Client 1.0.2_24 的设置界面。
 *
 * <p>这一版客户端把设置拆成了两条通路：
 * <ul>
 *   <li>界面结构：{@code ClientScreen#buildGroups()} 按 {@code SettingsSpec.ROWS} 拼出
 *       {@code Group / Unit / Row}，绘制、滚动、点击全部按这三个类的字段走；</li>
 *   <li>取值：行的开关、颜色、数值统一走 {@code net.client.settings.SettingsValues} 的静态方法（按 key）。</li>
 * </ul>
 *
 * <p>所以本模组不去动客户端的 {@code ROWS} / {@code LABELS}（两者都是定长表，
 * 往里加行会撑爆它自己的 validate 自检和 A→Z 重排），而是：
 * buildGroups 末尾往 {@code groups} 里插一个 id=216 的分类，行 key 从 130 起占用客户端没用过的空档，
 * 文案由 label 的 HEAD 注入提供，开关/颜色由 SettingsValues 的 HEAD 注入接管。
 *
 * <p>客户端已有的 key 最大到 116（{@code LABELS} 的长度），130 起的这一段它完全不认识，
 * 不会和任何现成设置撞车。
 */
public final class AmethystSettings {

    /** 客户端的分组 id 是 200–226，216 目前没有使用（{@code GROUP_NAME_*} 里是空串）。 */
    public static final int GROUP_ID = 216;

    // ---- 开关 key：130–135 ----
    public static final int KEY_ENTITIES = 130;
    public static final int KEY_BLOCKS = 131;
    public static final int KEY_ORES = 132;
    public static final int KEY_BEDS = 133;
    public static final int KEY_OBSIDIAN = 134;
    public static final int KEY_ANTI_INVIS = 135;

    /** 颜色行 key：136 起，连续 {@link EspConfig#COLOR_COUNT} 个。 */
    public static final int FIRST_COLOR_KEY = 136;

    /** 行类型，和客户端 {@code SettingsSpec.K_TOGGLE / K_COLOR} 一致。 */
    private static final int KIND_TOGGLE = 0;
    private static final int KIND_COLOR = 2;

    /** 下标 = key - {@link #KEY_ENTITIES}；[0] 中文、[1] 英文。 */
    private static final String[][] LABELS = {
            {"实体透视", "ESP Entities"},
            {"方块实体透视", "ESP Block Entities"},
            {"资源透视", "ESP Ores"},
            {"床透视", "ESP Beds"},
            {"床周黑曜石", "Bed Obsidian"},
            {"反隐身", "Anti-Invis"},

            {"和平生物", "Peaceful mobs"},
            {"中立生物", "Neutral mobs"},
            {"敌对生物与玩家", "Hostile mobs & players"},

            {"箱子", "Chests"},
            {"陷阱箱", "Trapped chests"},
            {"其它方块", "Other blocks"},

            {"钻石矿", "Diamond ore"},
            {"绿宝石矿", "Emerald ore"},
            {"金矿", "Gold ore"},
            {"铁矿", "Iron ore"},
            {"煤矿", "Coal ore"},
            {"红石矿", "Redstone ore"},
            {"青金石矿", "Lapis ore"},
            {"下界石英矿", "Nether quartz"},

            {"床", "Beds"},
            {"黑曜石", "Obsidian"},
            {"隐身玩家", "Invisible players"}
    };

    private static boolean resolved;
    private static boolean failedLogged;

    private static Field groupsField;
    private static Field groupUnitsField;
    private static Field groupIdField;
    private static Field unitRowsField;
    private static Constructor<?> groupCtor;
    private static Constructor<?> unitCtor;
    private static Constructor<?> rowCtor;
    private static Method groupNameEn;

    private static Method langIndexMethod;

    private AmethystSettings() {
    }

    /** 是否是本模组的开关 key；不是返回 null。 */
    public static Boolean toggleState(int key) {
        if (key == KEY_ENTITIES) {
            return EspConfig.entities;
        }
        if (key == KEY_BLOCKS) {
            return EspConfig.blocks;
        }
        if (key == KEY_ORES) {
            return EspConfig.ores;
        }
        if (key == KEY_BEDS) {
            return EspConfig.beds;
        }
        if (key == KEY_OBSIDIAN) {
            return EspConfig.showObsidian;
        }
        if (key == KEY_ANTI_INVIS) {
            return EspConfig.antiInvis;
        }
        return null;
    }

    /** 翻转本模组的开关；不是本模组的 key 返回 false。 */
    public static boolean toggle(int key) {
        if (key == KEY_ENTITIES) {
            EspConfig.entities = !EspConfig.entities;
            return true;
        }
        if (key == KEY_BLOCKS) {
            EspConfig.blocks = !EspConfig.blocks;
            return true;
        }
        if (key == KEY_ORES) {
            EspConfig.ores = !EspConfig.ores;
            return true;
        }
        if (key == KEY_BEDS) {
            EspConfig.beds = !EspConfig.beds;
            return true;
        }
        if (key == KEY_OBSIDIAN) {
            EspConfig.showObsidian = !EspConfig.showObsidian;
            return true;
        }
        if (key == KEY_ANTI_INVIS) {
            EspConfig.antiInvis = !EspConfig.antiInvis;
            return true;
        }
        return false;
    }

    /** 颜色行 key → EspConfig 的颜色下标；不是本模组的 key 返回 -1。 */
    public static int colorIndex(int key) {
        int index = key - FIRST_COLOR_KEY;
        return index >= 0 && index < EspConfig.COLOR_COUNT ? index : -1;
    }

    /** 行文案；不是本模组的 key 返回 null（让客户端照常查它自己的 LABELS）。 */
    public static String labelFor(int key) {
        int index = key - KEY_ENTITIES;
        if (index < 0 || index >= LABELS.length) {
            return null;
        }
        return LABELS[index][langIndex()];
    }

    /** 0 = 中文，1 = 英文，跟客户端的界面语言设置一致。 */
    private static int langIndex() {
        try {
            if (langIndexMethod == null) {
                langIndexMethod = Class.forName("net.client.ClientConfig").getMethod("langIndex");
            }
            Object value = langIndexMethod.invoke(null);
            return value instanceof Integer ? ((Integer) value).intValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 由 {@code ClientScreen#buildGroups} 的 TAIL 注入调用。 */
    public static void addGroup(Object screen) {
        try {
            if (!resolve()) {
                return;
            }
            installGroupName();
            List<Object> groups = castList(groupsField.get(screen));
            if (groups == null) {
                return;
            }
            for (Object existing : groups) {
                if (groupId(existing) == GROUP_ID) {
                    return;
                }
            }
            Object group = groupCtor.newInstance(GROUP_ID);
            List<Object> units = castList(groupUnitsField.get(group));
            if (units == null) {
                return;
            }
            addCard(units, KEY_ENTITIES, EspConfig.C_PEACEFUL, EspConfig.C_NEUTRAL, EspConfig.C_HOSTILE);
            addCard(units, KEY_BLOCKS, EspConfig.C_CHEST, EspConfig.C_TRAP, EspConfig.C_OTHER_BLOCK);
            addCard(units, KEY_ORES, EspConfig.C_DIAMOND, EspConfig.C_EMERALD, EspConfig.C_GOLD,
                    EspConfig.C_IRON, EspConfig.C_COAL, EspConfig.C_REDSTONE, EspConfig.C_LAPIS,
                    EspConfig.C_QUARTZ);
            addCard(units, KEY_BEDS, EspConfig.C_BED);
            addCard(units, KEY_OBSIDIAN, EspConfig.C_OBSIDIAN);
            addCard(units, KEY_ANTI_INVIS, EspConfig.C_INVIS);
            groups.add(insertIndex(groups), group);
        } catch (Throwable t) {
            if (!failedLogged) {
                failedLogged = true;
                AmethystEspAddon.LOGGER.error("[AmethystESP] failed to attach ESP group to client settings", t);
            }
        }
    }

    /**
     * 客户端把分类按英文名 A→Z 排好了，我们插在「ESP」该在的位置，
     * 而不是无脑丢到列表末尾，免得界面里凭空多出一张乱序的卡片。
     */
    private static int insertIndex(List<Object> groups) {
        for (int i = 0; i < groups.size(); i++) {
            try {
                Object name = groupNameEn.invoke(groups.get(i));
                if (name instanceof String && ((String) name).compareToIgnoreCase("ESP") > 0) {
                    return i;
                }
            } catch (Throwable ignored) {
                // 读不到名字就当它排在后面
            }
        }
        return groups.size();
    }

    private static void addCard(List<Object> units, int toggleKey, int... colorIndexes) throws Exception {
        Object head = rowCtor.newInstance(KIND_TOGGLE, toggleKey);
        Object unit = unitCtor.newInstance(head);
        List<Object> rows = castList(unitRowsField.get(unit));
        if (rows == null) {
            return;
        }
        for (int colorIndex : colorIndexes) {
            rows.add(rowCtor.newInstance(KIND_COLOR, FIRST_COLOR_KEY + colorIndex));
        }
        units.add(unit);
    }

    private static int groupId(Object group) {
        try {
            return ((Integer) groupIdField.get(group)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 客户端分类名放在 {@code SettingsSpec.GROUP_NAME_ZH/EN} 两个数组里（下标 = gid - 200）。
     * 字段是 static final 不能替换，但数组元素是可写的，所以直接写 216 那一格。
     */
    private static void installGroupName() {
        try {
            Class<?> spec = Class.forName("net.client.settings.SettingsSpec");
            String[] zh = (String[]) spec.getField("GROUP_NAME_ZH").get(null);
            String[] en = (String[]) spec.getField("GROUP_NAME_EN").get(null);
            int index = GROUP_ID - 200;
            if (zh != null && index >= 0 && index < zh.length) {
                zh[index] = "透视";
            }
            if (en != null && index >= 0 && index < en.length) {
                en[index] = "ESP";
            }
        } catch (Throwable t) {
            if (!failedLogged) {
                failedLogged = true;
                AmethystEspAddon.LOGGER.error("[AmethystESP] failed to install ESP category name", t);
            }
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return true;
        }
        try {
            Class<?> screen = Class.forName("net.client.ClientScreen");
            Class<?> group = Class.forName("net.client.ClientScreen$Group");
            Class<?> unit = Class.forName("net.client.ClientScreen$Unit");
            Class<?> row = Class.forName("net.client.ClientScreen$Row");

            groupsField = accessible(screen.getDeclaredField("groups"));
            groupUnitsField = accessible(group.getDeclaredField("units"));
            groupIdField = accessible(group.getDeclaredField("id"));
            unitRowsField = accessible(unit.getDeclaredField("rows"));

            groupNameEn = group.getDeclaredMethod("nameEn");
            groupNameEn.setAccessible(true);

            groupCtor = accessible(group.getDeclaredConstructor(int.class));
            unitCtor = accessible(unit.getDeclaredConstructor(row));
            rowCtor = accessible(row.getDeclaredConstructor(int.class, int.class));

            resolved = true;
            return true;
        } catch (Throwable t) {
            if (!failedLogged) {
                failedLogged = true;
                AmethystEspAddon.LOGGER.error("[AmethystESP] client settings layout not resolvable", t);
            }
            return false;
        }
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }

    private static Constructor<?> accessible(Constructor<?> ctor) {
        ctor.setAccessible(true);
        return ctor;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }
}
