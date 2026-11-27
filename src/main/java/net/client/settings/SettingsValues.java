package net.client.settings;

import com.amethyst.blur.Blur;
import net.client.ClientConfig;
import net.client.ime.ImeBridge;
import net.client.ui.md3.render.Md3Canvas;

/**
 * 1.0.2_2：把 ClientScreen 里那一大坨「按下标取/改设置值」的逻辑搬出来。
 *
 * 原来这些 switch 全部塞在 ClientScreen（两千多行）里，改一个设置要在一屏代码里
 * 上下翻。它们其实跟界面绘制毫无关系，只依赖 ClientConfig 与 SettingsSpec，
 * 所以独立成一个无状态工具类：界面只管画，取值/写值统一走这里。
 */
public final class SettingsValues {
   public static boolean isOn(int key) {
      switch (key) {
         case SettingsSpec.FOV_ON: return ClientConfig.fovClient;
         case SettingsSpec.GLINT_ON: return ClientConfig.customGlint;
         case SettingsSpec.FOG_ON: return ClientConfig.fogModify;
         case SettingsSpec.FOG_SKY: return ClientConfig.fogSky;
         case SettingsSpec.HL_ON: return ClientConfig.highlightBlock;
         case SettingsSpec.NAME_ON: return ClientConfig.selfNameTag;
         case SettingsSpec.CHAT_ON: return ClientConfig.chatBackground;
         case SettingsSpec.SB_ON: return ClientConfig.scoreboardBackground;
         case SettingsSpec.WEATHER_ROOF: return ClientConfig.weatherThroughBlocks;
         case SettingsSpec.RAW_INPUT: return ClientConfig.rawInput;
         case SettingsSpec.TIME_ON: return ClientConfig.timeOverride;
         case SettingsSpec.BLUR_ON: return ClientConfig.motionBlur;
         case SettingsSpec.HURT_ON: return ClientConfig.hurtColorOn;
         case SettingsSpec.DROP_ON: return ClientConfig.realDrops;
         case SettingsSpec.CROSSHAIR_ON: return ClientConfig.customCrosshair;
         case SettingsSpec.NO_HURT: return ClientConfig.noHurtCam;
         case SettingsSpec.FULLBRIGHT: return ClientConfig.fullbright;
         case SettingsSpec.MIN_BOB: return ClientConfig.minimalBobbing;
         case SettingsSpec.AUTO_SPRINT: return ClientConfig.autoSprint;
         case SettingsSpec.CHAT_VISIBLE: return !ClientConfig.chatVisible; // 反向：开关亮起表示隐藏
         case SettingsSpec.SCORE_VISIBLE: return ClientConfig.hideScoreboard;
         case SettingsSpec.SCORE_NO_NUMBERS: return ClientConfig.hideScoreNumbers;
         case SettingsSpec.SCORE_CUSTOM: return ClientConfig.scoreboardCustom;
         case SettingsSpec.LEFT_HANDED: return ClientConfig.leftHanded;
         case SettingsSpec.BLOCKHIT_ON: return ClientConfig.blockHitAnim;
         case SettingsSpec.HEART_BLINK: return ClientConfig.hurtBlinkOff;
         case SettingsSpec.AF_ON: return ClientConfig.afterimage;
         case SettingsSpec.AF_SELF: return ClientConfig.afterimageSelf;
         case SettingsSpec.FREELOOK_ON: return ClientConfig.freelook;
         case SettingsSpec.DROP_REST: return ClientConfig.dropRest;
         case SettingsSpec.UI_OVERRIDE: return ClientConfig.uiOverride;
         case SettingsSpec.BLUR_CHAT: return com.amethyst.blur.Blur.chatBlurValue();
         case SettingsSpec.MODERN_FONT: return ClientConfig.modernFont;
         case SettingsSpec.HIDE_PARTICLES: return ClientConfig.hideOwnParticles;
         case SettingsSpec.PARTICLE_ON: return ClientConfig.customParticles;
         case SettingsSpec.IME_FIX: return ClientConfig.imeFix;
         case SettingsSpec.MINING_PARTICLES: return ClientConfig.hideMiningParticles;
         case SettingsSpec.SPRINT_PARTICLES: return ClientConfig.hideSprintParticles;
         case SettingsSpec.FOV_SMOOTH: return ClientConfig.fovSmooth;
         case SettingsSpec.FOV_SPRINT_ON: return ClientConfig.fovSprintOn;
         case SettingsSpec.FOV_SPEED_ON: return ClientConfig.fovSpeedOn;
         case SettingsSpec.FOV_SLOW_ON: return ClientConfig.fovSlowOn;
         case SettingsSpec.FOV_FLY_ON: return ClientConfig.fovFlyOn;
         case SettingsSpec.FOV_AIM_ON: return ClientConfig.fovAimOn;
         case SettingsSpec.FOV_WATER_ON: return ClientConfig.fovWaterOn;
         default: return false;
      }
   }

