package net.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import net.client.util.ClientLog;
import net.minecraft.client.MinecraftClient;

public final class ClientConfig {
   public static boolean hurtColorOn = false;
   public static boolean realDrops = false;
   public static boolean uiOverride = false;
   public static boolean lightTheme = false;
   /** 自定义主题色（ARGB），默认 M3 baseline 紫。 */
   public static int themeColor = 0xFF6750A4;
   /** 用 MD3 预烘焙字体覆盖原版界面字体。 */
   public static boolean modernFont = false;
   /** 1.0.2_1：客户端设置面板的背景不透明度（0=全透明，100=不透明）。 */
   public static int panelOpacity = 100;
   /** 0=显示玻璃，1=隐藏全部玻璃，2=只隐藏未染色玻璃。 */
   public static int glassMode = 0;
   public static boolean dropRest = false;
   /** 原版受击覆盖色：(1.0, 0.0, 0.0, 0.30) 的 ARGB。 */
   public static int hurtColor = 1308557312;
   public static boolean noHurtCam = false;
   public static boolean blockHitAnim = false;
   /** 「关闭受击闪烁」：开启后受击/回血时血条（心形）不再闪烁抖动；默认关闭=保持原版闪烁。 */
   public static boolean hurtBlinkOff = false;
   public static boolean afterimage = false;
   public static int afterimageColor = -1;
   public static int afterimageLife = 1380;
   public static float afterimageStrength = 1.0F;
   public static boolean freelook = false;
   public static int freelookKey = 56;
   public static int afterimageLayers = 10;
   public static boolean afterimageSelf = false;
   public static boolean fullbright = false;
   public static boolean minimalBobbing = false;
   public static boolean autoSprint = false;
   /**
    * 1.0.1_13：FOV 改成「客户端 FOV」——总开关 + 默认值 + 六个条件各自的目标视野（Badlion 式）。
    *
    * 总开关 fovClient 打开时由客户端接管 getFov：默认值 = fovBase × fovGlobal，
    * 疾跑 / 速度药水 / 迟缓 / 飞行 / 瞄准 / 水下 各自有开关和目标值，命中条件时把视野过渡到该值。
    * 总开关关闭时客户端完全不参与，原版 FOV（含 OptiFine 的缩放）逐位照旧。
    */
   /** 1.0.1_17：默认改成关闭（新装不再接管原版视野），老配置会一次性迁到关闭。 */
   public static boolean fovClient = false;
   public static float fovBase = 70.0F;
   /** 条件切换时是否做平滑过渡（Badlion 的 SMOOTH）；关闭则瞬时切换。 */
   public static boolean fovSmooth = true;
   /** 整体倍率：乘在默认视野上，50%–150%。 */
   public static float fovGlobal = 1.0F;
   /** 最终视野上下限（内部兜底，不再单独做成设置项）。 */
   public static float fovMin = 10.0F;
   public static float fovMax = 160.0F;
   public static boolean fovSprintOn = true;
   public static float fovSprint = 80.0F;
   public static boolean fovSpeedOn = true;
   public static float fovSpeed = 80.0F;
   public static boolean fovSlowOn = true;
   public static float fovSlow = 65.0F;
   public static boolean fovFlyOn = true;
   public static float fovFly = 77.0F;
   public static boolean fovAimOn = true;
   public static float fovAim = 60.0F;
   public static boolean fovWaterOn = true;
   public static float fovWater = 60.0F;
   public static boolean customGlint = false;
   public static int glintColor = -6606593;
   public static boolean fogModify = false;
   public static int fogColor = -9535576;
   public static float fogDensity = 1.0F;
   public static boolean fogSky = false;
   public static boolean highlightBlock = false;
   public static int highlightFill = 1344470920;
   public static int highlightOutline = -16711800;
   public static boolean selfNameTag = false;
   /** 原版玩家名牌底板色：纯黑 + 25% 透明度（1.8.9 EntityRenderer.renderLabelIfPresent 的 0x40000000）。 */
   public static int nameBgColor = 1073741824;
   public static boolean customCrosshair = false;
   public static int crosshairColor = -1;
   public static String crosshairMask = "000010000/000010000/000010000/000010000/111111111/000010000/000010000/000010000/000010000";
   public static int nameTextColor = -1;
   public static float screenDim = 0.80F;
   /** 世界内菜单背景模糊强度，0=关闭，1=最强；默认 50%。 */
   public static float backdropBlur = 0.5F;
   public static boolean chatBackground = false;
   /** 原版聊天框底色：纯黑 + 原版透明度（0x80000000，由 ChatHudMixin 按原版淡出比例换算）。 */
   public static int chatBgColor = -2147483648;
   public static boolean scoreboardBackground = false;
   /** 隐藏计分板（默认关 = 保持原版显示）。 */
   public static boolean hideScoreboard = false;
   /** 原版计分板底色：0x50000000（1.8.9 InGameHud.renderScoreboardObjective 的 fill 常量）。 */
   public static int scoreboardBgColor = 1342177280;
   public static boolean chatVisible = true;
   
