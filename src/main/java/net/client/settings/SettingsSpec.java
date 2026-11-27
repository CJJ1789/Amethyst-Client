package net.client.settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SettingsSpec {
   public static final int K_HEADER = -1;
   public static final int K_TOGGLE = 0;
   public static final int K_VALUE = 1;
   public static final int K_COLOR = 2;
   public static final int K_CYCLE = 3;
   /** 1.0.1_3：危险动作（重置设置）单独一种行类型，别再和 K_CYCLE 抢 3。 */
   public static final int K_RESET = 4;
   public static final int K_ACTION = 5;
   public static final int FOV_ON = 6;
   public static final int FOV_BASE = 7;
   /** 1.0.2：FOV 子项的键值以前散在 ClientScreen 里当裸数字用，这里补成具名常量。 */
   public static final int FOV_SMOOTH = 84;
   public static final int FOV_GLOBAL = 85;
   public static final int FOV_SPRINT_ON = 86;
   public static final int FOV_SPRINT = 87;
   public static final int FOV_SPEED_ON = 88;
   public static final int FOV_SPEED = 89;
   public static final int FOV_SLOW_ON = 90;
   public static final int FOV_SLOW = 91;
   public static final int FOV_FLY_ON = 92;
   public static final int FOV_FLY = 93;
   public static final int FOV_AIM_ON = 94;
   public static final int FOV_AIM = 95;
   public static final int FOV_WATER_ON = 96;
   public static final int FOV_WATER = 97;
   public static final int GLINT_ON = 14;
   public static final int GLINT_COLOR = 15;
   public static final int FOG_ON = 16;
   public static final int FOG_COLOR = 17;
   public static final int FOG_DENSITY = 19;
   public static final int FOG_SKY = 18;
   public static final int HL_ON = 20;
   public static final int HL_FILL = 21;
   public static final int HL_OUTLINE = 22;
   public static final int NAME_ON = 23;
   public static final int NAME_BG = 24;
   public static final int NAME_TEXT = 25;
   public static final int CHAT_ON = 26;
   public static final int CHAT_COLOR = 27;
   public static final int SB_ON = 28;
   public static final int SB_COLOR = 29;
   public static final int WEATHER = 30;
   public static final int WEATHER_ROOF = 31;
   public static final int RAW_INPUT = 32;
   public static final int TIME_ON = 35;
   public static final int TIME_TICKS = 36;
   public static final int BLUR_ON = 37;
   public static final int BLUR_AMOUNT = 38;
   public static final int HURT_ON = 39;
   public static final int HURT_COLOR = 40;
   public static final int DROP_ON = 42;
   public static final int CROSSHAIR_ON = 43;
   public static final int CROSSHAIR_COLOR = 44;
   public static final int CROSSHAIR_EDIT = 45;
   public static final int NO_HURT = 46;
   public static final int FULLBRIGHT = 47;
   public static final int MIN_BOB = 48;
   public static final int AUTO_SPRINT = 49;
   public static final int FREELOOK_ON = 63;
   public static final int FREELOOK_KEY = 64;
   public static final int DROP_REST = 65;
   public static final int UI_OVERRIDE = 66;
   public static final int THEME_COLOR = 103;
   public static final int MODERN_FONT = 104;
   /** 1.0.1：玻璃显示模式（关闭 / 全部玻璃 / 仅未染色玻璃）。 */
   public static final int GLASS_MODE = 110;
   public static final int MOD_LIST = 69;
   public static final int CLICK_NODELAY = 70;
   public static final int HIDE_PARTICLES = 71;
   public static final int FIRE_HEIGHT = 72;
   public static final int SCREEN_DIM = 50;
   public static final int CHAT_VISIBLE = 52;
   public static final int SCORE_VISIBLE = 53;
   public static final int SCORE_NO_NUMBERS = 54;
   /** 1.0.1：自定义计分板内容开关 + 编辑入口。 */
   public static final int SCORE_CUSTOM = 105;
   public static final int SCORE_EDIT = 106;
   public static final int HAND_FWD = 73;
   public static final int HAND_RIGHT = 74;
   public static final int HAND_UP = 75;
   /** 1.0.1：第一人称左手主手（仅视觉）。 */
   public static final int LEFT_HANDED = 109;
   public static final int PARTICLE_ON = 76;
   public static final int CRIT_MULT = 77;
   public static final int SHARP_MULT = 78;
   /** 1.0.1 新增：中文输入法修复开关（Windows / IMM32）。 */
   public static final int IME_FIX = 80;
   /** 1.0.1_2 新增：重置所有客户端设置（底部独立按钮行）。 */
   public static final int RESET_SETTINGS = 81;
   /** 1.0.1_9 新增：挖掘粒子 / 脚步粒子开关。 */
   public static final int MINING_PARTICLES = 82;
   public static final int SPRINT_PARTICLES = 83;
   // 1.0.1_29：attack-feel（原 98）不再是设置项——后台常开，界面上已无开关，key 98 空出来不再使用。
   public static final int BLUR_STYLE = 102;
   public static final int BLOCKHIT_ON = 55;
   /** 1.0.1：受击时血条闪烁（默认关闭，血条不再抖动/闪烁）。 */
   public static final int HEART_BLINK = 111;
   // ---- 1.0.2_1：设置面板不透明度 ----
   // 1.0.2_2：key 112–115 重新接回面板（淡入时长 / 渐变起止色 / 模糊聊天）。
   // 之前当成「Blur 配置页已经能改的重复项」删掉了，用户要求 Blur 的选项在客户端里改全。
   public static final int FADE_TIME = 112;
   public static final int GRADIENT_START = 113;
   public static final int GRADIENT_END = 114;
   public static final int BLUR_CHAT = 115;
   public static final int PANEL_OPACITY = 116;
   public static final int AF_ON = 56;
   public static final int AF_COLOR = 57;
   public static final int AF_LIFE = 58;
   public static final int AF_STR = 59;
   public static final int AF_LAYERS = 60;
   public static final int AF_SELF = 61;
   public static final int G_SCREEN = 220;
   public static final int G_HAND = 223;
   public static final int G_PARTICLES = 225;
   public static final int G_GLASS = 226;
   public static final int G_SWING = 200;
   public static final int G_HURT = 201;
   public static final int G_GAMMA = 202;
   public static final int G_BOB = 203;
   public static final int G_SPRINT = 204;
   public static final int G_FOV = 205;
   public static final int G_GLINT = 206;
   public static final int G_FOG = 207;
   public static final int G_HL = 208;
   public static final int G_NAME = 209;
   public static final int G_CHAT = 210;
   public static final int G_SB = 211;
   public static final int G_WEATHER = 212;
   public static final int G_TIME = 213;
   public static final int G_BLUR = 215;
   public static final int G_MOUSE = 214;
   public static final int G_DROP = 218;
   public static final int G_CROSSHAIR = 219;
   public static final int G_BLOCKHIT = 221;
   public static final int G_AFTERIMAGE = 222;
   public static final int G_FREELOOK = 224;
   public static final int GROUPS = 27;
   public static final int[][] ROWS = new int[][]{
      {-1, -1, 221},
      {0, 221, 55},
      {0, 221, 111},
      {1, 221, 72},
      {-1, -1, 222},
      {0, 222, 56},
      {2, 222, 57},
      {1, 222, 58},
      {1, 222, 59},
      {1, 222, 60},
      {0, 222, 61},
      {-1, -1, 201},
      {0, 201, 46},
      {0, 201, 39},
      {2, 201, 40},
      {-1, -1, 202},
      {0, 202, 47},
      {-1, -1, 203},
      {0, 203, 48},
      {-1, -1, 214},
      {0, 214, 32},
      {-1, -1, 204},
      {0, 204, 49},
      {-1, -1, 205},
      {0, 205, 6},
      {1, 205, 7},
      {0, 205, 84},
      {1, 205, 85},
      {0, 205, 86},
      {1, 205, 87},
      {0, 205, 88},
      {1, 205, 89},
      {0, 205, 90},
      {1, 205, 91},
      {0, 205, 92},
      {1, 205, 93},
      {0, 205, 94},
      {1, 205, 95},
      {0, 205, 96},
      {1, 205, 97},
      {-1, -1, 206},
      {0, 206, 14},
      {2, 206, 15},
      {-1, -1, 207},
      {0, 207, 16},
      {2, 207, 17},
      {1, 207, 19},
      {-1, -1, 208},
      {0, 208, 20},
      {2, 208, 21},
      {2, 208, 22},
      {-1, -1, 209},
      {0, 209, 23},
      {2, 209, 24},
      {2, 209, 25},
      {-1, -1, 210},
      {0, 210, 52},
      {0, 210, 26},
      {2, 210, 27},
      {0, 210, 80},
      {-1, -1, 211},
      {0, 211, 53},
      {0, 211, 54},
      {0, 211, 28},
      {2, 211, 29},
      {5, 211, 106},
      {-1, -1, 212},
      {3, 212, 30},
      {0, 212, 31},
      {-1, -1, 213},
      {0, 213, 35},
      {1, 213, 36},
      {-1, -1, 215},
      {0, 215, 37},
      {1, 215, 38},
      {3, 215, 102},
      {-1, -1, 218},
      {0, 218, 42},
      {0, 218, 65},
      {-1, -1, 220},
      {2, 220, 103},
      {1, 220, 50},
      {1, 220, 116},
      {1, 220, 112},
      {2, 220, 113},
      {2, 220, 114},
      {0, 220, 115},
      {0, 220, 104},
      {0, 220, 66},
      {-1, -1, 219},
      {0, 219, 43},
      {2, 219, 44},
      {5, 219, 45},
      {-1, -1, 223},
      {0, 223, 109},
      {1, 223, 73},
      {1, 223, 74},
      {1, 223, 75},
      {-1, -1, 224},
      {0, 224, 63},
      {5, 224, 64},
      {-1, -1, 225},
      {0, 225, 76},
      {1, 225, 77},
      {1, 225, 78},
      {0, 225, 71},
      {0, 225, 82},
      {0, 225, 83},
      {-1, -1, 226},
      {3, 226, 110}
   };
   /**
    * 1.0.2_2：ROWS 里每一行是不是「一个设置项的第一行」。
    * 排序会把行按英文名重排，重排后「紧跟上一个开关」这种位置判断会误把两个无关的项
    * 粘成一张卡（比如 模糊聊天界面 + 模糊渐入时长）。这里在切分阶段就把结果记下来，
    * 界面按标记切，不再靠位置猜。
    */
   public static final boolean[] UNIT_HEAD = new boolean[ROWS.length];
   public static final String[] GROUP_NAME_ZH = new String[27];
   public static final String[] GROUP_NAME_EN = new String[27];
   public static final String[][] LABELS = new String[][]{
      {"", ""},
      {"受击无抖动", "No hurt camera shake"},
      {"伽马满亮度", "Gamma fullbright"},
      {"最小视角摇晃", "Minimal view bobbing"},
      {"自动疾跑", "Auto sprint"},
      {"客户端 FOV", "Client FOV"},
      {"默认 FOV", "Default FOV"},
      {"疾跑强度", "Sprint strength"},
      {"速度药水强度", "Speed strength"},
      {"迟缓强度", "Slowness strength"},
      {"飞行强度", "Flying strength"},
      {"拉弓强度", "Aiming strength"},
      {"水下强度", "Submerged strength"},
      {"自定义附魔光效", "Custom glint"},
      {"光效颜色", "Glint color"},
      {"自定义迷雾", "Custom fog"},
      {"迷雾颜色", "Fog color"},
      {"", ""},
      {"迷雾浓度", "Fog density"},
      {"准星方块高亮", "Block highlight"},
      {"高亮填充色", "Highlight fill"},
      {"高亮描边色", "Highlight outline"},
      {"自己名字标签", "Self name tag"},
      {"名字背景色", "Name background"},
      {"名字文字色", "Name text"},
      {"自定义聊天框背景", "Chat background"},
      {"聊天框颜色", "Chat color"},
      {"自定义计分板背景", "Scoreboard background"},
      {"计分板背景颜色", "Scoreboard background color"},
      {"天气", "Weather"},
      {"天气可穿过头顶方块", "Weather through roofs"},
      {"线性鼠标灵敏度（仅限电脑端）", "Linear mouse sensitivity (desktop only)"},
      {"", ""},
      {"界面语言", "Language"},
      {"覆盖世界时间（仅画面）", "Override world time (visual)"},
      {"时间点", "Time of day "},
      {"动态模糊", "Motion blur"},
      {"模糊强度", "Blur strength"},
      {"自定义受击颜色", "Custom hurt color"},
      {"受击颜色", "Hurt color"},
      {"", ""},
      {"真实掉落物", "Realistic 3D item drops"},
      {"自定义准星", "Custom crosshair"},
      {"准星颜色", "Crosshair color"},
      {"编辑准星", "Edit crosshair "},
      {"受击无抖动", "No hurt camera shake"},
      {"伽马满亮度", "Gamma fullbright"},
      {"最小视角摇晃", "Minimal view bobbing"},
      {"自动疾跑", "Auto sprint"},
      {"背景模糊强度", "Backdrop blur strength"},
      {"", ""},
      {"隐藏聊天栏", "Hide chat"},
      {"隐藏计分板", "Hide scoreboard"},
      {"隐藏计分板红色数字", "Hide red score numbers"},
      {"旧动画", "Old animations"},
      {"受击残影", "Hit afterimage"},
      {"残影颜色", "Afterimage color"},
      {"残影持续时间", "Afterimage duration "},
      {"残影不透明度", "Afterimage opacity"},
      {"柔光层数", "Glow layers"},
      {"自己也留下残影", "Include yourself"},
      {"", ""},
      {"自由视角", "Freelook "},
      {"绑定按键", "Bind key "},
      {"落地后完全静止", "Rest fixed on ground "},
      {"接管原版界面", "Override vanilla UI"},
      {"", ""}, // key 67 was the old smooth-font pack; removed in the MD3 rebuild.
      {"", ""}, // theme is controlled by the app-bar toggle now.
      {"已安装模组", "Installed mods"},
      {"", ""},
      {"隐藏自身粒子", "Hide own particles"},
      {"第一人称火焰高度", "First-person fire height"},
      {"前后偏移", "Forward / back offset"},
      {"左右偏移", "Left / right offset"},
      {"上下偏移", "Up / down offset"},
      {"自定义粒子", "Custom particles"},
      {"暴击粒子倍率", "Crit particle multiplier"},
      {"锋利粒子倍率", "Sharpness particle multiplier"},
      {"", ""},
      {"中文输入法修复（仅限电脑端）", "Chinese IME fix (desktop only)"},
      {"重置所有设置", "Reset all settings"},
      {"隐藏挖掘粒子", "Hide mining particles"},
      {"隐藏脚下粒子", "Hide footstep particles"},
      {"平滑过渡", "Smooth transition"},
      {"整体倍率", "Overall multiplier"},
      {"疾跑 FOV 开关", "Sprint FOV"},
      {"疾跑 FOV", "Sprint FOV value"},
      {"速度 FOV 开关", "Speed FOV"},
      {"速度 FOV", "Speed FOV value"},
      {"迟缓 FOV 开关", "Slowness FOV"},
      {"迟缓 FOV", "Slowness FOV value"},
      {"飞行 FOV 开关", "Flying FOV"},
      {"飞行 FOV", "Flying FOV value"},
      {"瞄准 FOV 开关", "Aiming FOV"},
      {"瞄准 FOV", "Aiming FOV value"},
      {"水下 FOV 开关", "Underwater FOV"},
      {"水下 FOV", "Underwater FOV value"},
      // 1.0.1_29：key 98（攻击手感优化）的界面开关已移除，这条文案保留占位以维持
      // LABELS 与 key 的一一对应（LABELS[key - 1]），后续 key 不要再往前挪。
      {"攻击手感优化", "Attack feel"},
      {"", ""},
      {"", ""},
      {"", ""},
      {"动态模糊样式", "Blur style"},
      {"主题色", "Theme color"},
      {"现代字体", "Modern font"},
      {"自定义计分板内容", "Custom scoreboard content"},
      {"编辑计分板内容", "Edit scoreboard content"},
      {"", ""},
      {"", ""},
      {"左手模式", "Left hand"},
      {"隐藏玻璃", "Hide glass"},
      // key 111
      {"关闭受击闪烁", "No hurt blink"},
      // key 112~115：Blur 自己的参数，1.0.2_2 起接回客户端设置。
      {"模糊渐入时长", "Blur fade time"},
      {"渐变起始色", "Gradient start"},
      {"渐变结束色", "Gradient end"},
      {"模糊聊天界面", "Blur chat"},
      // key 116：客户端设置自己的背景不透明度
      {"背景不透明度", "Panel opacity"},
   };
   public static final String[][] WEATHER_NAMES = new String[][]{{"跟随", "Follow"}, {"晴", "Clear"}, {"雨", "Rain"}, {"雷暴", "Storm"}, {"雪", "Snow"}};

   /**
    * 动态模糊样式（1.0.1_28 起按「模糊机制」命名，不再借用别的客户端名字）：
    * 0 = 时间累积（帧累积 + 亚像素重采样），1 = 线性拖影（子帧叠加硬拖尾），
    * 2 = 速度模糊（沿摄像机位移方向定向抽样），3 = 柔化累积（帧率无关累积 + 柔化）。
    */
   public static final String[][] GLASS_MODE_NAMES = new String[][]{
      {"关闭", "Off"},
      {"全部玻璃（含染色）", "All glass (incl. stained)"},
      {"仅未染色玻璃", "Clear glass only"}
   };

   public static final String[][] BLUR_STYLE_NAMES = new String[][]{
      {"时间累积", "Temporal"},
      {"线性拖影", "Linear"},
      {"速度模糊", "Velocity"},
      {"柔化累积", "Soft"}
   };

   public static List<String> validate() {
      List<String> problems = new ArrayList<>();
      Set<Integer> groups = new HashSet<>();
      Set<Integer> keys = new HashSet<>();

      for (int[] row : ROWS) {
         if (row[0] == -1 && row[2] > 0 && !groups.add(row[2])) {
            problems.add("分组 id 重复: " + row[2]);
         }
      }

      for (int[] rowx : ROWS) {
         int kind = rowx[0];
         int parent = rowx[1];
         int key = rowx[2];
         if (kind != -1) {
            if (parent > 0 && !groups.contains(parent)) {
               problems.add("分组未定义: " + parent + "（key " + key + "）");
            }

            if (key > 0) {
               if (!keys.add(key)) {
                  problems.add("功能 key 重复: " + key);
               }

               if (key > LABELS.length) {
                  problems.add("key " + key + " 超出 LABELS 范围(" + LABELS.length + ")");
               } else if (LABELS[key - 1][0].trim().isEmpty() && LABELS[key - 1][1].trim().isEmpty()) {
                  problems.add("key " + key + " 没有文案（行表/文案表错位）");
               }
            }
         }
      }

      // 1.0.1_31：顺带自检排序结果——分类 A→Z、分类内条目 A→Z、同一分类的行必须连续，
      // 后续加设置时如果忘了给英文名或者把行插错位置，启动日志会直接报出来，不会静默乱序。
      List<int[]> strayRows = new ArrayList<>();
      List<SettingGroup> ordered = parseGroups(strayRows);

      String lastGroupKey = null;
      String lastGroupName = null;

      for (SettingGroup group : ordered) {
         String groupKey = group.key();

         if (lastGroupKey != null && groupKey.compareTo(lastGroupKey) < 0) {
            problems.add("分类顺序不是 A→Z: " + lastGroupName + " 之后出现 " + group.name());
         }

         lastGroupKey = groupKey;
         lastGroupName = group.name();

         if (group.units.isEmpty()) {
            problems.add("分类 " + group.id + " 没有任何条目");
         }

         String lastUnitKey = null;
         String lastUnitName = null;
         int start = 0;

         if (group.id == G_FOV) {
            if (group.units.isEmpty() || group.units.get(0).head[2] != FOV_ON) {
               problems.add("FOV 分类没有把客户端 FOV 开关置顶");
            } else {
               // 置顶项不参与后面的 A→Z 比较，否则第二项必然小于 Client FOV。
               lastUnitKey = null;
               lastUnitName = null;
               start = 1;
            }
         }

         for (int ui = start; ui < group.units.size(); ui++) {
            SettingUnit unit = group.units.get(ui);
            String unitKey = unit.key();

            if (lastUnitKey != null && unitKey.compareTo(lastUnitKey) < 0) {
               problems.add("分类 " + group.id + " 内条目顺序不是 A→Z: " + lastUnitName + " 之后出现 " + unit.name());
            }

            lastUnitKey = unitKey;
            lastUnitName = unit.name();
         }
      }

      for (int[] stray : strayRows) {
         if (stray[1] > 0) {
            problems.add("分类内的行被别的行隔开（parent " + stray[1] + "，key " + stray[2] + "），会从界面上消失");
         }
      }

      return problems;
   }

   private static void gname(int id, String zh, String en) {
      GROUP_NAME_ZH[id - 200] = zh;
      GROUP_NAME_EN[id - 200] = en;
   }

   // ---- 1.0.1_29：设置界面按英文名 A→Z 排列 ----

   /**
    * 一项设置 = 首行（开关 / 多值胶囊 / 标题）+ 跟在它后面的子行（数值、颜色、样式、编辑按钮）。
    * 子行跟着首行走，不单独参与排序，否则「动态模糊」的滑块会被字母顺序甩到别的开关下面。
    */
   private static final class SettingUnit {
      final int[] head;
      final List<int[]> rows = new ArrayList<>();

      SettingUnit(int[] head) {
         this.head = head;
         this.rows.add(head);
      }

      /** 排序键：英文文案的小写形式（不区分大小写）。 */
      String key() {
         return this.name().toLowerCase(java.util.Locale.ROOT);
      }

      /** 英文名原文，用于自检信息。 */
      String name() {
         int i = this.head[2] - 1;
         String en = i >= 0 && i < LABELS.length ? LABELS[i][1] : "";
         return en == null ? "" : en;
      }
   }

   /** 左栏的一个分类及其条目。 */
   private static final class SettingGroup {
      final int id;
      final int[] header;
      final List<SettingUnit> units = new ArrayList<>();

      SettingGroup(int id, int[] header) {
         this.id = id;
         this.header = header;
      }

      String key() {
         return this.name().toLowerCase(java.util.Locale.ROOT);
      }

      /** 分类英文名原文，用于自检信息。 */
      String name() {
         int i = this.id - 200;
         String en = i >= 0 && i < GROUP_NAME_EN.length ? GROUP_NAME_EN[i] : "";
         return en == null ? "" : en;
      }
   }

   private static final java.util.Comparator<SettingGroup> BY_GROUP_NAME = new java.util.Comparator<SettingGroup>() {
      public int compare(SettingGroup a, SettingGroup b) {
         return a.key().compareTo(b.key());
      }
   };

   private static final java.util.Comparator<SettingUnit> BY_SETTING_NAME = new java.util.Comparator<SettingUnit>() {
      public int compare(SettingUnit a, SettingUnit b) {
         return a.key().compareTo(b.key());
      }
   };

   /**
    * 按当前 ROWS 的排布切出「分类 → 设置项」结构。
    * 1.0.1_31：切分规则改成——行首是开关就开新的一项；行首不是开关（数值/颜色/样式）时，
    * 只有紧跟在开关后面才算它的子行，否则自己就是独立一项（比如第一人称手臂三个偏移量互不从属，
    * 各自参与排序，不会被粘成一块）。
    */
   private static List<SettingGroup> parseGroups(List<int[]> looseOut) {
      List<SettingGroup> groups = new ArrayList<>();
      int i = 0;

      while (i < ROWS.length) {
         int[] row = ROWS[i];

         if (row[0] == K_HEADER) {
            SettingGroup group = new SettingGroup(row[2], row);
            i++;

            while (i < ROWS.length && ROWS[i][0] != K_HEADER && ROWS[i][1] == row[2]) {
               int[] member = ROWS[i];

               if (startsNewUnit(group, member)) {
                  group.units.add(new SettingUnit(member));
               } else {
                  group.units.get(group.units.size() - 1).rows.add(member);
               }

               i++;
            }

            groups.add(group);
         } else {
            if (looseOut != null) {
               looseOut.add(row);
            }
            i++;
         }
      }

      return groups;
   }

   private static boolean startsNewUnit(SettingGroup group, int[] member) {
      if (group.units.isEmpty() || member[0] == K_TOGGLE) {
         return true;
      }
      // 这两项看着像上面开关的子行，其实是独立设置，各自成卡。
      if (member[2] == FIRE_HEIGHT || member[2] == PANEL_OPACITY) {
         return true;
      }
      // 上一项的头不是开关，说明它是独立滑块，当前行不能挂在它下面
      return group.units.get(group.units.size() - 1).head[0] != K_TOGGLE;
   }

   /**
    * 把 ROWS 原地重排成「分类按英文名 A→Z，分类内的设置项也按英文名 A→Z」。
    * 就地重排（System.arraycopy 回写）是因为 ROWS 是 public static final，引用不能被替换；
    * 界面的渲染、滚动、点击全部按 ROWS 的顺序工作，排好之后不用再改 ClientScreen。
    */
   private static void sortAlphabetically() {
      List<int[]> loose = new ArrayList<>();
      List<SettingGroup> groups = parseGroups(loose);

      java.util.Collections.sort(groups, BY_GROUP_NAME);

      for (SettingGroup group : groups) {
         if (group.id == G_FOV) {
            // FOV 组按产品要求把「客户端 FOV」开关置顶，其余仍按英文名 A→Z。
            for (int i = 0; i < group.units.size(); i++) {
               if (group.units.get(i).head[2] == FOV_ON) {
                  SettingUnit first = group.units.remove(i);
                  group.units.add(0, first);
                  break;
               }
            }
            for (int i = 1; i < group.units.size(); i++) {
               // 从第二个开始排序，避免把置顶开关又排回去。
               java.util.List<SettingUnit> rest = group.units.subList(1, group.units.size());
               java.util.Collections.sort(rest, BY_SETTING_NAME);
               break;
            }
         } else {
            java.util.Collections.sort(group.units, BY_SETTING_NAME);
         }
      }

      int n = 0;

      for (SettingGroup group : groups) {
         ROWS[n] = group.header;
         UNIT_HEAD[n] = true;
         n++;

         for (SettingUnit unit : group.units) {
            int j = 0;
            for (int[] row : unit.rows) {
               ROWS[n] = row;
               UNIT_HEAD[n] = (j == 0);
               n++;
               j++;
            }
         }
      }

      for (int[] row : loose) {
         ROWS[n] = row;
         UNIT_HEAD[n] = true;
         n++;
      }

      if (n != ROWS.length) {
         throw new IllegalStateException("设置表重排后行数不一致: " + n + " != " + ROWS.length);
      }
   }

   private SettingsSpec() {
   }

   static {
      gname(200, "旧动画", "Old Animations");
      gname(201, "受击反馈", "HURT FEEDBACK");
      gname(202, "亮度", "BRIGHTNESS");
      gname(203, "视角摇晃", "VIEW BOBBING");
      gname(204, "疾跑", "SPRINT");
      gname(205, "FOV / 视场角", "FIELD OF VIEW");
      gname(206, "附魔光效", "ENCHANT GLINT");
      gname(207, "迷雾", "FOG");
      gname(208, "方块高亮", "BLOCK HIGHLIGHT");
      gname(209, "名字标签", "NAME TAG");
      gname(210, "聊天框", "CHAT");
      gname(211, "计分板", "SCOREBOARD");
      gname(212, "天气", "WEATHER");
      gname(213, "世界时间", "WORLD TIME");
      gname(214, "鼠标灵敏度", "MOUSE");
      gname(215, "动态模糊", "MOTION BLUR");
      gname(218, "真实掉落物", "REALISTIC DROPS");
      gname(219, "准星", "CROSSHAIR");
      gname(220, "UI选项", "UI OPTIONS");
      gname(221, "动画", "ANIMATIONS");
      gname(222, "受击残影", "HIT AFTERIMAGE");
      gname(223, "第一人称手臂", "FIRST PERSON HAND");
      gname(224, "自由视角", "FREELOOK");
      gname(225, "粒子", "PARTICLES");
      gname(226, "玻璃", "GLASS");
      // 1.0.1_29：分类与条目统一按英文名 A→Z 排序（显示文案仍跟随游戏语言，排序只看英文名；必须在 gname 初始化完之后做）
      sortAlphabetically();
   }
}