   public static void toggle(int key) {
      switch (key) {
         case SettingsSpec.FOV_ON: ClientConfig.fovClient = !ClientConfig.fovClient; break;
         case SettingsSpec.GLINT_ON: ClientConfig.customGlint = !ClientConfig.customGlint; break;
         case SettingsSpec.FOG_ON: ClientConfig.fogModify = !ClientConfig.fogModify; break;
         case SettingsSpec.FOG_SKY: ClientConfig.fogSky = !ClientConfig.fogSky; break;
         case SettingsSpec.HL_ON: ClientConfig.highlightBlock = !ClientConfig.highlightBlock; break;
         case SettingsSpec.NAME_ON: ClientConfig.selfNameTag = !ClientConfig.selfNameTag; break;
         case SettingsSpec.CHAT_ON: ClientConfig.chatBackground = !ClientConfig.chatBackground; break;
         case SettingsSpec.SB_ON: ClientConfig.scoreboardBackground = !ClientConfig.scoreboardBackground; break;
         case SettingsSpec.WEATHER_ROOF: ClientConfig.weatherThroughBlocks = !ClientConfig.weatherThroughBlocks; break;
         case SettingsSpec.RAW_INPUT: ClientConfig.rawInput = !ClientConfig.rawInput; break;
         case SettingsSpec.TIME_ON: ClientConfig.timeOverride = !ClientConfig.timeOverride; break;
         case SettingsSpec.BLUR_ON: ClientConfig.motionBlur = !ClientConfig.motionBlur; break;
         case SettingsSpec.HURT_ON: ClientConfig.hurtColorOn = !ClientConfig.hurtColorOn; break;
         case SettingsSpec.DROP_ON: ClientConfig.realDrops = !ClientConfig.realDrops; break;
         case SettingsSpec.CROSSHAIR_ON: ClientConfig.customCrosshair = !ClientConfig.customCrosshair; break;
         case SettingsSpec.NO_HURT: ClientConfig.noHurtCam = !ClientConfig.noHurtCam; break;
         case SettingsSpec.FULLBRIGHT: ClientConfig.fullbright = !ClientConfig.fullbright; break;
         case SettingsSpec.MIN_BOB: ClientConfig.minimalBobbing = !ClientConfig.minimalBobbing; break;
         case SettingsSpec.AUTO_SPRINT: ClientConfig.autoSprint = !ClientConfig.autoSprint; break;
         case SettingsSpec.CHAT_VISIBLE: ClientConfig.chatVisible = !ClientConfig.chatVisible; break; // 默认 chatVisible=true，即“隐藏聊天栏”默认关闭
         case SettingsSpec.SCORE_VISIBLE: ClientConfig.hideScoreboard = !ClientConfig.hideScoreboard; break;
         case SettingsSpec.SCORE_NO_NUMBERS: ClientConfig.hideScoreNumbers = !ClientConfig.hideScoreNumbers; break;
         case SettingsSpec.SCORE_CUSTOM: ClientConfig.scoreboardCustom = !ClientConfig.scoreboardCustom; break;
         case SettingsSpec.LEFT_HANDED: ClientConfig.leftHanded = !ClientConfig.leftHanded; break;
         case SettingsSpec.BLOCKHIT_ON: ClientConfig.blockHitAnim = !ClientConfig.blockHitAnim; break;
         case SettingsSpec.HEART_BLINK: ClientConfig.hurtBlinkOff = !ClientConfig.hurtBlinkOff; break;
         case SettingsSpec.AF_ON: ClientConfig.afterimage = !ClientConfig.afterimage; break;
         case SettingsSpec.AF_SELF: ClientConfig.afterimageSelf = !ClientConfig.afterimageSelf; break;
         case SettingsSpec.FREELOOK_ON: ClientConfig.freelook = !ClientConfig.freelook; break;
         case SettingsSpec.DROP_REST: ClientConfig.dropRest = !ClientConfig.dropRest; break;
         case SettingsSpec.UI_OVERRIDE: ClientConfig.uiOverride = !ClientConfig.uiOverride; ClientConfig.save(); break;
         case SettingsSpec.BLUR_CHAT: com.amethyst.blur.Blur.setChatBlur(!com.amethyst.blur.Blur.chatBlurValue()); break;
         case SettingsSpec.HIDE_PARTICLES: ClientConfig.hideOwnParticles = !ClientConfig.hideOwnParticles; break;
         case SettingsSpec.PARTICLE_ON: ClientConfig.customParticles = !ClientConfig.customParticles; break;
         case SettingsSpec.IME_FIX:
            ClientConfig.imeFix = !ClientConfig.imeFix;
            ImeBridge.onConfigChanged();
            break;
         case SettingsSpec.MINING_PARTICLES: ClientConfig.hideMiningParticles = !ClientConfig.hideMiningParticles; break;
         case SettingsSpec.SPRINT_PARTICLES: ClientConfig.hideSprintParticles = !ClientConfig.hideSprintParticles; break;
         case SettingsSpec.FOV_SMOOTH: ClientConfig.fovSmooth = !ClientConfig.fovSmooth; break;
         case SettingsSpec.FOV_SPRINT_ON: ClientConfig.fovSprintOn = !ClientConfig.fovSprintOn; break;
         case SettingsSpec.FOV_SPEED_ON: ClientConfig.fovSpeedOn = !ClientConfig.fovSpeedOn; break;
         case SettingsSpec.FOV_SLOW_ON: ClientConfig.fovSlowOn = !ClientConfig.fovSlowOn; break;
         case SettingsSpec.FOV_FLY_ON: ClientConfig.fovFlyOn = !ClientConfig.fovFlyOn; break;
         case SettingsSpec.FOV_AIM_ON: ClientConfig.fovAimOn = !ClientConfig.fovAimOn; break;
         case SettingsSpec.FOV_WATER_ON: ClientConfig.fovWaterOn = !ClientConfig.fovWaterOn; break;
         case SettingsSpec.MODERN_FONT: ClientConfig.modernFont = !ClientConfig.modernFont; break;
         default: break;
      }
   }