   public static boolean hideScoreNumbers = false;
   // ---- 1.0.1 自定义计分板内容 ----
   /** 开启后由 Client 接管侧边栏计分板渲染，下面的字段才生效。 */
   public static boolean scoreboardCustom = false;
   /** 自定义标题，空字符串 = 沿用服务器下发的 objective 标题。 */
   public static String scoreboardTitle = "";
   /** 自定义计分板正文，一行一条；"名字:分数" 会拆成左右两列。空 = 回退服务端条目。 */
   public static String scoreboardText = "";
   public static boolean scoreboardShowTitle = true;
   public static boolean scoreboardShowNames = true;
   public static boolean scoreboardShowScores = true;
   /** 最多显示多少行（1-15，和原版上限一致）。 */
   public static int scoreboardMaxLines = 15;
   public static int weatherMode = 0;
   public static boolean weatherThroughBlocks = false;
   public static float handForward = 0.0F;
   public static float handRight = 0.0F;
   public static float handUp = 0.0F;
   public static boolean timeOverride = false;
   public static int timeTicks = 6000;
   public static boolean motionBlur = false;
   public static float blurAmount = 0.5F;
   public static boolean hideOwnParticles = false;
   /** 1.0.1_17：线性鼠标灵敏度（跳过原版灵敏度三次曲线，g = f * 2），默认关闭=原版手感。 */
   public static boolean rawInput = false;
   /** 一次性迁移标记：1.0.1_17 把客户端 FOV 总开关默认改成关闭。 */
   public static boolean fovDefaultOffMigrated = false;
   public static boolean customParticles = false;
   /** 1.0.1_14：挖掘方块粒子 / 疾跑脚步粒子改成「隐藏」语义，默认关闭（=原版照常显示）。 */
   /** 一次性迁移标记：1.0.1_14 把聊天框 / 计分板 / 受击色的自定义默认值对齐原版。 */
   public static boolean colorDefaultsMigrated = false;
   /** 一次性迁移标记：1.0.1_19 把名牌底色默认值对齐原版 0x40000000。 */
   public static boolean nameBgMigrated = false;
   public static boolean hideMiningParticles = false;
   public static boolean hideSprintParticles = false;
   public static float critParticleMultiplier = 1.0F;
   public static float sharpParticleMultiplier = 1.0F;
   public static boolean imeFix = false;
   public static int fireHeight = 100;
   /** 1.0.1：第一人称主手改为左手外观（仅视觉，服务端无感知）。 */
   public static boolean leftHanded = false;
   public static String msClientId = "";
   // ---- 攻击手感（1.0.1_29：改成后台常开、界面上不再保留开关）----
   // 旧版「无点击冷却 / 命中后重置冷却 / 攻击节奏平滑」三项已合并进 MinecraftClientMixin 的
   // attackGuard + hitDelayFix 两处注入，不再有配置字段：每次点击前清攻击冷却，命中实体后再清一次。
   // ---- 动态模糊样式（1.0.1_24 起四套，见 MotionBlurMixin）----
   // 0 = 时间累积，1 = 线性拖影，2 = 速度模糊，3 = 柔化累积（详见 MotionBlurMixin）
   public static int blurStyle = 0;
   public static final float FULLBRIGHT_GAMMA = 100.0F;
   private static boolean loaded = false;
   private static boolean dirty = false;
   private static long lastFlush = 0L;

