package net.client.mixin;

import java.util.ArrayList;
import java.util.List;
import net.client.ClientConfig;
import net.client.util.HudFill;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {InGameHud.class},
   priority = 1200
)
public class InGameHudMixin {
   /** 原版受击/回血时的心形闪动计时器（1.8.9 InGameHud#heartJumpEndTick）。 */
   @Shadow
   private long heartJumpEndTick;

   /**
    * 「关闭受击闪烁」：原版在掉血/回血时把 heartJumpEndTick 设成 ticks + 20/+10，
    * 之后 renderStatusBars 里的 bl 就会让心形左右抖动并叠加闪白的那一帧。
    * 该开关开启时每帧先把计时器清零，bl 恒为 false，血条就不再闪。
    */
   @Inject(method = {"renderStatusBars"}, at = {@At("HEAD")}, require = 1)
   private void client$noHeartBlink(Window window, CallbackInfo ci) {
      if (ClientConfig.hurtBlinkOff) {
         this.heartJumpEndTick = 0L;
      }
   }
   @Redirect(
      method = {"renderScoreboardObjective"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/InGameHud;fill(IIIII)V"
      )
   )
   private void client$scoreboardBackground(int x1, int y1, int x2, int y2, int color) {
      if (ClientConfig.scoreboardBackground) {
         color = ClientConfig.scoreboardBgColor;
      }

      HudFill.fill(x1, y1, x2, y2, color);
   }