   public static String valueText(int key) {
      switch (key) {
         case SettingsSpec.FOV_GLOBAL: return String.format("%.0f%%", ClientConfig.fovGlobal * 100f);
         case SettingsSpec.FOV_SPRINT: return String.format("%.0f", ClientConfig.fovSprint);
         case SettingsSpec.FOV_SPEED: return String.format("%.0f", ClientConfig.fovSpeed);
         case SettingsSpec.FOV_SLOW: return String.format("%.0f", ClientConfig.fovSlow);
         case SettingsSpec.FOV_FLY: return String.format("%.0f", ClientConfig.fovFly);
         case SettingsSpec.FOV_AIM: return String.format("%.0f", ClientConfig.fovAim);
         case SettingsSpec.FOV_WATER: return String.format("%.0f", ClientConfig.fovWater);
         case SettingsSpec.FOV_BASE: return String.format("%.0f", ClientConfig.fovBase);
         case SettingsSpec.FOG_DENSITY: return String.format("%.2f", ClientConfig.fogDensity);
         case SettingsSpec.TIME_TICKS: return ticksToClock(ClientConfig.timeTicks);
         case SettingsSpec.BLUR_AMOUNT: return String.format("%.0f%%", ClientConfig.blurAmount * 100f);
         case SettingsSpec.SCREEN_DIM: return com.amethyst.blur.Blur.radiusValue() + "%";
         case SettingsSpec.PANEL_OPACITY: return ClientConfig.panelOpacity + "%";
         case SettingsSpec.FADE_TIME: return com.amethyst.blur.Blur.fadeTimeValue() + " ms";
         case SettingsSpec.AF_LIFE: return ClientConfig.afterimageLife + " ms";
         case SettingsSpec.AF_STR: return String.format("%.2fx", ClientConfig.afterimageStrength);
         case SettingsSpec.AF_LAYERS: return Integer.toString(ClientConfig.afterimageLayers);
         case SettingsSpec.FIRE_HEIGHT: return ClientConfig.fireHeight + "%";
         case SettingsSpec.HAND_FWD: return handText(ClientConfig.handForward);
         case SettingsSpec.HAND_RIGHT: return handText(ClientConfig.handRight);
         case SettingsSpec.HAND_UP: return handText(ClientConfig.handUp);
         case SettingsSpec.CRIT_MULT: return String.format("%.1fx", ClientConfig.critParticleMultiplier);
         case SettingsSpec.SHARP_MULT: return String.format("%.1fx", ClientConfig.sharpParticleMultiplier);
         default: return "";
      }
   }