   public static int langIndex() {
      try {
         MinecraftClient mc = MinecraftClient.getInstance();
         return mc != null && mc.options != null && mc.options.language != null && mc.options.language.toLowerCase(Locale.ROOT).startsWith("zh") ? 0 : 1;
      } catch (Exception var1) {
         return 1;
      }
   }

   private ClientConfig() {
   }

   private static File configFile() {
      return new File(MinecraftClient.getInstance().runDirectory, "config/client.properties");
   }

   public static synchronized void ensureLoaded() {
      if (!loaded) {
         loaded = true;
         File file = configFile();
         if (file.isFile()) {
            Properties props = new Properties();
            // 1.0.2：迁移标记必须在迁移完立刻写回。原来只有 nameBgMigrated 调了
            // saveSoon()，fovDefaultOffMigrated / colorDefaultsMigrated 只改了内存，
            // 只要玩家这一局没动过任何设置，下次启动就会重新把 fovClient 关掉、
            // 把聊天框/计分板/受击色覆盖回默认值 —— 表现为「改了的颜色每次重启都丢」。
            boolean migrated = false;

            try {
               InputStreamReader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);

               try {
                  props.load(in);
               } catch (Throwable var6) {
                  try {
                     in.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }

                  throw var6;
               }

               in.close();
            } catch (Exception var7) {
               // 读失败：保留字段默认值，但仍然过一遍范围收敛。
               validate();
               return;
            }

            hurtColorOn = readBool(props, "hurtColorOn", hurtColorOn);
            realDrops = readBool(props, "realDrops", realDrops);
            uiOverride = readBool(props, "uiOverride", uiOverride);
            lightTheme = readBool(props, "lightTheme", lightTheme);
            themeColor = readColor(props, "themeColor", themeColor);
            modernFont = readBool(props, "modernFont", modernFont);
            panelOpacity = readInt(props, "panelOpacity", panelOpacity);
            glassMode = readInt(props, "glassMode", glassMode);
            dropRest = readBool(props, "dropRest", readBool(props, "dropNoSpin", dropRest));
            hurtColor = readInt(props, "hurtColor", hurtColor);
            noHurtCam = readBool(props, "noHurtCam", noHurtCam);
            blockHitAnim = readBool(props, "blockHitAnim", blockHitAnim);
            hurtBlinkOff = readBool(props, "hurtBlinkOff", hurtBlinkOff);
            afterimage = readBool(props, "afterimage", afterimage);
            freelook = readBool(props, "freelook", freelook);
            freelookKey = readInt(props, "freelookKey", freelookKey);
            afterimageColor = readColor(props, "afterimageColor", afterimageColor);
            afterimageLife = readInt(props, "afterimageLife", afterimageLife);
            afterimageStrength = readFloat(props, "afterimageStrength", afterimageStrength);
            afterimageLayers = readInt(props, "afterimageLayers", afterimageLayers);
            afterimageSelf = readBool(props, "afterimageSelf", afterimageSelf);
            fullbright = readBool(props, "fullbright", fullbright);
            minimalBobbing = readBool(props, "minimalBobbing", minimalBobbing);
            autoSprint = readBool(props, "autoSprint", autoSprint);
            fovBase = readFloat(props, "fovBase", fovBase);
            fovClient = readBool(props, "fovClient", fovClient);
            fovDefaultOffMigrated = readBool(props, "fovDefaultOffMigrated", fovDefaultOffMigrated);
            if (!fovDefaultOffMigrated) {
               // 1.0.1_17：客户端 FOV 的默认状态改成关闭，老配置一次性迁过来
               fovClient = false;
               fovDefaultOffMigrated = true;
               migrated = true;
            }
            fovSmooth = readBool(props, "fovSmooth", fovSmooth);
            fovGlobal = readFloat(props, "fovGlobal", fovGlobal);
            fovMin = readFloat(props, "fovMin", fovMin);
            fovMax = readFloat(props, "fovMax", fovMax);
            fovSprintOn = readBool(props, "fovSprintOn", fovSprintOn);
            fovSprint = readFloat(props, "fovSprint", fovSprint);
            fovSpeedOn = readBool(props, "fovSpeedOn", fovSpeedOn);
            fovSpeed = readFloat(props, "fovSpeed", fovSpeed);
            fovSlowOn = readBool(props, "fovSlowOn", fovSlowOn);
            fovSlow = readFloat(props, "fovSlow", fovSlow);
            fovFlyOn = readBool(props, "fovFlyOn", fovFlyOn);
            fovFly = readFloat(props, "fovFly", fovFly);
            fovAimOn = readBool(props, "fovAimOn", fovAimOn);
            fovAim = readFloat(props, "fovAim", fovAim);
            fovWaterOn = readBool(props, "fovWaterOn", fovWaterOn);
            fovWater = readFloat(props, "fovWater", fovWater);
            customGlint = readBool(props, "customGlint", customGlint);
            glintColor = readColor(props, "glintColor", glintColor);
            fogModify = readBool(props, "fogModify", fogModify);
            fogColor = readColor(props, "fogColor", fogColor);
            fogDensity = readFloat(props, "fogDensity", fogDensity);
            fogSky = readBool(props, "fogSky", readBool(props, "fogSkyFollow", fogSky));
            highlightBlock = readBool(props, "highlightBlock", highlightBlock);
            highlightFill = readColor(props, "highlightFill", highlightFill);
            highlightOutline = readColor(props, "highlightOutline", highlightOutline);
            selfNameTag = readBool(props, "selfNameTag", selfNameTag);
            nameBgColor = readColor(props, "nameBgColor", nameBgColor);
            customCrosshair = readBool(props, "customCrosshair", customCrosshair);
            crosshairColor = readColor(props, "crosshairColor", crosshairColor);
            crosshairMask = props.getProperty("crosshairMask", crosshairMask);
            screenDim = readFloat(props, "screenDim", screenDim);
            backdropBlur = readFloat(props, "backdropBlur", backdropBlur);
            if (!readBool(props, "screenDimVanillaDefault", false) && screenDim <= 0.02F) {
               // 旧默认值是 0%（只有极轻的遮罩），一次性迁移到接近原版菜单的暗度；手动调过的值保留。
               screenDim = 0.80F;
            }
            nameTextColor = readColor(props, "nameTextColor", nameTextColor);
            chatBackground = readBool(props, "chatBackground", chatBackground);
            chatBgColor = readColor(props, "chatBgColor", chatBgColor);
            scoreboardBackground = readBool(props, "scoreboardBackground", scoreboardBackground);
            chatVisible = readBool(props, "chatVisible", chatVisible);
            hideScoreboard = readBool(props, "hideScoreboard", hideScoreboard);
            hideScoreNumbers = readBool(props, "hideScoreNumbers", hideScoreNumbers);
            scoreboardBgColor = readColor(props, "scoreboardBgColor", scoreboardBgColor);
            scoreboardCustom = readBool(props, "scoreboardCustom", scoreboardCustom);
            scoreboardTitle = props.getProperty("scoreboardTitle", scoreboardTitle);
            scoreboardText = props.getProperty("scoreboardText", scoreboardText);
            scoreboardShowTitle = readBool(props, "scoreboardShowTitle", scoreboardShowTitle);
            scoreboardShowNames = readBool(props, "scoreboardShowNames", scoreboardShowNames);
            scoreboardShowScores = readBool(props, "scoreboardShowScores", scoreboardShowScores);
            scoreboardMaxLines = readInt(props, "scoreboardMaxLines", scoreboardMaxLines);
            weatherMode = readInt(props, "weatherMode", weatherMode);
            weatherThroughBlocks = readBool(props, "weatherThroughBlocks", weatherThroughBlocks);
            handForward = readFloat(props, "handForward", handForward);
            handRight = readFloat(props, "handRight", handRight);
            handUp = readFloat(props, "handUp", handUp);
            timeOverride = readBool(props, "timeOverride", timeOverride);
            timeTicks = readInt(props, "timeTicks", timeTicks);
            motionBlur = readBool(props, "motionBlur", motionBlur);
            blurAmount = readFloat(props, "blurAmount", blurAmount);
            hideOwnParticles = readBool(props, "hideOwnParticles", hideOwnParticles);
            rawInput = readBool(props, "rawInput", rawInput);
            customParticles = readBool(props, "customParticles", customParticles);
            colorDefaultsMigrated = readBool(props, "colorDefaultsMigrated", colorDefaultsMigrated);
            nameBgMigrated = readBool(props, "nameBgMigrated", nameBgMigrated);
            if (!colorDefaultsMigrated) {
               // 旧版这三项默认值不是原版色（受击色还被 validate 剥掉了 alpha），一次性对齐：
               // 聊天框 #80000000（纯黑）、计分板 #50000000、受击 #4DFF0000（红 30%）
               chatBgColor = -2147483648;
               scoreboardBgColor = 1342177280;
               hurtColor = 1308557312;
               colorDefaultsMigrated = true;
               migrated = true;
            }
            if (!nameBgMigrated) {
               // 旧版默认是 0x80000000（50% 黑），比原版名牌底 0x40000000（25% 黑）暗一倍，一次性对齐
               nameBgColor = 1073741824;
               nameBgMigrated = true;
               migrated = true;
            }
            hideMiningParticles = readBool(props, "hideMiningParticles", hideMiningParticles);
            hideSprintParticles = readBool(props, "hideSprintParticles", hideSprintParticles);
            critParticleMultiplier = readFloat(props, "critParticleMultiplier", critParticleMultiplier);
            sharpParticleMultiplier = readFloat(props, "sharpParticleMultiplier", sharpParticleMultiplier);
            imeFix = readBool(props, "imeFix", imeFix);
            fireHeight = readInt(props, "fireHeight", fireHeight);
            leftHanded = readBool(props, "leftHanded", leftHanded);
            msClientId = props.getProperty("msClientId", msClientId);
            // 1.0.1_29：攻击手感优化已改成后台常开（不再有开关），旧配置里的
            // attackTuning / noClickDelay / hitDelayFix / stableAttackTiming 一律忽略。
            blurStyle = readInt(props, "blurStyle", blurStyle);
            validate();
            if (migrated) {
               saveSoon();
            }
         } else {
            // 1.0.2：没有配置文件时原来直接跳过 validate()，默认值不经过收敛。
            // 正常默认值都在范围内，但这让「首次启动」和「读配置失败」两条路径的
            // 不变量不一致；统一收敛一次。
            validate();
         }
      }
   }

   public static synchronized void save() {
      File file = configFile();
      File dir = file.getParentFile();
      if (dir != null && !dir.isDirectory()) {
         dir.mkdirs();
      }

      Properties props = new Properties();
      props.setProperty("hurtColorOn", Boolean.toString(hurtColorOn));
      props.setProperty("realDrops", Boolean.toString(realDrops));
      props.setProperty("uiOverride", Boolean.toString(uiOverride));
      props.setProperty("lightTheme", Boolean.toString(lightTheme));
      props.setProperty("themeColor", toHex(themeColor));
      props.setProperty("modernFont", Boolean.toString(modernFont));
      props.setProperty("panelOpacity", Integer.toString(panelOpacity));
      props.setProperty("glassMode", Integer.toString(glassMode));
      props.setProperty("dropRest", Boolean.toString(dropRest));
      props.setProperty("hurtColor", Integer.toString(hurtColor));
      props.setProperty("noHurtCam", Boolean.toString(noHurtCam));
      props.setProperty("blockHitAnim", Boolean.toString(blockHitAnim));
      props.setProperty("hurtBlinkOff", Boolean.toString(hurtBlinkOff));
      props.setProperty("afterimage", Boolean.toString(afterimage));
      props.setProperty("freelook", Boolean.toString(freelook));
      props.setProperty("freelookKey", Integer.toString(freelookKey));
      props.setProperty("afterimageColor", toHex(afterimageColor));
      props.setProperty("afterimageLife", Integer.toString(afterimageLife));
      props.setProperty("afterimageStrength", Float.toString(afterimageStrength));
      props.setProperty("afterimageLayers", Integer.toString(afterimageLayers));
      props.setProperty("afterimageSelf", Boolean.toString(afterimageSelf));
      props.setProperty("fullbright", Boolean.toString(fullbright));
      props.setProperty("minimalBobbing", Boolean.toString(minimalBobbing));
      props.setProperty("autoSprint", Boolean.toString(autoSprint));
      props.setProperty("fovBase", Float.toString(fovBase));
      props.setProperty("fovClient", Boolean.toString(fovClient));
      props.setProperty("fovSmooth", Boolean.toString(fovSmooth));
      props.setProperty("fovGlobal", Float.toString(fovGlobal));
      props.setProperty("fovMin", Float.toString(fovMin));
      props.setProperty("fovMax", Float.toString(fovMax));
      props.setProperty("fovSprintOn", Boolean.toString(fovSprintOn));
      props.setProperty("fovSprint", Float.toString(fovSprint));
      props.setProperty("fovSpeedOn", Boolean.toString(fovSpeedOn));
      props.setProperty("fovSpeed", Float.toString(fovSpeed));
      props.setProperty("fovSlowOn", Boolean.toString(fovSlowOn));
      props.setProperty("fovSlow", Float.toString(fovSlow));
      props.setProperty("fovFlyOn", Boolean.toString(fovFlyOn));
      props.setProperty("fovFly", Float.toString(fovFly));
      props.setProperty("fovAimOn", Boolean.toString(fovAimOn));
      props.setProperty("fovAim", Float.toString(fovAim));
      props.setProperty("fovWaterOn", Boolean.toString(fovWaterOn));
      props.setProperty("fovWater", Float.toString(fovWater));
      props.setProperty("customGlint", Boolean.toString(customGlint));
      props.setProperty("glintColor", toHex(glintColor));
      props.setProperty("fogModify", Boolean.toString(fogModify));
      props.setProperty("fogColor", toHex(fogColor));
      props.setProperty("fogDensity", Float.toString(fogDensity));
      props.setProperty("fogSky", Boolean.toString(fogSky));
      props.setProperty("highlightBlock", Boolean.toString(highlightBlock));
      props.setProperty("highlightFill", toHex(highlightFill));
      props.setProperty("highlightOutline", toHex(highlightOutline));
      props.setProperty("selfNameTag", Boolean.toString(selfNameTag));
      props.setProperty("nameBgColor", toHex(nameBgColor));
      props.setProperty("customCrosshair", Boolean.toString(customCrosshair));
      props.setProperty("crosshairColor", toHex(crosshairColor));
      props.setProperty("crosshairMask", crosshairMask);
      props.setProperty("screenDim", String.format(Locale.ROOT, "%.2f", screenDim));
      props.setProperty("backdropBlur", String.format(Locale.ROOT, "%.2f", backdropBlur));
      props.setProperty("screenDimVanillaDefault", "true");
      props.setProperty("nameTextColor", toHex(nameTextColor));
      props.setProperty("chatBackground", Boolean.toString(chatBackground));
      props.setProperty("chatBgColor", toHex(chatBgColor));
      props.setProperty("scoreboardBackground", Boolean.toString(scoreboardBackground));
      props.setProperty("chatVisible", Boolean.toString(chatVisible));
      props.setProperty("hideScoreboard", Boolean.toString(hideScoreboard));
      props.setProperty("hideScoreNumbers", Boolean.toString(hideScoreNumbers));
      props.setProperty("scoreboardBgColor", toHex(scoreboardBgColor));
      props.setProperty("scoreboardCustom", Boolean.toString(scoreboardCustom));
      props.setProperty("scoreboardTitle", scoreboardTitle == null ? "" : scoreboardTitle);
      props.setProperty("scoreboardText", scoreboardText == null ? "" : scoreboardText);
      props.setProperty("scoreboardShowTitle", Boolean.toString(scoreboardShowTitle));
      props.setProperty("scoreboardShowNames", Boolean.toString(scoreboardShowNames));
      props.setProperty("scoreboardShowScores", Boolean.toString(scoreboardShowScores));
      props.setProperty("scoreboardMaxLines", Integer.toString(scoreboardMaxLines));
      props.setProperty("weatherMode", Integer.toString(weatherMode));
      props.setProperty("weatherThroughBlocks", Boolean.toString(weatherThroughBlocks));
      props.setProperty("handForward", Float.toString(handForward));
      props.setProperty("handRight", Float.toString(handRight));
      props.setProperty("handUp", Float.toString(handUp));
      props.setProperty("timeOverride", Boolean.toString(timeOverride));
      props.setProperty("timeTicks", Integer.toString(timeTicks));
      props.setProperty("motionBlur", Boolean.toString(motionBlur));
      props.setProperty("blurAmount", Float.toString(blurAmount));
      props.setProperty("nameBgMigrated", Boolean.toString(nameBgMigrated));
      props.setProperty("hideOwnParticles", Boolean.toString(hideOwnParticles));
      props.setProperty("customParticles", Boolean.toString(customParticles));
      props.setProperty("colorDefaultsMigrated", Boolean.toString(colorDefaultsMigrated));
      props.setProperty("fovDefaultOffMigrated", Boolean.toString(fovDefaultOffMigrated));
      props.setProperty("rawInput", Boolean.toString(rawInput));
      props.setProperty("hideMiningParticles", Boolean.toString(hideMiningParticles));
      props.setProperty("hideSprintParticles", Boolean.toString(hideSprintParticles));
      props.setProperty("critParticleMultiplier", Float.toString(critParticleMultiplier));
      props.setProperty("sharpParticleMultiplier", Float.toString(sharpParticleMultiplier));
      props.setProperty("imeFix", Boolean.toString(imeFix));
      props.setProperty("fireHeight", Integer.toString(fireHeight));
      props.setProperty("leftHanded", Boolean.toString(leftHanded));
      props.setProperty("msClientId", msClientId == null ? "" : msClientId);
      props.setProperty("blurStyle", Integer.toString(blurStyle));
      File tmp = new File(file.getPath() + ".tmp");

      try {
         OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);

         try {
            props.store(out, "Client 1.8.9 settings");
         } catch (Throwable var9) {
            try {
               out.close();
            } catch (Throwable var7) {
               var9.addSuppressed(var7);
            }

            throw var9;
         }

         out.close();

         try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var8) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }

         dirty = false;
      } catch (Exception var10) {
         ClientLog.warn("配置写入失败: " + file, var10);
      }
   }

   public static void saveSoon() {
      dirty = true;
   }

   public static synchronized void flushIfDirty() {
      if (dirty) {
         long now = System.currentTimeMillis();
         if (now - lastFlush >= 2000L) {
            lastFlush = now;
            save();
         }
      }
   }

   public static void validate() {
      afterimageLife = clamp(afterimageLife, 200, 4000);
      afterimageStrength = clamp(afterimageStrength, 0.2F, 2.0F);
      afterimageLayers = clamp(afterimageLayers, 0, 10);
      fireHeight = clamp(fireHeight, 0, 100);
      panelOpacity = clamp(panelOpacity, 0, 100);
      fovBase = clamp(fovBase, 30.0F, 110.0F);
      fovGlobal = clamp(fovGlobal, 0.5F, 1.5F);
      fovMin = clamp(fovMin, 10.0F, 110.0F);
      fovMax = clamp(fovMax, 30.0F, 160.0F);
      if (fovMax <= fovMin) {
         fovMax = fovMin + 10.0F;
      }
      fovSprint = clamp(fovSprint, 10.0F, 160.0F);
      fovSpeed = clamp(fovSpeed, 10.0F, 160.0F);
      fovSlow = clamp(fovSlow, 10.0F, 160.0F);
      fovFly = clamp(fovFly, 10.0F, 160.0F);
      fovAim = clamp(fovAim, 10.0F, 160.0F);
      fovWater = clamp(fovWater, 10.0F, 160.0F);
      // 1.0.1_14：不再把受击色剥成不透明（旧代码这里是 hurtColor &= 0xFFFFFF），
      // 原版受击染色的 alpha 是 0.30，剥掉 alpha 会让自定义色变成整块不透明红光。
      if (afterimageColor < 0) {
         afterimageColor = 16777215;
      }
      blurStyle = clamp(blurStyle, 0, 3);
      scoreboardMaxLines = clamp(scoreboardMaxLines, 1, 15);
      if (scoreboardTitle == null) {
         scoreboardTitle = "";
      }
      if (scoreboardTitle.length() > 48) {
         scoreboardTitle = scoreboardTitle.substring(0, 48);
      }
      if (scoreboardText == null) {
         scoreboardText = "";
      }
      // 1.0.2_18：这里原来无条件写死 scoreboardCustom / scoreboardShowTitle = true，
      // 导致设置里的开关关掉后、下次启动又被强行打开（用户配置被静默覆盖）。
      scoreboardText = scoreboardText.replace("\r", "");
      if (scoreboardText.length() > 960) {
         scoreboardText = scoreboardText.substring(0, 960);
      }
   }

   // ---- 1.0.1_2：恢复默认设置 ---- //

   /** 类加载时抓一份「字段声明时的默认值」，用于「重置所有设置」。 */
   private static final java.util.Map<String, Object> DEFAULTS = new java.util.HashMap<>();

   static {
      for (java.lang.reflect.Field f : ClientConfig.class.getDeclaredFields()) {
         int mod = f.getModifiers();
         if (!java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isFinal(mod)) {
            continue;
         }
         String name = f.getName();
         // 内部状态与账号绑定信息不属于「客户端设置」，重置时保留
         if (name.equals("loaded") || name.equals("dirty") || name.equals("lastFlush") || name.equals("msClientId")
            || name.equals("DEFAULTS") || name.equals("FULLBRIGHT_GAMMA")) {
            continue;
         }
         try {
            f.setAccessible(true);
            Object v = f.get(null);
            if (v != null) {
               DEFAULTS.put(name, v);
            }
         } catch (Throwable ignored) {
         }
      }
   }

   /** 把除账号绑定外的所有设置恢复成默认值（不写盘，调用方决定何时保存）。 */
   public static synchronized void resetAll() {
      for (java.lang.reflect.Field f : ClientConfig.class.getDeclaredFields()) {
         Object def = DEFAULTS.get(f.getName());
         if (def == null) {
            continue;
         }
         try {
            f.setAccessible(true);
            f.set(null, def);
         } catch (Throwable ignored) {
         }
      }
      validate();
   }

   private static int clamp(int v, int lo, int hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   private static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   private static boolean readBool(Properties props, String key, boolean def) {
      String v = props.getProperty(key);
      return v == null ? def : Boolean.parseBoolean(v.trim());
   }

   private static int readInt(Properties props, String key, int def) {
      try {
         String v = props.getProperty(key);
         return v == null ? def : Integer.parseInt(v.trim());
      } catch (Exception var4) {
         return def;
      }
   }

   private static float readFloat(Properties props, String key, float def) {
      try {
         String v = props.getProperty(key);
         return v == null ? def : Float.parseFloat(v.trim());
      } catch (Exception var4) {
         return def;
      }
   }

   private static int readColor(Properties props, String key, int def) {
      try {
         String v = props.getProperty(key);
         if (v == null) {
            return def;
         } else {
            v = v.trim();
            if (v.startsWith("#")) {
               v = v.substring(1);
            }

            if (v.length() == 6) {
               v = "FF" + v;
            }

            return (int)Long.parseLong(v, 16);
         }
      } catch (Exception var4) {
         return def;
      }
   }

   public static String t(String zh, String en) {
      return langIndex() == 0 ? zh : en;
   }

   public static String toHex(int argb) {
      return String.format("#%08X", argb);
   }
}