   @Inject(
      method = {"renderScoreboardObjective"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void client$hideScoreboard(ScoreboardObjective objective, Window window, CallbackInfo ci) {
      if (ClientConfig.hideScoreboard) {
         ci.cancel();
         return;
      }

      // 1.0.1：自定义计分板由下面的渲染器接管。这里直接取消原版绘制，避免
      // 两套计分板叠在一起；自定义模式下仍然尊重“隐藏计分板”开关。
      if (ClientConfig.scoreboardCustom) {
         ci.cancel();
      }
   }

   /**
    * 现代字体接管后，原版计分板文字色 0x20FFFFFF 的 alpha 会再乘一次字体图集的
    * 抗锯齿 alpha，看起来像被整块稀释。只在计分板这条渲染路径里把文字 alpha
    * 抬到 FF；其它 HUD 颜色不受影响。
    */
   @Redirect(
      method = {"renderScoreboardObjective"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I"
      ),
      require = 0
   )
   private int client$hideRedNumbers(TextRenderer fontRenderer, String text, int x, int y, int color) {
      if (ClientConfig.hideScoreNumbers && text != null && text.startsWith("§c")) {
         return 0;
      }

      int argb = ClientConfig.modernFont ? (color | 0xFF000000) : color;
      return fontRenderer.draw(text, x, y, argb);
   }

   private void client$renderCustomScoreboard(ScoreboardObjective objective, Window window) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || window == null) {
         return;
      }

      TextRenderer font = mc.textRenderer;
      if (font == null) {
         return;
      }

      int maxLines = ClientConfig.scoreboardMaxLines;
      if (maxLines < 1) {
         maxLines = 1;
      } else if (maxLines > 15) {
         maxLines = 15;
      }

      // 自定义内容优先：每行一个字符串，name:score 用冒号分两列。
      // 内容为空时才回退到服务端 objective 的玩家分数列表。
      List<String[]> rows = new ArrayList<String[]>();
      String customText = ClientConfig.scoreboardText;
      boolean literal = customText != null && !customText.trim().isEmpty();
      if (literal) {
         String[] split = customText.replace("\r", "").split("\n");
         List<String> lines = new ArrayList<String>();
         for (String raw : split) {
            if (raw != null && !raw.trim().isEmpty()) {
               lines.add(raw.trim());
            }
         }
         if (lines.size() > maxLines) {
            lines = new ArrayList<String>(lines.subList(0, maxLines));
         }
         for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon > 0 && colon < line.length() - 1) {
               if (ClientConfig.hideScoreNumbers) {
                  rows.add(new String[]{line.substring(0, colon).trim(), null});
               } else {
                  rows.add(new String[]{line.substring(0, colon).trim(), line.substring(colon + 1).trim()});
               }
            } else {
               rows.add(new String[]{line, null});
            }
         }
      } else {
         if (objective == null) {
            return;
         }

         Scoreboard scoreboard = objective.getScoreboard();
         List<ScoreboardPlayerScore> scores = new ArrayList<ScoreboardPlayerScore>();
         for (ScoreboardPlayerScore score : scoreboard.getAllPlayerScores(objective)) {
            if (score != null && score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
               scores.add(score);
            }
         }
         if (scores.size() > maxLines) {
            scores = new ArrayList<ScoreboardPlayerScore>(scores.subList(scores.size() - maxLines, scores.size()));
         }

         boolean showNames = ClientConfig.scoreboardShowNames;
         boolean showScores = ClientConfig.scoreboardShowScores;
         if (!showNames && !showScores) {
            showNames = true;
         }
         for (ScoreboardPlayerScore score : scores) {
            String name = Team.decorateName(scoreboard.getPlayerTeam(score.getPlayerName()), score.getPlayerName());
            boolean wantNames = showNames;
            boolean wantScores = showScores && !ClientConfig.hideScoreNumbers;
            if (!wantNames && !wantScores) {
               wantNames = true;
            }
            if (wantNames && wantScores) {
               rows.add(new String[]{name, this.client$scoreText(score)});
            } else if (wantNames) {
               rows.add(new String[]{name, null});
            } else {
               rows.add(new String[]{null, this.client$scoreText(score)});
            }
         }
      }

      String title = ClientConfig.scoreboardTitle;
      if (title == null || title.trim().isEmpty()) {
         title = objective == null ? "" : objective.getDisplayName();
      }
      boolean showTitle = ClientConfig.scoreboardShowTitle && title != null && !title.isEmpty();

      int width = showTitle ? font.getStringWidth(title) : 0;
      for (String[] row : rows) {
         int lineWidth = row[0] == null ? 0 : font.getStringWidth(row[0]);
         if (row[1] != null) {
            lineWidth += font.getStringWidth("  ") + font.getStringWidth(row[1]);
         }
         width = Math.max(width, lineWidth);
      }
      if (width <= 0) {
         return;
      }

      int fontHeight = font.fontHeight;
      int y = window.getHeight() / 2 + rows.size() * fontHeight / 3;
      int x = window.getWidth() - width - 3;
      int right = window.getWidth() - 1;
      int bg = ClientConfig.scoreboardBackground ? ClientConfig.scoreboardBgColor : 0x50000000;

      for (int i = 0; i < rows.size(); i++) {
         String[] row = rows.get(i);
         int lineY = y - (i + 1) * fontHeight;
         HudFill.fill(x - 2, lineY, right, lineY + fontHeight, bg);
         if (row[0] != null) {
            font.draw(row[0], x, lineY, 0xFFFFFFFF);
         }
         if (row[1] != null) {
            int rightWidth = font.getStringWidth(row[1]);
            font.draw(row[1], right - rightWidth - 1, lineY, 0xFFFFFFFF);
         }
      }

      if (showTitle) {
         int titleY = y - rows.size() * fontHeight - fontHeight - 1;
         HudFill.fill(x - 2, titleY, right, y - rows.size() * fontHeight - 1, bg);
         int titleX = x + (width - font.getStringWidth(title)) / 2;
         font.draw(title, titleX, titleY, 0xFFFFFFFF);
      }
   }

   private String client$customLine(Scoreboard scoreboard, ScoreboardPlayerScore score,
            boolean showNames, boolean showScores) {
      String name = Team.decorateName(scoreboard.getPlayerTeam(score.getPlayerName()), score.getPlayerName());
      if (showNames && showScores) {
         return name;
      }
      if (showScores) {
         return this.client$scoreText(score);
      }
      return name;
   }

   private String client$scoreText(ScoreboardPlayerScore score) {
      return "§c" + score.getScore();
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void client$renderCustomScoreboardEarly(float tickDelta, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (ClientConfig.scoreboardCustom
            && !ClientConfig.hideScoreboard
            && mc != null && mc.world != null && mc.player != null
            && !mc.options.hudHidden) {
         ScoreboardObjective objective = mc.world.getScoreboard().getObjectiveForSlot(1);
         this.client$renderCustomScoreboard(objective, new Window(mc));
      }
   }

   @Inject(
      method = {"showCrosshair"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$hideVanillaCrosshair(CallbackInfoReturnable<Boolean> cir) {
      if (ClientConfig.customCrosshair) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void client$drawCrosshair(float delta, CallbackInfo ci) {
      if (ClientConfig.customCrosshair) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.world != null && client.player != null && !client.options.hudHidden) {
            String[] rows = ClientConfig.crosshairMask.split("/");
            if (rows.length == 9) {
               Window window = new Window(client);
               int cx = window.getWidth() / 2 - 4;
               int cy = window.getHeight() / 2 - 4;

               for (int r = 0; r < 9; r++) {
                  String row = rows[r];

                  for (int c = 0; c < 9 && c < row.length(); c++) {
                     if (row.charAt(c) == '1') {
                        HudFill.fill(cx + c, cy + r, cx + c + 1, cy + r + 1, ClientConfig.crosshairColor);
                     }
                  }
               }
            }
         }
      }
   }
}