   public static String handText(float v) {
      return Math.abs(v) < 0.001f ? "0.00" : String.format("%+.2f", v);
   }

   public static float minOf(int key) {
      switch (key) {
         case SettingsSpec.FOV_GLOBAL: return 0.5f;
         case SettingsSpec.FOV_SPRINT: case SettingsSpec.FOV_SPEED: case SettingsSpec.FOV_SLOW: case SettingsSpec.FOV_FLY: case SettingsSpec.FOV_AIM: case SettingsSpec.FOV_WATER: return 10f;
         case SettingsSpec.FOV_BASE: return 30f;
         case SettingsSpec.FOG_DENSITY: return 0.5f;
         case SettingsSpec.TIME_TICKS: return 0f;
         case SettingsSpec.BLUR_AMOUNT: return 0.05f;
         case SettingsSpec.SCREEN_DIM: return 0f;
         case SettingsSpec.PANEL_OPACITY: return 0f;
         case SettingsSpec.FADE_TIME: return 0f;
         case SettingsSpec.AF_LIFE: return 200f;
         case SettingsSpec.AF_STR: return 0.2f;
         case SettingsSpec.AF_LAYERS: return 0f;
         case SettingsSpec.FIRE_HEIGHT: return 0f;
         case SettingsSpec.HAND_FWD: case SettingsSpec.HAND_RIGHT: case SettingsSpec.HAND_UP: return -2f;
         case SettingsSpec.CRIT_MULT: case SettingsSpec.SHARP_MULT: return 0f;
         default: return 0f;
      }
   }

   public static float maxOf(int key) {
      switch (key) {
         case SettingsSpec.FOV_GLOBAL: return 1.5f;
         case SettingsSpec.FOV_SPRINT: case SettingsSpec.FOV_SPEED: case SettingsSpec.FOV_SLOW: case SettingsSpec.FOV_FLY: case SettingsSpec.FOV_AIM: case SettingsSpec.FOV_WATER: return 160f;
         case SettingsSpec.FOV_BASE: return 110f;
         case SettingsSpec.FOG_DENSITY: return 30f;
         case SettingsSpec.TIME_TICKS: return 23999f;
         case SettingsSpec.BLUR_AMOUNT: return 1f;
         case SettingsSpec.SCREEN_DIM: return 1f;
         case SettingsSpec.PANEL_OPACITY: return 100f;
         case SettingsSpec.FADE_TIME: return 800f;
         case SettingsSpec.AF_LIFE: return 4000f;
         case SettingsSpec.AF_STR: return 2f;
         case SettingsSpec.AF_LAYERS: return 10f;
         case SettingsSpec.FIRE_HEIGHT: return 100f;
         case SettingsSpec.HAND_FWD: case SettingsSpec.HAND_RIGHT: case SettingsSpec.HAND_UP: return 2f;
         case SettingsSpec.CRIT_MULT: case SettingsSpec.SHARP_MULT: return 5f;
         default: return 1f;
      }
   }

   public static float stepOf(int key) {
      switch (key) {
         case SettingsSpec.FOV_GLOBAL: case SettingsSpec.BLUR_AMOUNT: case SettingsSpec.SCREEN_DIM: return 0.01f;
         case SettingsSpec.FOG_DENSITY: return 0.1f;
         case SettingsSpec.TIME_TICKS: return 100f;
         case SettingsSpec.AF_LIFE: return 10f;
         case SettingsSpec.PANEL_OPACITY: return 1f;
         case SettingsSpec.FADE_TIME: return 10f;
         case SettingsSpec.FIRE_HEIGHT: return 1f;
         case SettingsSpec.AF_STR: case SettingsSpec.CRIT_MULT: case SettingsSpec.SHARP_MULT: return 0.01f;
         case SettingsSpec.HAND_FWD: case SettingsSpec.HAND_RIGHT: case SettingsSpec.HAND_UP: return 0.01f;
         default: return 1f;
      }
   }

   public static float valueOf(int key) {
      switch (key) {
         case SettingsSpec.FOV_GLOBAL: return ClientConfig.fovGlobal;
         case SettingsSpec.FOV_SPRINT: return ClientConfig.fovSprint;
         case SettingsSpec.FOV_SPEED: return ClientConfig.fovSpeed;
         case SettingsSpec.FOV_SLOW: return ClientConfig.fovSlow;
         case SettingsSpec.FOV_FLY: return ClientConfig.fovFly;
         case SettingsSpec.FOV_AIM: return ClientConfig.fovAim;
         case SettingsSpec.FOV_WATER: return ClientConfig.fovWater;
         case SettingsSpec.FOV_BASE: return ClientConfig.fovBase;
         case SettingsSpec.FOG_DENSITY: return ClientConfig.fogDensity;
         case SettingsSpec.TIME_TICKS: return ClientConfig.timeTicks;
         case SettingsSpec.BLUR_AMOUNT: return ClientConfig.blurAmount;
         case SettingsSpec.SCREEN_DIM: return com.amethyst.blur.Blur.radiusValue() / 100f;
         case SettingsSpec.PANEL_OPACITY: return ClientConfig.panelOpacity;
         case SettingsSpec.FADE_TIME: return com.amethyst.blur.Blur.fadeTimeValue();
         case SettingsSpec.AF_LIFE: return ClientConfig.afterimageLife;
         case SettingsSpec.AF_STR: return ClientConfig.afterimageStrength;
         case SettingsSpec.AF_LAYERS: return ClientConfig.afterimageLayers;
         case SettingsSpec.FIRE_HEIGHT: return ClientConfig.fireHeight;
         case SettingsSpec.HAND_FWD: return ClientConfig.handForward;
         case SettingsSpec.HAND_RIGHT: return ClientConfig.handRight;
         case SettingsSpec.HAND_UP: return ClientConfig.handUp;
         case SettingsSpec.CRIT_MULT: return ClientConfig.critParticleMultiplier;
         case SettingsSpec.SHARP_MULT: return ClientConfig.sharpParticleMultiplier;
         default: return 0f;
      }
   }

   public static void setValue(int key, float v) {
      switch (key) {
         case SettingsSpec.FOV_GLOBAL: ClientConfig.fovGlobal = clampF(v, 0.5f, 1.5f); break;
         case SettingsSpec.FOV_SPRINT: ClientConfig.fovSprint = clampF(v, 10f, 160f); break;
         case SettingsSpec.FOV_SPEED: ClientConfig.fovSpeed = clampF(v, 10f, 160f); break;
         case SettingsSpec.FOV_SLOW: ClientConfig.fovSlow = clampF(v, 10f, 160f); break;
         case SettingsSpec.FOV_FLY: ClientConfig.fovFly = clampF(v, 10f, 160f); break;
         case SettingsSpec.FOV_AIM: ClientConfig.fovAim = clampF(v, 10f, 160f); break;
         case SettingsSpec.FOV_WATER: ClientConfig.fovWater = clampF(v, 10f, 160f); break;
         case SettingsSpec.FOV_BASE: ClientConfig.fovBase = clampF(v, 30f, 110f); break;
         case SettingsSpec.FOG_DENSITY: ClientConfig.fogDensity = clampF(v, 0.5f, 30f); break;
         case SettingsSpec.TIME_TICKS: ClientConfig.timeTicks = (int) clampF(v, 0f, 23999f); break;
         case SettingsSpec.BLUR_AMOUNT: ClientConfig.blurAmount = clampF(v, 0.05f, 1f); break;
         // 1.0.2_1：0% 就是关闭（不再强制最低 1%）。
         case SettingsSpec.SCREEN_DIM: com.amethyst.blur.Blur.setRadiusValue(Math.round(clampF(v, 0f, 1f) * 100f)); break;
         case SettingsSpec.PANEL_OPACITY: ClientConfig.panelOpacity = Math.round(clampF(v, 0f, 100f)); break;
         case SettingsSpec.FADE_TIME: com.amethyst.blur.Blur.setFadeTimeValue((int) clampF(v, 0f, 800f)); break;
         case SettingsSpec.AF_LIFE: ClientConfig.afterimageLife = (int) clampF(v, 200f, 4000f); break;
         case SettingsSpec.AF_STR: ClientConfig.afterimageStrength = clampF(v, 0.2f, 2f); break;
         case SettingsSpec.AF_LAYERS: ClientConfig.afterimageLayers = (int) clampF(v, 0f, 10f); break;
         case SettingsSpec.FIRE_HEIGHT: ClientConfig.fireHeight = (int) clampF(v, 0f, 100f); break;
         case SettingsSpec.HAND_FWD: ClientConfig.handForward = clampF(v, -2f, 2f); break;
         case SettingsSpec.HAND_RIGHT: ClientConfig.handRight = clampF(v, -2f, 2f); break;
         case SettingsSpec.HAND_UP: ClientConfig.handUp = clampF(v, -2f, 2f); break;
         case SettingsSpec.CRIT_MULT: ClientConfig.critParticleMultiplier = clampF(v, 0f, 5f); break;
         case SettingsSpec.SHARP_MULT: ClientConfig.sharpParticleMultiplier = clampF(v, 0f, 5f); break;
         default: break;
      }
   }

   public static float normOf(int key) {
      float min = minOf(key);
      return (valueOf(key) - min) / (maxOf(key) - min);
   }

   public static void applyNorm(int key, float norm) {
      float min = minOf(key);
      float max = maxOf(key);
      float step = stepOf(key);
      float raw = min + norm * (max - min);
      float snapped = min + Math.round((raw - min) / step) * step;
      setValue(key, snapped);
      ClientConfig.saveSoon();
   }

   public static int colorOf(int key) {
      switch (key) {
         case SettingsSpec.GLINT_COLOR: return ClientConfig.glintColor;
         case SettingsSpec.FOG_COLOR: return ClientConfig.fogColor;
         case SettingsSpec.HL_FILL: return ClientConfig.highlightFill;
         case SettingsSpec.HL_OUTLINE: return ClientConfig.highlightOutline;
         case SettingsSpec.NAME_BG: return ClientConfig.nameBgColor;
         case SettingsSpec.NAME_TEXT: return ClientConfig.nameTextColor;
         case SettingsSpec.CHAT_COLOR: return ClientConfig.chatBgColor;
         case SettingsSpec.SB_COLOR: return ClientConfig.scoreboardBgColor;
         case SettingsSpec.HURT_COLOR: return ClientConfig.hurtColor;
         case SettingsSpec.CROSSHAIR_COLOR: return ClientConfig.crosshairColor;
         case SettingsSpec.AF_COLOR: return ClientConfig.afterimageColor;
         case SettingsSpec.THEME_COLOR: return ClientConfig.themeColor;
         case SettingsSpec.GRADIENT_START: return com.amethyst.blur.Blur.gradientStartValue(false);
         case SettingsSpec.GRADIENT_END: return com.amethyst.blur.Blur.gradientStartValue(true);
         default: return -16777216;
      }
   }

   public static void setColor(int key, int argb) {
      switch (key) {
         case SettingsSpec.GLINT_COLOR: ClientConfig.glintColor = argb; break;
         case SettingsSpec.FOG_COLOR: ClientConfig.fogColor = argb; break;
         case SettingsSpec.HL_FILL: ClientConfig.highlightFill = argb; break;
         case SettingsSpec.HL_OUTLINE: ClientConfig.highlightOutline = argb; break;
         case SettingsSpec.NAME_BG: ClientConfig.nameBgColor = argb; break;
         case SettingsSpec.NAME_TEXT: ClientConfig.nameTextColor = argb; break;
         case SettingsSpec.CHAT_COLOR: ClientConfig.chatBgColor = argb; break;
         case SettingsSpec.SB_COLOR: ClientConfig.scoreboardBgColor = argb; break;
         case SettingsSpec.HURT_COLOR: ClientConfig.hurtColor = argb; break;
         case SettingsSpec.CROSSHAIR_COLOR: ClientConfig.crosshairColor = argb; break;
         case SettingsSpec.AF_COLOR: ClientConfig.afterimageColor = argb; break;
         case SettingsSpec.THEME_COLOR: ClientConfig.themeColor = 0xFF000000 | (argb & 0x00FFFFFF); break;
         case SettingsSpec.GRADIENT_START: com.amethyst.blur.Blur.setGradientColor(false, argb); break;
         case SettingsSpec.GRADIENT_END: com.amethyst.blur.Blur.setGradientColor(true, argb); break;
         default: break;
      }
   }

   public static String[] cycleNames(int key) {
      int lang = ClientConfig.langIndex();
      if (key == 30) {
         String[] out = new String[SettingsSpec.WEATHER_NAMES.length];
         for (int i = 0; i < out.length; i++) {
            out[i] = SettingsSpec.WEATHER_NAMES[i][lang];
         }
         return out;
      }
      if (key == 102) {
         String[] out = new String[SettingsSpec.BLUR_STYLE_NAMES.length];
         for (int i = 0; i < out.length; i++) {
            out[i] = SettingsSpec.BLUR_STYLE_NAMES[i][lang];
         }
         return out;
      }
      if (key == SettingsSpec.GLASS_MODE) {
         String[] out = new String[SettingsSpec.GLASS_MODE_NAMES.length];
         for (int i = 0; i < out.length; i++) {
            out[i] = SettingsSpec.GLASS_MODE_NAMES[i][lang];
         }
         return out;
      }
      return new String[0];
   }

   public static String cycleText(int key) {
      if (key == 30) {
         int i = idx(ClientConfig.weatherMode, SettingsSpec.WEATHER_NAMES.length);
         return SettingsSpec.WEATHER_NAMES[i][ClientConfig.langIndex()];
      }
      if (key == 102) {
         int i = idx(ClientConfig.blurStyle, SettingsSpec.BLUR_STYLE_NAMES.length);
         return SettingsSpec.BLUR_STYLE_NAMES[i][ClientConfig.langIndex()];
      }
      if (key == SettingsSpec.GLASS_MODE) {
         int i = idx(ClientConfig.glassMode, SettingsSpec.GLASS_MODE_NAMES.length);
         return SettingsSpec.GLASS_MODE_NAMES[i][ClientConfig.langIndex()];
      }
      return "";
   }

   public static int cycleIndex(int key) {
      if (key == 30) {
         return idx(ClientConfig.weatherMode, SettingsSpec.WEATHER_NAMES.length);
      }
      if (key == 102) {
         return idx(ClientConfig.blurStyle, SettingsSpec.BLUR_STYLE_NAMES.length);
      }
      if (key == SettingsSpec.GLASS_MODE) {
         return idx(ClientConfig.glassMode, SettingsSpec.GLASS_MODE_NAMES.length);
      }
      return 0;
   }

   public static void setCycle(int key, int index) {
      if (key == 30) {
         ClientConfig.weatherMode = index % SettingsSpec.WEATHER_NAMES.length;
      } else if (key == 102) {
         ClientConfig.blurStyle = index % SettingsSpec.BLUR_STYLE_NAMES.length;
      } else if (key == SettingsSpec.GLASS_MODE) {
         ClientConfig.glassMode = index % SettingsSpec.GLASS_MODE_NAMES.length;
      }
   }

   public static boolean maskAt(int row, int col) {
      String[] rows = ClientConfig.crosshairMask.split("/");
      return rows.length == 9 && row >= 0 && row < rows.length && col >= 0
              && col < rows[row].length() && rows[row].charAt(col) == '1';
   }

   public static void setMask(int row, int col, boolean on) {
      String[] rows = ClientConfig.crosshairMask.split("/");
      if (rows.length != 9) {
         return;
      }
      StringBuilder sb = new StringBuilder();
      for (int r = 0; r < 9; r++) {
         StringBuilder line = new StringBuilder(rows[r]);
         while (line.length() < 9) {
            line.append('0');
         }
         if (r == row) {
            line.setCharAt(col, (char) (on ? '1' : '0'));
         }
         if (r > 0) {
            sb.append('/');
         }
         sb.append(line, 0, 9);
      }
      ClientConfig.crosshairMask = sb.toString();
   }

   public static void drawChecker(Md3Canvas c, float x, float y, float w, float h, float cell) {
      int light = 0xFFE9E3EC;
      int dark = 0xFFCAC4D0;
      boolean flip = false;
      for (float yy = 0f; yy < h; yy += cell) {
         boolean rowFlip = flip;
         for (float xx = 0f; xx < w; xx += cell) {
            float cw = Math.min(cell, w - xx);
            float chh = Math.min(cell, h - yy);
            c.fill(x + xx, y + yy, cw, chh, rowFlip ? dark : light);
            rowFlip = !rowFlip;
         }
         flip = !flip;
      }
   }

   public static String ticksToClock(int ticks) {
      int totalMin = ticks % 24000 * 1440 / 24000;
      int hh = 6 + totalMin / 60;
      int mm = totalMin % 60;
      return String.format("%02d:%02d", hh % 24, mm);
   }

   public static int idx(int value, int len) {
      return value >= 0 && value < len ? value : 0;
   }

   public static float clampF(float v, float min, float max) {
      return v < min ? min : (v > max ? max : v);
   }

   public static float clamp01(float v) {
      return v < 0f ? 0f : (v > 1f ? 1f : v);
   }

   public static float[] argbToHsv(int argb) {
      float r = (argb >> 16 & 0xFF) / 255f;
      float g = (argb >> 8 & 0xFF) / 255f;
      float b = (argb & 0xFF) / 255f;
      float max = Math.max(r, Math.max(g, b));
      float min = Math.min(r, Math.min(g, b));
      float h;
      if (max <= 0f) {
         h = 0f;
      } else if (max == r) {
         h = ((g - b) / (max - min) / 6f + 1f) % 1f;
      } else if (max == g) {
         h = ((b - r) / (max - min) + 2f) / 6f;
      } else {
         h = ((r - g) / (max - min) + 4f) / 6f;
      }
      if (max - min < 1f / 100000f) {
         h = 0f;
      }
      float s = max <= 0f ? 0f : (max - min) / max;
      return new float[]{h, s, max};
   }

   public static int hsvToArgb(float h, float s, float v, int a) {
      float hh = (h - (float) Math.floor(h)) * 6f;
      int i = (int) hh;
      float f = hh - i;
      float p = v * (1f - s);
      float q = v * (1f - s * f);
      float t = v * (1f - s * (1f - f));
      float r;
      float g;
      float b;
      switch (i % 6) {
         case 0: r = v; g = t; b = p; break;
         case 1: r = q; g = v; b = p; break;
         case 2: r = p; g = v; b = t; break;
         case 3: r = p; g = q; b = v; break;
         case 4: r = t; g = p; b = v; break;
         default: r = v; g = p; b = q; break;
      }
      return (a & 0xFF) << 24
              | (Math.round(r * 255f) & 0xFF) << 16
              | (Math.round(g * 255f) & 0xFF) << 8
              | (Math.round(b * 255f) & 0xFF);
   }
}
