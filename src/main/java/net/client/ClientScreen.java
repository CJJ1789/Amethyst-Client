package net.client;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3IconButton;
import net.client.ui.md3.widget.Md3Menu;
import net.client.ui.md3.widget.Md3Surfaces;
import net.client.ui.md3.widget.Md3Switch;
import net.client.ui.md3.widget.Md3Text;
import net.client.ui.md3.widget.Md3TextField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.client.ime.ImeBridge;
import net.client.settings.SettingsSpec;
import net.client.settings.SettingsValues;
import net.client.ScoreboardSettingsScreen;
import net.client.ui.ClientIcons;
import net.client.ui.ClientScroll;
import net.client.ui.ClientTheme;
import net.client.ui.ClientGlass;
import net.client.ui.ClientUi;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Client settings rebuilt on the Material 3 library.
 *
 * <p>Top app bar (title / search / overflow / theme / close), a scrollable
 * category tab strip, and an aligned three-column grid of compact setting
 * cards. One card is one setting, every card has the same height, so rows line
 * up exactly like the reference mod grid instead of a masonry layout.</p>
 */
public class ClientScreen extends Screen {
   /**
    * 调色板预设。1.0.2_19：原来是一组偏灰的 Material 400（#90A4AE 蓝灰、#8D6E63 土棕、
    * #5C6BC0 暗靛蓝…），在深色面板上又脏又难认。换成 12 个标准色：
    * 白 / 黑 / 红 / 橙 / 黄 / 绿 / 青 / 蓝 / 紫 / 粉 / 灰 / 棕。
    */
   private static final int[] PRESETS = new int[]{
      0xFFFFFFFF, 0xFF000000, 0xFFF44336, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50,
      0xFF00BCD4, 0xFF2196F3, 0xFF9C27B0, 0xFFE91E63, 0xFF9E9E9E, 0xFF795548
   };
   private static final String DEFAULT_MASK =
      "000010000/000010000/000010000/000010000/111111111/000010000/000010000/000010000/000010000";

   private final Md3Canvas canvas = new Md3Canvas();
   private final ClientScroll scroll = new ClientScroll();
   private final List<Tile> tiles = new ArrayList<Tile>();
   private final List<TileBox> tileBoxes = new ArrayList<TileBox>();
   private final List<TabBox> tabBoxes = new ArrayList<TabBox>();
   private final Md3TextField search = new Md3TextField();
   private final Map<Integer, Md3Switch> switches = new HashMap<Integer, Md3Switch>();
   private final Map<Integer, Boolean> lastSwitchOn = new HashMap<Integer, Boolean>();
   private final List<RowBox> boxes = new ArrayList<RowBox>();
   private final Md3IconButton themeButton = new Md3IconButton();
   private final Md3IconButton closeButton = new Md3IconButton();
   private final Md3IconButton menuButton = new Md3IconButton();
   private final Md3IconButton backButton = new Md3IconButton();
   private final Md3Menu appMenu = new Md3Menu();
   private boolean appMenuOpen;
   /** 下拉菜单打开时，底下的卡片/行不该再有悬停高亮（菜单是模态的）。 */
   private boolean hoverBlocked;

   private List<Group> groups = new ArrayList<Group>();
   private int selectedCat = 0;
   private int detailGid = -1;
   private Group detailGroup;

   private long lastNanos;
   private float dt;
   private int openFrames;
   private float mouseNowX;
   private float mouseNowY;
   private boolean mouseDown;

   private float appBarX;
   private float appBarY;
   private float appBarW;
   private float appBarH;
   private float panelX;
   private float panelY;
   private float panelW;
   private float panelH;
   private float tileH = 60f;
   private float stackTitleCy;
   private float stackSubCy;
   /** 纵向堆叠卡片的左右留白与图标边长（随卡片宽度微调）。 */
   private float stackPad = 9f;
   private float stackIcon = 22f;
   private float contentX;
   private float contentY;
   private float contentW;
   private float contentH;
   private float tabsX;
   private float tabsY;
   private float tabsW;
   private float tabsH = 36f;
   private float tabScrollX;
   private float tabsContentW;
   private float cardsTotalHeight;
   private float menuBtnX;
   private float menuBtnY;
   private float menuBtnS = 38f;
   private float themeBtnX;
   private float themeBtnY;
   private float themeBtnS = 38f;
   private float closeBtnX;
   private float closeBtnY;
   private float closeBtnS = 38f;
   private float searchX;
   private float searchY;
   private float searchW;
   private float searchH = 44f;
   private final float headerH = 56f;

   private boolean searchFocus;
   private String searchText = "";

   private int sliderKey = -1;
   private float sliderNorm;
   private int sliderStartX;
   private float sliderStartNorm;

   private boolean bindingKey;

   private int pickerKey = -1;
   private float pickerH;
   private float pickerS;
   private float pickerV;
   private int pickerA;
   private int pickerDrag = -1;
   private float dialogX;
   private float dialogY;
   private float dialogW;
   private float dialogH;
   private float pickerBarX;
   private float pickerBarY;
   private float pickerBarW;
   private float pickerBarStep = 28f;

   private boolean crosshairEditor;
   private int editorDrag = -1;

   private int menuKey = -1;
   private final Md3Menu menu = new Md3Menu();
   private int menuIndex = -1;

   private long resetConfirmUntil;
   private String snack;
   private long snackUntil;

   public ClientScreen() {
      ClientConfig.ensureLoaded();
   }

   /* ------------------------------------------------------------------ */
   /* lifecycle                                                          */
   /* ------------------------------------------------------------------ */

   @Override
   public void removed() {
      ClientConfig.save();
      super.removed();
   }

   public void init() {
      this.buttons.clear();
      this.buildGroups();
      this.applyFilter();
      this.detailGroup = this.findGroup(this.detailGid);
      if (this.detailGid >= 0 && this.detailGroup == null) {
         this.detailGid = -1;
      }
      this.lastNanos = 0L;
      this.openFrames = 0;

      // 1.0.2_1：尺寸完全沿用「已安装模组」那张卡（ModListScreen.init 的同一组常量），
      // 不再自己算百分比/宽高比。两个页面必须长得一模一样，只改一处就同步。
      this.panelW = ModListScreen.panelWidth(this.width);
      this.panelH = ModListScreen.panelHeight(this.height);
      this.panelX = (this.width - this.panelW) / 2f;
      this.panelY = (this.height - this.panelH) / 2f;

      this.appBarX = this.panelX + 12f;
      this.appBarY = this.panelY + 8f;
      this.appBarW = this.panelW - 24f;
      this.appBarH = 44f;

      this.closeBtnS = 32f;
      this.themeBtnS = 32f;
      this.menuBtnS = 32f;
      this.searchH = 28f;
      this.tabsH = 30f;

      this.closeBtnX = this.appBarX + this.appBarW - 10f - this.closeBtnS;
      this.closeBtnY = this.appBarY + (this.appBarH - this.closeBtnS) / 2f;
      this.themeBtnX = this.closeBtnX - 4f - this.themeBtnS;
      this.themeBtnY = this.closeBtnY;
      this.menuBtnX = this.themeBtnX - 4f - this.menuBtnS;
      this.menuBtnY = this.closeBtnY;

      this.searchX = this.appBarX + (this.showLogo() ? 42f : 6f);
      this.searchW = Math.max(96f, this.menuBtnX - 10f - this.searchX);
      this.searchY = this.appBarY + (this.appBarH - this.searchH) / 2f;

      this.tabsX = this.panelX + 12f;
      this.tabsY = this.panelY + this.appBarH + 7f;
      this.tabsW = this.panelW - 24f;
      this.contentX = this.panelX + 12f;
      this.contentY = this.tabsY + this.tabsH + 7f;
      this.contentW = this.panelW - 24f - 12f;
      this.contentH = Math.max(90f, this.panelY + this.panelH - 12f - this.contentY);

      this.search.variant = Md3TextField.Variant.OUTLINED;
      this.search.label = "";
      this.search.supporting = "";
      this.search.leadingIcon = "search";
      this.search.trailingIcon = null;
      this.search.maxLength = 32;
      this.search.setBounds(this.searchX, this.searchY, this.searchW, this.searchH);
      this.search.focused = this.searchFocus;

      this.themeButton.variant = Md3IconButton.Variant.STANDARD;
      this.themeButton.setBounds(this.themeBtnX, this.themeBtnY, this.themeBtnS, this.themeBtnS);
      this.closeButton.variant = Md3IconButton.Variant.STANDARD;
      this.closeButton.icon = "close";
      this.closeButton.setBounds(this.closeBtnX, this.closeBtnY, this.closeBtnS, this.closeBtnS);
      this.menuButton.variant = Md3IconButton.Variant.STANDARD;
      this.menuButton.icon = "more_vert";
      this.menuButton.setBounds(this.menuBtnX, this.menuBtnY, this.menuBtnS, this.menuBtnS);
      this.backButton.variant = Md3IconButton.Variant.STANDARD;
      this.backButton.icon = "arrow_back";
      this.backButton.setBounds(this.appBarX + 8f,
              this.appBarY + (this.appBarH - this.menuBtnS) / 2f, this.menuBtnS, this.menuBtnS);

      this.scroll.jump(0f);
      this.tabScrollX = 0f;
      this.appMenuOpen = false;
   }

   private void buildGroups() {
      this.groups.clear();
      Group current = null;
      // 1.0.2_2：按 SettingsSpec 切分阶段记下的 UNIT_HEAD 来分组，不再用「紧跟上一个开关」猜，
      // 那条规则在 A→Z 重排后会错误地把两个无关的项粘成一张卡。
      for (int i = 0; i < SettingsSpec.ROWS.length; i++) {
         int[] row = SettingsSpec.ROWS[i];
         if (row[0] == SettingsSpec.K_HEADER) {
            current = new Group(row[2]);
            this.groups.add(current);
            continue;
         }
         if (current == null) {
            continue;
         }
         Row item = new Row(row[0], row[2]);
         if (SettingsSpec.UNIT_HEAD[i] || current.units.isEmpty()) {
            current.units.add(new Unit(item));
         } else {
            current.units.get(current.units.size() - 1).rows.add(item);
         }
      }
   }

   private Group findGroup(int gid) {
      for (Group group : this.groups) {
         if (group.id == gid) {
            return group;
         }
      }
      return null;
   }

   private void applyFilter() {
      this.tiles.clear();
      String needle = this.searchText.trim().toLowerCase();
      if (!needle.isEmpty()) {
         this.selectedCat = 0;
      }
      for (Group group : this.groups) {
         if (this.selectedCat != 0 && this.categoryOf(group.id) != this.selectedCat) {
            continue;
         }
         if (needle.isEmpty() || this.groupMatches(group, needle)) {
            this.tiles.add(new Tile(group));
         }
      }
      boolean showReplay = net.client.replay.ReplayClient.isReady()
              && (needle.isEmpty() || needle.contains("回放") || needle.contains("replay"))
              && (this.selectedCat == 0 || this.selectedCat == 5);
      if (showReplay) {
         // 设置卡片按英文名 A→Z 排列，回放卡片插到 Replay 该在的位置，不能放第一张。
         String replayKey = (this.client != null && this.client.world != null)
                 ? "Replay recording" : "Replay console";
         int pos = this.tiles.size();
         for (int i = 0; i < this.tiles.size(); i++) {
            Group g = this.tiles.get(i).group;
            String en = g == null ? "" : g.nameEn();
            if (en != null && en.compareToIgnoreCase(replayKey) > 0) {
               pos = i;
               break;
            }
         }
         this.tiles.add(pos, new Tile(null));
      }
      this.scroll.jump(0f);
   }

   private boolean groupMatches(Group group, String needle) {
      String names = (group.nameZh() + " " + group.nameEn()).toLowerCase();
      if (names.contains(needle)) {
         return true;
      }
      for (Unit unit : group.units) {
         if (this.label(unit.head.key).toLowerCase().contains(needle)) {
            return true;
         }
         for (Row row : unit.rows) {
            if (this.label(row.key).toLowerCase().contains(needle)) {
               return true;
            }
         }
      }
      return false;
   }

   /** Broad tab buckets, deliberately coarser than the 23 setting groups. */
   private int categoryOf(int gid) {
      switch (gid) {
         case SettingsSpec.G_BOB:
         case SettingsSpec.G_FOV:
         case SettingsSpec.G_CROSSHAIR:
         case SettingsSpec.G_HAND:
         case SettingsSpec.G_FREELOOK:
            return 2; // camera / view
         case SettingsSpec.G_NAME:
         case SettingsSpec.G_CHAT:
         case SettingsSpec.G_SB:
         case SettingsSpec.G_SCREEN:
            return 3; // HUD
         case SettingsSpec.G_SPRINT:
         case SettingsSpec.G_MOUSE:
            return 4; // input
         default:
            return 1; // display / render
      }
   }

   private String categoryLabel(int index) {
      switch (index) {
         case 1: return "DISPLAY";
         case 2: return "CAMERA";
         case 3: return "HUD";
         case 4: return "INPUT";
         case 5: return "MISC";
         default: return "ALL";
      }
   }

   private boolean matchesUnit(Unit unit, String needle) {
      if (this.label(unit.head.key).toLowerCase().contains(needle)) {
         return true;
      }
      for (Row row : unit.rows) {
         if (this.label(row.key).toLowerCase().contains(needle)) {
            return true;
         }
      }
      return false;
   }

   /* ------------------------------------------------------------------ */
   /* render                                                             */
   /* ------------------------------------------------------------------ */

   @Override
   public void render(int mouseX, int mouseY, float tickDelta) {
      this.hoverBlocked = this.appMenuOpen;
      this.mouseNowX = mouseX;
      this.mouseNowY = mouseY;
      long now = System.nanoTime();
      this.dt = this.lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - this.lastNanos) / 1.0E9f);
      this.lastNanos = now;
      this.openFrames++;

      super.render(mouseX, mouseY, tickDelta);

      Md3Theme t = ClientTheme.get();
      // 1.0.2_13：背景模糊改由 Blur 模组负责（用户要求「用 blur 的模糊」）。
      // Blur 是挂在 Screen.renderBackground 的 HEAD 上做后处理，1.8.9 的 Screen.render
      // 自己不会调 renderBackground，所以这里必须显式调一次，否则模糊永远不生效。
      // 1.0.2_22：设置页不铺全景。世界内直接透出场景（Blur 负责糊）；
      // 从主菜单打开时铺静态底色（surface + 淡主/次色光晕），不再让那套旋转全景动来动去。
      // 1.0.2_24：这块静态底色恢复成带光晕的旧观感，不再是单色。
      this.canvas.begin(this.width, this.height);
      ClientUi.backdrop(this.canvas, t, this.width, this.height,
              this.client != null && this.client.world != null);
      // 抓屏放在 backdrop 之后：玻璃取样带上那层压色，面板才不会比周围亮出一截，
      // 白字压上去仍有对比（只用到「干净背景」时整块面板会发白、卡片和文字都糊）。
      if (ClientGlass.ENABLE_BLUR && this.glassStrength() > 0.02f) {
         ClientGlass.capture(this.canvas);
         ClientGlass.blur(this.panelOpacity() > 0.5f ? 2 : 3);
      }
      this.renderPanel(t);

      if (this.detailGid >= 0) {
         this.renderDetailAppBar(t, mouseX, mouseY);
         this.renderDetailContent(t, mouseX, mouseY);
      } else {
         this.renderAppBar(t, mouseX, mouseY);
         this.renderTabs(t, mouseX, mouseY);
         this.renderContent(t, mouseX, mouseY);
         this.renderAppMenu(t, mouseX, mouseY);
      }
      this.renderSnack(t);
      this.renderMenu(t, mouseX, mouseY);
      if (this.pickerKey >= 0) {
         this.renderColorDialog(t, mouseX, mouseY);
      }
      if (this.crosshairEditor) {
         this.renderCrosshairDialog(t, mouseX, mouseY);
      }

      this.canvas.end();
   }

   /**
    * 1.0.2_2：菜单黑色的浓度 = 面板透明度的反向。
    * 面板 100% 不透明时自身就是一块实底，菜单不需要额外压黑；
    * 面板越透，透出来的场景越花，菜单就越需要黑底把文字衬住。
    * 亮色主题文字是深色，黑底上限压得低一些，不然会反过来糊。
    */
   private float menuVeil() {
      float opaque = Math.max(0f, Math.min(1f, ClientConfig.panelOpacity / 100f));
      float max = ClientTheme.dark() ? 0.68f : 0.26f;
      return max * (1f - opaque);
   }

   /** 1.0.2_1：按「背景不透明度」把面板与内部色块整体降 alpha，透出后面的模糊背景。 */
   private int panelSurface(int color) {
      int opacity = ClientConfig.panelOpacity;
      if (opacity >= 100) {
         return color;
      }
      if (opacity <= 0) {
         // 不能用 0：Md3TextField / Md3IconButton 把 surface==0 当成「没设过底色」，
         // 会回落到不透明的 colors.surface，于是透明模式下搜索框和右上三个圆按钮反而最实。
         // 给 1/255 的 alpha，肉眼完全看不见，但组件会老老实实用它。
         return Md3Colors.alpha(color, 1);
      }
      return Md3Colors.alpha(color, Math.round(opacity * 255f / 100f));
   }

   /**
    * 1.0.2_4：控件背后那层「二次毛玻璃」的实度 = 面板不透明度的反向。
    * 面板 100% 不透明时控件自己就是实底，不需要玻璃；面板越透，
    * 控件底下的背景越花，就叠得越实（clientGlass 采样「控件底下已经画好的画面」再做 9 点平均，
    * 等于在整屏模糊之上再糊一层，磨砂玻璃片而不是一块黑砖）。
    */
   private float glassStrength() {
      float opaque = Math.max(0f, Math.min(1f, ClientConfig.panelOpacity / 100f));
      return (1f - opaque) * 0.9f;
   }

   /** 控件底色：1.0.2_4 起不再垫黑，只做透明度联动，玻璃层由 glass() 单独画。 */
   private int controlSurface(int color) {
      return this.panelSurface(color);
   }

   /**
    * 1.0.2_11：卡片/控件统一「比面板亮一档」，不再用 surfaceContainerHigh。
    * 面板透明以后，原来的 Low(面板)+High(卡片) 在亮背景下会反转：面板被背景顶亮、
    * 卡片还是那块暗色，看着就成了一个个凹进去的坑。这里直接用「面板色 + 一点白」
    * 派生，亮暗两种主题下都保证卡片比面板亮。
    */
   private int raisedSurface(Md3Theme t, float lift) {
      return canvasSurface(Md3Colors.composite(Md3Colors.alpha(0xFFFFFF, lift),
              t.colors.surfaceContainer));
   }

   /**
    * 1.0.2_20：顶栏控件（搜索框 / 三个圆按钮 / 分类胶囊）专用表面。
    * canvasSurface() 为了卡片不糊成一片留了 78% 的透明度保底，这几个控件跟着它走，
    * 结果面板已经全透了、顶栏还是几块实心色。这里改成完全跟随「背景不透明度」。
    */
   private int clearRaised(Md3Theme t, float lift) {
      return this.panelSurface(Md3Colors.composite(Md3Colors.alpha(0xFFFFFF, lift),
              t.colors.surfaceContainer));
   }

   private int canvasSurface(int color) {
      // 卡片/控件要“浮”在面板上，透明度不能跟面板一样透：
      // 面板按 panelOpacity 透出模糊背景，控件至少 55% 实，否则两者糊成一片、看不出层级。
      int opacity = Math.max(ClientConfig.panelOpacity, 78);
      if (opacity >= 100) {
         return color;
      }
      return Md3Colors.alpha(color, Math.round(opacity * 255f / 100f));
   }

   /**
    * 1.0.2_9：面板本体的「液态玻璃」。
    * 之前只有控件底下有玻璃，面板本身只是一块半透明色块 —— 越透就越像直接贴了张
    * 场景截图。这里把整块面板也按「背景不透明度」反向糊：不透明度 100% 时不画（实底
    * 不需要），越透糊得越狠（取样半径 2→26px、叠 2→3 遍），透到 0% 时面板就成了一块
    * 磨砂玻璃，透出来的场景被真正模糊掉，而不是原样透出来。
    */
   private void panelGlass(Md3Theme t) {
      float clear = 1f - this.panelOpacity();
      if (clear <= 0.02f) {
         return;
      }
      // 玻璃本体只是一层很淡的白；面板颜色由上面那层 panelSurface 叠加。
      int tint = Md3Colors.alpha(0xFFFFFF, (int) (14 + 10 * clear));
      if (!ClientGlass.blurred()) {
         // 没有 GPU 管线时的旧路径（取样叠加）。
         ClientGlass.drawRound(this.canvas, this.panelX, this.panelY, this.panelW, this.panelH,
               24f, 1f, tint, 0xFFFFFF, 2, 8f + 16f * clear, 0.34f + 0.26f * clear);
         return;
      }
      // refract：边缘折射位移（物理像素）—— 越透玻璃越「厚」。
      float refract = (7f + 21f * clear) * this.canvas.pixelScale;
      float lift = 0.14f + 0.20f * clear;
      ClientGlass.drawPanel(this.canvas, this.panelX, this.panelY, this.panelW, this.panelH,
            24f, 1f, tint, refract, lift);
   }

   /** 面板不透明度 0~1。 */
   private float panelOpacity() {
      return Math.max(0f, Math.min(1f, ClientConfig.panelOpacity / 100f));
   }

   /** 在控件底下铺一层毛玻璃（调用顺序：先 glass() 再画控件本体）。 */
   private void glass(Md3Theme t, float x, float y, float w, float h, float radius) {
      float s = this.glassStrength();
      if (s <= 0.02f) {
         return;
      }
      // 玻璃本体偏面板色（33% 左右），取样叠加用白色调制 —— 用 surface 调会被乘成暗色，
      // 控件整块比周围背景暗，卡片之间就显出一道道暗竖线。
      int tint = Md3Colors.alpha(0xFFFFFF, ClientTheme.dark() ? 7 : 16);
      ClientGlass.drawRound(this.canvas, x, y, w, h, radius, s, tint, 0xFFFFFF);
   }

   /**
    * 1.0.2_1：卡片从「凹」改成「凸」。
    * 原来卡片用 surfaceContainerLow 直接压在 surfaceContainerHigh 面板上，同色系差一档、
    * 又没有投影和描边，视觉上就是挖进去的坑。模组菜单那种浮起来的感觉靠三点：
    * 底色比面板再亮一点点（叠一层 4% onSurface）+ 一圈很淡的外阴影 + 1px 浅描边。
    */
   private void drawTileCard(Md3Theme t, float x, float y, float w, float h, boolean hot) {
      // 1.0.2_19：卡片原来在 surfaceContainer 上叠 30% 白，比设置页的行卡片（只叠 10%）
      // 明显浅一档，同一套 UI 里出现两种灰度。这里对齐成行卡片的 10%。
      int bgBase = Md3Colors.composite(Md3Colors.alpha(0xFFFFFF, 0.10f), t.colors.surfaceContainer);
      int bg = hot ? Md3Colors.composite(Md3Colors.alpha(t.colors.primary, 0.10f), bgBase) : bgBase;
      this.glass(t, x, y, w, h, 18f);
      this.canvas.roundRect(x, y, w, h, 18f, this.controlSurface(bg));
      if (hot) {
         // 1.0.2_2：悬停反馈是交互状态，不跟「背景不透明度」一起淡出。
         // 之前这里也过分到 panelSurface，不透明度 0 时鼠标压上去整张卡片一点反应都没有。
         this.canvas.roundRect(x, y, w, h, 18f,
                 Md3Colors.alpha(t.colors.primary, ClientTheme.dark() ? 0.22f : 0.14f));
      }
   }

   private void renderPanel(Md3Theme t) {
      // 1.0.2_2：按用户要求外观回退到 1.0.1 —— 面板是偏暗的 Low + 24 圆角 + 细描边 + 浅投影，
      // 瓦片用 High 浮在上面。只保留 1.0.2 的「背景不透明度」联动，所以底色都过一遍 panelSurface。
      this.canvas.shadow(this.panelX, this.panelY, this.panelW, this.panelH, 28f, 2);
      this.panelGlass(t);
      // 1.0.2_12：面板压暗一档 —— 透明时面板被背景顶得太浅，卡片压在上面就显凹。
      float clear = 1f - this.panelOpacity();
      int panelBase = Md3Colors.composite(Md3Colors.alpha(0x000000, 0.12f + 0.10f * clear),
              t.colors.surfaceContainer);
      this.canvas.roundRect(this.panelX, this.panelY, this.panelW, this.panelH, 24f,
              this.panelSurface(panelBase));
      // 1.0.2_4：按用户要求去掉面板那圈 1px 淡描边（不透明时它像一道多余的线）。
   }

   private void renderAppBar(Md3Theme t, int mouseX, int mouseY) {
      float logoX = this.appBarX + 2f;
      float logoY = this.appBarY + (this.appBarH - 30f) / 2f;
      if (this.showLogo()) {
         this.canvas.roundRect(logoX, logoY, 30f, 30f, 10f, t.colors.primaryContainer);
         this.canvas.icon("auto_awesome", logoX + 5f, logoY + 5f, 20f, t.colors.onPrimaryContainer);
      }

      this.search.trailingIcon = this.search.text.isEmpty() ? null : "close";
      this.search.surface = this.clearRaised(t, 0.20f);
      this.glass(t, this.search.x, this.search.y, this.search.w, this.search.h, 4f);
      this.search.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.searchFocus) {
         ImeBridge.onFieldRender((int) this.search.x + 16, (int) this.search.y + 22,
                 (int) this.search.w - 32, this.search.text, this.search.text.length(), true);
      }
      if (this.search.text.isEmpty()) {
         String hint = this.searchW < 150f ? tr("搜索", "Search") : tr("搜索设置", "Search settings");
         this.canvas.text(ClientUi.fit(this.canvas, hint, Md3Type.BODY_MEDIUM,
                 this.searchW - 52f), this.searchX + 44f,
                 Md3Text.topForLineCenter(this.searchY + this.searchH / 2f, Md3Type.BODY_MEDIUM),
                 Md3Type.BODY_MEDIUM, Md3Colors.alpha(t.colors.onSurfaceVariant, 0.72f));
      }

      this.themeButton.icon = ClientTheme.dark() ? "light_mode" : "dark_mode";
      this.themeButton.surface = this.clearRaised(t, 0.20f);
      this.glass(t, this.themeButton.x, this.themeButton.y, this.themeButton.w, this.themeButton.h, this.themeButton.w / 2f);
      this.themeButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.themeButton.consumeClick()) {
         ClientConfig.lightTheme = !ClientConfig.lightTheme;
         ClientConfig.saveSoon();
      }

      this.menuButton.surface = this.clearRaised(t, 0.20f);
      this.glass(t, this.menuButton.x, this.menuButton.y, this.menuButton.w, this.menuButton.h, this.menuButton.w / 2f);
      this.menuButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      // 开关只在 mouseClicked 里做一次：这里再 consumeClick 会和它互相抵消，表现就是"点一下没反应"。

      this.closeButton.surface = this.clearRaised(t, 0.20f);
      this.glass(t, this.closeButton.x, this.closeButton.y, this.closeButton.w, this.closeButton.h, this.closeButton.w / 2f);
      this.closeButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.closeButton.consumeClick()) {
         this.close();
      }
   }

   /** 悬停判定；下拉菜单展开时一律不命中，避免菜单底下的卡片跟着亮。 */
   private boolean hover(int mouseX, int mouseY, float x, float y, float w, float h) {
      return !this.hoverBlocked && ClientUi.hit(mouseX, mouseY, x, y, w, h);
   }

   private boolean showLogo() {
      return this.appBarW >= 360f;
   }

   private void openAppMenu() {
      this.appMenu.items = new String[]{
              tr("已安装模组", "Installed mods"),
              tr("重置所有设置", "Reset all settings")};
      // 这两项是一次性动作，不是二选一，菜单不显示选中底色和勾选。
      this.appMenu.selected = -1;
      this.appMenu.x = Math.max(this.appBarX + 8f, this.menuBtnX - 150f);
      this.appMenu.y = this.appBarY + this.appBarH + 6f;
      // 小窗口下菜单会顶到屏幕底，第三条项被裁掉；放不下就整体上移。
      float menuBottomLimit = this.height - 6f;
      float menuH = 8f + this.appMenu.items.length * 42f;
      if (this.appMenu.y + menuH > menuBottomLimit) {
         this.appMenu.y = Math.max(4f, menuBottomLimit - menuH);
      }
      this.appMenu.w = 190f;
      this.appMenu.itemHeight = 42f;
      this.appMenu.open();
      this.appMenuOpen = true;
      ClientUi.click();
   }

   private void renderAppMenu(Md3Theme t, int mouseX, int mouseY) {
      if (!this.appMenuOpen) {
         return;
      }
      this.appMenu.update(this.dt);
      // 1.0.2_1：下拉菜单属于设置面板的一部分，跟着「背景不透明度」一起变透明。
      // 1.0.2_2：透明模式下菜单会和卡片文字糊在一起，叠一层黑色滤镜把文字衬出来；
      // 面板不透明时这层黑也只会让菜单更深一点，对比度反而更好。
      // 1.0.2_2：菜单的黑色滤镜和「背景不透明度」反着走——
      // 面板越不透明（自己能衬住文字）就越不需要黑底；面板越透，黑底压得越重。
      // opacity=100 → 不叠黑（菜单就是原来的 surfaceContainer），opacity=0 → 叠满上限。
      this.appMenu.surfaceColor = Md3Colors.composite(
              Md3Colors.alpha(0x000000, this.menuVeil()),
              this.panelSurface(t.colors.surfaceContainer));
      this.appMenu.hasSurface = true;
      this.appMenu.render(this.canvas, t, mouseX, mouseY);
   }

   private void renderTabs(Md3Theme t, int mouseX, int mouseY) {
      this.canvas.pushClip(this.tabsX + 6f, this.tabsY + 2f, this.tabsW - 12f, this.tabsH - 4f);
      this.tabBoxes.clear();

      // 1.0.2_1：面板变窄后（宽高比被压回 4:3 时）六个分类标签放不下，
      // MISC 会被右边缘裁成「MIS」。窄面板改用小一号字和更紧的左右留白。
      boolean narrow = this.appBarW < 380f;
      Md3Type tabType = narrow ? Md3Type.LABEL_SMALL : Md3Type.LABEL_MEDIUM;
      float tabPad = narrow ? 9f : 14f;
      float tx = this.tabsX + 8f - this.tabScrollX;
      float total = 0f;
      for (int i = 0; i <= 5; i++) {
         String label = this.categoryLabel(i);
         float tw = this.canvas.textWidth(label, tabType) + tabPad;
         float th = 24f;
         float ty = this.tabsY + (this.tabsH - th) / 2f;
         boolean active = this.selectedCat == i;
         // 1.0.2_21：选中的分类不再跟面板一起变透明。透明度调到 0 时整排胶囊都消失，
         // 只剩一排一样颜色的字，根本认不出当前在哪个分类。高亮属于状态反馈，留 90% 实。
         int base = active ? Md3Colors.alpha(t.colors.primaryContainer, 230)
                 : this.clearRaised(t, 0.20f);
         int content = active ? t.colors.onPrimaryContainer : t.colors.onSurfaceVariant;
         this.glass(t, tx, ty, tw, th, 12f);
         this.canvas.roundRect(tx, ty, tw, th, 12f, base);
         Md3Text.centered(this.canvas, label, tx + tw / 2f, ty + th / 2f,
                 tabType, content);

         TabBox box = new TabBox();
         box.index = i;
         box.x = tx;
         box.y = ty;
         box.w = tw;
         box.h = th;
         this.tabBoxes.add(box);

         tx += tw + (narrow ? 3f : 4f);
         total = tx - (this.tabsX + 8f - this.tabScrollX);
      }
      this.tabsContentW = total;
      this.tabScrollX = Math.max(0f, Math.min(this.tabScrollX,
              Math.max(0f, total - (this.tabsW - 16f))));
      this.canvas.popClip();

      if (this.tabsContentW > this.tabsW - 16f) {
         float frac = (this.tabsW - 16f) / this.tabsContentW;
         float bw = Math.max(36f, (this.tabsW - 16f) * frac);
         float bx = this.tabsX + 8f + (this.tabsW - 16f - bw)
                 * (this.tabScrollX / Math.max(1f, this.tabsContentW - (this.tabsW - 16f)));
         this.canvas.roundRect(bx, this.tabsY + this.tabsH - 4f, bw, 2f, 1f,
                 Md3Colors.alpha(t.colors.primary, 0.65f));
      }
   }

   private void renderContent(Md3Theme t, int mouseX, int mouseY) {
      this.boxes.clear();
      this.tileBoxes.clear();
      this.layoutTiles();
      this.scroll.measure(this.cardsTotalHeight, this.contentH);
      this.scroll.update(this.dt);

      this.canvas.pushClip(this.contentX, this.contentY, this.contentW, this.contentH);
      if (this.tiles.isEmpty()) {
         this.canvas.icon("search", this.contentX + this.contentW / 2f - 13f,
                 this.contentY + 44f, 26f, t.colors.onSurfaceVariant);
         Md3Text.centered(this.canvas, tr("没有匹配的设置", "No matching settings"),
                 this.contentX + this.contentW / 2f, this.contentY + 92f,
                 Md3Type.TITLE_SMALL, t.colors.onSurfaceVariant);
         this.canvas.popClip();
         return;
      }

      for (Tile tile : this.tiles) {
         float y = this.contentY + tile.y - this.scroll.value;
         if (y + this.tileH < this.contentY - 8f || y > this.contentY + this.contentH + 8f) {
            continue;
         }
         this.renderTile(t, tile, y, mouseX, mouseY);
      }
      this.canvas.popClip();

      this.scroll.renderBar(this.canvas, t, this.contentX + this.contentW + 9f,
              this.contentY + 2f, 3f);
   }

   private void renderDetailAppBar(Md3Theme t, int mouseX, int mouseY) {
      this.backButton.setBounds(this.appBarX + 8f,
              this.appBarY + (this.appBarH - this.menuBtnS) / 2f, this.menuBtnS, this.menuBtnS);
      // 详情页这两个圆按钮之前没设 surface，Md3IconButton 会回落到不透明的 colors.surface，
      // 于是面板已经全透了、返回和关闭还是两个实心黑圆。
      this.backButton.surface = this.clearRaised(t, 0.20f);
      this.glass(t, this.backButton.x, this.backButton.y, this.backButton.w, this.backButton.h, this.backButton.w / 2f);
      this.backButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.backButton.consumeClick()) {
         this.closeDetail();
         ClientUi.click();
         return;
      }

      Group group = this.detailGroup;
      if (group != null) {
         this.canvas.text(group.name(), this.appBarX + 54f, this.appBarY + 11f,
                 Md3Type.TITLE_MEDIUM, t.colors.onSurface);
         this.canvas.text(this.trCount(group.rowCount()),
                 this.appBarX + 54f, this.appBarY + 31f,
                 Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);
      }

      this.closeButton.setBounds(this.closeBtnX, this.closeBtnY, this.closeBtnS, this.closeBtnS);
      this.closeButton.surface = this.panelSurface(t.colors.surfaceContainer);
      this.glass(t, this.closeButton.x, this.closeButton.y, this.closeButton.w, this.closeButton.h, this.closeButton.w / 2f);
      this.closeButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.closeButton.consumeClick()) {
         this.close();
      }
   }

   private void renderDetailContent(Md3Theme t, int mouseX, int mouseY) {
      this.boxes.clear();
      this.tileBoxes.clear();
      Group group = this.detailGroup;
      if (group == null) {
         return;
      }
      this.contentX = this.panelX + 12f;
      // 顶栏是两行文字（标题 + 条数），appBarH 只包住按钮高度；再 +10 会让
      // 内容区的第一行压到副标题上，这里留出两行文字的高度。
      this.contentY = this.panelY + this.appBarH + 24f;
      this.contentW = this.panelW - 24f - 12f;
      this.contentH = Math.max(80f, this.panelY + this.panelH - 12f - this.contentY);

      float listW = Math.min(780f, this.contentW);
      float lx = this.contentX + (this.contentW - listW) / 2f;
      // FOV 详情页固定单列，客户端 FOV 开关在 SettingsSpec 排序时置顶。
      int cols = group.id == SettingsSpec.G_FOV ? 1 : (listW >= 500f ? 2 : 1);
      float gap = 10f;
      float cardW = (listW - gap * (cols - 1)) / cols;
      int unitCount = group.units.size();
      int rows = (unitCount + cols - 1) / cols;

      float total = 0f;
      float[] rowH = new float[rows];
      for (int i = 0; i < unitCount; i++) {
         int row = i / cols;
         rowH[row] = Math.max(rowH[row], this.unitHeight(group.units.get(i)) + 20f);
      }
      for (int i = 0; i < rows; i++) {
         total += rowH[i] + gap;
      }
      total = Math.max(0f, total - gap);
      this.scroll.measure(total, this.contentH);
      this.scroll.update(this.dt);

      this.canvas.pushClip(this.contentX, this.contentY, this.contentW, this.contentH);
      float ry = this.contentY - this.scroll.value;
      for (int row = 0; row < rows; row++) {
         for (int col = 0; col < cols; col++) {
            int i = row * cols + col;
            if (i >= unitCount) {
               continue;
            }
            Unit unit = group.units.get(i);
            float cx = lx + col * (cardW + gap);
            if (ry + rowH[row] >= this.contentY - 8f && ry <= this.contentY + this.contentH + 8f) {
               this.glass(t, cx, ry, cardW, rowH[row], 16f);
               this.canvas.roundRect(cx, ry, cardW, rowH[row], 16f,
                       this.raisedSurface(t, 0.10f));
               this.renderUnit(t, unit, cx + 16f, ry + 10f, cardW - 32f, mouseX, mouseY);
            }
         }
         ry += rowH[row] + gap;
      }
      this.canvas.popClip();

      this.scroll.renderBar(this.canvas, t, this.contentX + this.contentW + 9f,
              this.contentY + 2f, 3f);
   }

   private void openDetail(Group group) {
      this.detailGid = group.id;
      this.detailGroup = group;
      this.scroll.jump(0f);
      this.searchFocus = false;
      this.search.focused = false;
      this.appMenuOpen = false;
   }

   private void closeDetail() {
      this.detailGid = -1;
      this.detailGroup = null;
      this.scroll.jump(0f);
      this.applyGridBounds();
   }

   private void applyGridBounds() {
      this.contentX = this.panelX + 12f;
      this.contentY = this.tabsY + this.tabsH + 7f;
      this.contentW = this.panelW - 24f - 12f;
      this.contentH = Math.max(90f, this.panelY + this.panelH - 12f - this.contentY);
   }

   private void detailClick(int mouseX, int mouseY, int button) {
      if (button == 1) {
         for (RowBox box : this.boxes) {
            if (box.kind == SettingsSpec.K_VALUE
                    && this.hover(mouseX, mouseY, box.ctrlX, box.ctrlY - 6f,
                            box.ctrlW, box.ctrlH + 12f)) {
               this.stepSlider(box.key, -1);
               return;
            }
         }
         return;
      }
      if (button != 0) {
         return;
      }
      // 1.0.2_2：命中区必须和画出来的条对齐（renderBar 的 x 是 contentW + 9），
      // 之前写在 +3，条右边那一段点不动，手感就是「条偏右、点不中」。
      if (this.scroll.press(mouseX, mouseY, this.contentX + this.contentW + 8f,
              this.contentY + 2f, 5f, 8f)) {
         return;
      }
      // 返回键 / 关闭键按下即生效：不再依赖 Md3Widget 的"按下→抬起"边沿，
      // 免得哪次改命中逻辑把 mouseDown 一清，整片按钮就静默变死。
      float backX = this.appBarX + 8f;
      float backY = this.appBarY + (this.appBarH - this.menuBtnS) / 2f;
      if (this.hover(mouseX, mouseY, backX, backY, this.menuBtnS, this.menuBtnS)) {
         this.closeDetail();
         ClientUi.click();
         return;
      }
      if (this.hover(mouseX, mouseY, this.closeBtnX, this.closeBtnY,
              this.closeBtnS, this.closeBtnS)) {
         this.close();
         return;
      }
      // 顶栏（返回键 / 标题 / 关闭键）图层更高：落在顶栏上的点击在渲染里由
      // Md3Widget 的"按下→抬起"边沿消费，这里只要不继续往下命中行就够了。
      // 注意不能把 mouseDown 清掉，否则按钮收不到按下事件，返回键会彻底点不动。
      if (this.hover(mouseX, mouseY, this.appBarX,
              this.appBarY - 4f, this.appBarW, this.appBarH + 8f)) {
         return;
      }
      // 行只在内容可视区内可点：render() 用 pushClip 把行裁在 contentY..+contentH，
      // 命中判定也要用同一块矩形，否则被裁掉的行仍然能被点中。
      if (!this.hover(mouseX, mouseY, this.contentX, this.contentY,
              this.contentW, this.contentH)) {
         return;
      }
      for (RowBox box : this.boxes) {
         boolean inside = this.hover(mouseX, mouseY, box.x, box.y, box.w, box.h);
         if (box.kind == SettingsSpec.K_VALUE) {
            if (this.hover(mouseX, mouseY, box.ctrlX, box.ctrlY - 6f,
                    box.ctrlW, box.ctrlH + 12f)) {
               this.sliderKey = box.key;
               float track = Math.max(1f, box.trackW - 14f);
               float firstNorm = SettingsValues.clamp01((mouseX - box.trackX - 7f) / track);
               this.sliderNorm = firstNorm;
               this.sliderStartX = mouseX;
               this.sliderStartNorm = firstNorm;
               this.applyNormSmooth(box.key, firstNorm);
               return;
            }
         } else if (box.kind == SettingsSpec.K_TOGGLE) {
            if (inside) {
               SettingsValues.toggle(box.key);
               this.syncSwitch(box.key);
               ClientConfig.save();
               ClientUi.click();
               if (box.key == SettingsSpec.UI_OVERRIDE) {
                  this.close();
                  return;
               }
            }
         } else {
            if (inside || this.hover(mouseX, mouseY, box.ctrlX, box.ctrlY,
                    box.ctrlW, box.ctrlH)) {
               this.rowAction(box);
               return;
            }
         }
      }
   }


   private void layoutTiles() {
      if (this.tiles.isEmpty()) {
         this.cardsTotalHeight = 0f;
         return;
      }
      // 列数按「塞得下几张卡」算，不再用 900/300 这种粗档位：
      // 高 guiScale（1080p 默认 4）下内容区只有 300 出头，也照样能摆三列。
      int cols = this.contentW >= 900f ? 4 : (this.contentW >= 250f ? 3 : (this.contentW >= 190f ? 2 : 1));
      float gap = 8f;
      float tileW = (this.contentW - gap * (cols - 1)) / cols;

      // 不再固定 60/76：按实际字体行高、图标和左右留白推导卡片高度。
      // 1.0.2_1：卡宽不足 230 时一律用「图标在上、文字在下」的纵排卡片——
      // 之前 132~230 走的是「图标在左、文字在右」的横排，卡片高度只有 52，
      // 720p 三列（卡宽约 140）下看起来就是一条扁条，和模组菜单那种方卡完全不是一回事。
      boolean wide = tileW >= 230f;
      float pad = wide ? 14f : (tileW >= 132f ? 11f : 9f);
      float icon = wide ? 26f : (tileW >= 132f ? 24f : 22f);
      float titleH = (wide ? Md3Type.TITLE_MEDIUM : Md3Type.TITLE_SMALL).lineHeight;
      float subH = (wide ? Md3Type.BODY_SMALL : Md3Type.LABEL_SMALL).lineHeight;
      this.stackPad = pad;
      this.stackIcon = icon;
      if (wide) {
         this.tileH = pad * 2f + Math.max(icon, titleH + 2f + subH);
      } else {
         // 纵向堆叠时，标题的垂直中心必须让开图标底部，不能只加 6px。
         this.stackTitleCy = pad + icon + 2f + titleH / 2f;
         this.stackSubCy = this.stackTitleCy + titleH / 2f + 1f + subH / 2f;
         this.tileH = this.stackSubCy + subH / 2f + pad;
      }
      this.tileH = Math.max(this.tileH, 52f);
      for (int i = 0; i < this.tiles.size(); i++) {
         Tile tile = this.tiles.get(i);
         int col = i % cols;
         int row = i / cols;
         tile.x = this.contentX + col * (tileW + gap);
         tile.y = row * (this.tileH + gap);
         tile.w = tileW;
      }
      int rows = (this.tiles.size() + cols - 1) / cols;
      this.cardsTotalHeight = Math.max(0f, rows * (this.tileH + gap) - gap);
   }

   private void renderTile(Md3Theme t, Tile tile, float y, int mouseX, int mouseY) {
      Group group = tile.group;
      float x = tile.x;
      if (group == null) {
         renderReplayTile(t, tile, y, mouseX, mouseY);
         return;
      }
      float w = tile.w;
      boolean hot = this.hover(mouseX, mouseY, x, y, w, this.tileH);
      this.drawTileCard(t, x, y, w, this.tileH, hot);

      String sub = this.trCount(group.rowCount());
      String en = group.nameEn();
      if (w >= 230f && en != null && !en.isEmpty()) {
         sub += " · " + en.toUpperCase();
      }
      if (w >= 230f) {
         // 宽卡片：图标在左，标题和说明竖排在右。
         float ix = x + 14f;
         float iy = y + 14f;
         this.canvas.roundRect(ix, iy, 26f, 26f, 8f, t.colors.primaryContainer);
         this.canvas.icon(ClientIcons.group(group.id), ix + 4f, iy + 4f, 18f,
                 t.colors.onPrimaryContainer);
         float nameX = ix + 36f;
         float nameW = Math.max(40f, w - 74f);
         ClientUi.textFit(this.canvas, group.name(), nameX, y + 11f,
                 Md3Type.TITLE_MEDIUM, nameW, t.colors.onSurface);
         ClientUi.textFit(this.canvas, sub, nameX, y + 32f, Md3Type.BODY_SMALL,
                 nameW, t.colors.onSurfaceVariant);
         this.canvas.icon("chevron_right", x + w - 30f, y + (this.tileH - 18f) / 2f, 16f,
                 hot ? t.colors.primary : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.65f));
      } else {
         // 纵排卡片：图标在上，标题、说明依次在下（方卡比例，和模组菜单一致）。
         float pad = this.stackPad;
         float box = this.stackIcon;
         float ix = x + pad;
         float iy = y + pad;
         this.canvas.roundRect(ix, iy, box, box, 8f, t.colors.primaryContainer);
         this.canvas.icon(ClientIcons.group(group.id), ix + 3f, iy + 3f, box - 6f,
                 t.colors.onPrimaryContainer);
         ClientUi.textFit(this.canvas, group.name(), x + pad, y + this.stackTitleCy,
                 Md3Type.TITLE_SMALL, w - pad * 2f, t.colors.onSurface);
         ClientUi.textFit(this.canvas, sub, x + pad, y + this.stackSubCy, Md3Type.LABEL_SMALL,
                 w - pad * 2f, t.colors.onSurfaceVariant);
         this.canvas.icon("chevron_right", x + w - 22f, y + pad - 1f, 14f,
                 hot ? t.colors.primary : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.65f));
      }

      TileBox box = new TileBox();
      box.gid = group.id;
      box.x = x;
      box.y = y;
      box.w = w;
      box.h = this.tileH;
      this.tileBoxes.add(box);
   }

   /** 回放不是设置分组，而是动作卡片：游戏内进录制页，主菜单进控制台。 */
   private void renderReplayTile(Md3Theme t, Tile tile, float y, int mouseX, int mouseY) {
      float x = tile.x;
      float w = tile.w;
      boolean hot = this.hover(mouseX, mouseY, x, y, w, this.tileH);
      this.drawTileCard(t, x, y, w, this.tileH, hot);

      String name = tr("回放", "Replay");
      String sub = "";

      if (w >= 230f) {
         float ix = x + 14f;
         float iy = y + 14f;
         this.canvas.roundRect(ix, iy, 26f, 26f, 8f, t.colors.primaryContainer);
         this.canvas.icon("animation", ix + 4f, iy + 4f, 18f, t.colors.onPrimaryContainer);
         ClientUi.textFit(this.canvas, name, ix + 36f, y + 11f,
                 Md3Type.TITLE_MEDIUM, Math.max(40f, w - 74f), t.colors.onSurface);
         ClientUi.textFit(this.canvas, sub, ix + 36f, y + 32f,
                 Md3Type.BODY_SMALL, Math.max(40f, w - 74f), t.colors.onSurfaceVariant);
         this.canvas.icon("chevron_right", x + w - 30f, y + (this.tileH - 18f) / 2f, 16f,
                 hot ? t.colors.primary : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.65f));
      } else {
         float pad = this.stackPad;
         float box = this.stackIcon;
         float ix = x + pad;
         float iy = y + pad;
         this.canvas.roundRect(ix, iy, box, box, 8f, t.colors.primaryContainer);
         this.canvas.icon("animation", ix + 3f, iy + 3f, box - 6f, t.colors.onPrimaryContainer);
         ClientUi.textFit(this.canvas, name, x + pad, y + this.stackTitleCy,
                 Md3Type.TITLE_SMALL, w - pad * 2f, t.colors.onSurface);
         ClientUi.textFit(this.canvas, sub, x + pad, y + this.stackSubCy, Md3Type.LABEL_SMALL,
                 w - pad * 2f, t.colors.onSurfaceVariant);
         this.canvas.icon("chevron_right", x + w - 22f, y + pad - 1f, 14f,
                 hot ? t.colors.primary : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.65f));
      }

      TileBox box = new TileBox();
      box.gid = -1;
      box.x = x;
      box.y = y;
      box.w = w;
      box.h = this.tileH;
      this.tileBoxes.add(box);
   }

   private int indexOf(Group group) {
      for (int i = 0; i < this.groups.size(); i++) {
         if (this.groups.get(i).id == group.id) {
            return i;
         }
      }
      return 0;
   }

   private String trCount(int count) {
      if (ClientConfig.langIndex() == 0) {
         return count + " 项设置";
      }
      return count == 1 ? "1 setting" : count + " settings";
   }

   private float unitHeight(Unit unit) {
      int headH = this.headHeight(unit.head.kind);
      if (unit.head.kind == SettingsSpec.K_TOGGLE && !unit.rows.isEmpty()) {
         float childH = 0f;
         for (Row row : unit.rows) {
            childH += this.childHeight(row.kind);
         }
         return headH + 4f + childH + 16f;
      }
      return headH;
   }

   private int headHeight(int kind) {
      switch (kind) {
         case SettingsSpec.K_VALUE:
            return 66;
         case SettingsSpec.K_COLOR:
         case SettingsSpec.K_CYCLE:
         case SettingsSpec.K_ACTION:
            return 52;
         case SettingsSpec.K_RESET:
            return 58;
         default:
            return 48;
      }
   }

   private int childHeight(int kind) {
      switch (kind) {
         case SettingsSpec.K_VALUE:
            return 58;
         case SettingsSpec.K_COLOR:
         case SettingsSpec.K_CYCLE:
         case SettingsSpec.K_ACTION:
            return 44;
         default:
            return 48;
      }
   }

   private void renderUnit(Md3Theme t, Unit unit, float x, float y, float w,
                           int mouseX, int mouseY) {
      Row head = unit.head;

      if (head.kind == SettingsSpec.K_TOGGLE) {
         this.renderToggle(t, head, x, y, w, mouseX, mouseY);
         if (!unit.rows.isEmpty()) {
            float panelY = y + 50f;
            float panelH = 8f;
            for (Row row : unit.rows) {
               panelH += this.childHeight(row.kind);
            }
            this.glass(t, x + 10f, panelY, w - 20f, panelH, 14f);
            this.canvas.roundRect(x + 10f, panelY, w - 20f, panelH, 14f,
                    this.controlSurface(t.colors.surfaceContainer));
            float cy = panelY + 4f;
            for (int i = 0; i < unit.rows.size(); i++) {
               Row row = unit.rows.get(i);
               float rh = this.childHeight(row.kind);
               this.renderChild(t, row, x + 22f, cy, w - 44f, rh, mouseX, mouseY);
               if (i < unit.rows.size() - 1) {
                  this.canvas.fill(x + 34f, cy + rh - 1f, w - 68f, 1f,
                          Md3Colors.alpha(t.colors.outlineVariant, 0.7f));
               }
               cy += rh;
            }
         }
         return;
      }

      this.renderChild(t, head, x, y, w, this.headHeight(head.kind), mouseX, mouseY);
   }

   private void renderToggle(Md3Theme t, Row row, float x, float y, float w,
                             int mouseX, int mouseY) {
      float cy = y + 24f;
      ClientUi.textFit(this.canvas, this.label(row.key), x + 4f, cy, Md3Type.TITLE_SMALL,
              w - 76f, t.colors.onSurface);

      boolean on = SettingsValues.isOn(row.key);
      Md3Switch sw = this.switches.get(row.key);
      if (sw == null) {
         sw = new Md3Switch();
         sw.setChecked(on);
         this.switches.put(row.key, sw);
      }
      Boolean last = this.lastSwitchOn.get(row.key);
      if (last == null) {
         sw.setChecked(on);
         this.lastSwitchOn.put(row.key, Boolean.valueOf(on));
      } else if (last.booleanValue() != on) {
         sw.setCheckedAnimated(on);
         this.lastSwitchOn.put(row.key, Boolean.valueOf(on));
      }
      sw.surface = this.controlSurface(t.colors.surfaceContainerHighest);
      sw.setBounds(x + w - 54f, y + 8f, 54f, 32f);
      sw.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      sw.consumeClick();

      RowBox box = new RowBox();
      box.kind = row.kind;
      box.key = row.key;
      box.x = x;
      box.y = y;
      box.w = w;
      box.h = 48f;
      box.ctrlX = x + w - 54f;
      box.ctrlY = y + 8f;
      box.ctrlW = 54f;
      box.ctrlH = 32f;
      this.boxes.add(box);
   }

   private void renderChild(Md3Theme t, Row row, float x, float y, float w, float h,
                            int mouseX, int mouseY) {
      switch (row.kind) {
         case SettingsSpec.K_VALUE:
            this.renderValueRow(t, row, x, y, w, h, mouseX, mouseY);
            return;
         case SettingsSpec.K_COLOR:
            this.renderColorRow(t, row, x, y, w, h, mouseX, mouseY);
            return;
         case SettingsSpec.K_CYCLE:
            this.renderCycleRow(t, row, x, y, w, h, mouseX, mouseY);
            return;
         case SettingsSpec.K_ACTION:
            this.renderActionRow(t, row, x, y, w, h, mouseX, mouseY);
            return;
         default:
            ClientUi.textFit(this.canvas, this.label(row.key), x + 4f, y + h / 2f,
                    Md3Type.TITLE_SMALL, w - 8f, t.colors.onSurface);
      }
   }

   private void renderValueRow(Md3Theme t, Row row, float x, float y, float w, float h,
                               int mouseX, int mouseY) {
      float cy = y + 14f;
      ClientUi.textFit(this.canvas, this.label(row.key), x + 4f, cy, Md3Type.TITLE_SMALL,
              w - 76f, t.colors.onSurface);

      String value = SettingsValues.valueText(row.key);
      float vw = this.canvas.textWidth(value, Md3Type.LABEL_MEDIUM);
      this.canvas.roundRect(x + w - vw - 18f, cy - 10f, vw + 14f, 20f, 10f,
              t.colors.secondaryContainer);
      Md3Text.centered(this.canvas, value, x + w - vw / 2f - 11f, cy,
              Md3Type.LABEL_MEDIUM, t.colors.onSecondaryContainer);

      float sx = x + 6f;
      float sy = y + 36f;
      float sw = w - 12f;
      float norm;
      if (this.sliderKey == row.key) {
         norm = this.sliderNorm;
      } else {
         norm = SettingsValues.clamp01(SettingsValues.normOf(row.key));
      }
      float trackY = sy + 10f;
      boolean hot = this.hover(mouseX, mouseY, sx, sy, sw, 32f);
      this.canvas.roundRect(sx, trackY, sw, 4f, 2f, t.colors.surfaceContainerHighest);
      if (norm > 0f) {
         this.canvas.roundRect(sx, trackY, Math.max(4f, norm * sw), 4f, 2f, t.colors.primary);
      }
      float thumbX = sx + 7f + norm * Math.max(0f, sw - 14f);
      float thumbCenterY = trackY + 2f;
      if (hot || this.sliderKey == row.key) {
         this.canvas.roundRect(thumbX - 14f, thumbCenterY - 14f, 28f, 28f, 14f,
                 Md3Colors.alpha(t.colors.primary, 0.10f));
      }
      this.canvas.roundRect(thumbX - 6f, thumbCenterY - 6f, 12f, 12f, 6f, t.colors.primary);

      RowBox box = new RowBox();
      box.kind = row.kind;
      box.key = row.key;
      box.x = x;
      box.y = y;
      box.w = w;
      box.h = h;
      // 1.0.2_1：整行（标签 + 轨道）都能按下去，不再只有轨道附近 32px 的一条窄缝能拖。
      box.ctrlX = x;
      box.ctrlY = y;
      box.ctrlW = w;
      box.ctrlH = h;
      box.trackX = sx;
      box.trackW = sw;
      this.boxes.add(box);
   }

   private void renderColorRow(Md3Theme t, Row row, float x, float y, float w, float h,
                               int mouseX, int mouseY) {
      float cy = y + h / 2f;
      ClientUi.textFit(this.canvas, this.label(row.key), x + 4f, cy, Md3Type.TITLE_SMALL,
              w - 56f, t.colors.onSurface);
      float d = 24f;
      float bx = x + w - d - 6f;
      float by = cy - d / 2f;
      SettingsValues.drawChecker(this.canvas, bx, by, d, d, 4f);
      this.canvas.roundRect(bx, by, d, d, d / 2f, SettingsValues.colorOf(row.key));
      this.canvas.strokeRoundRect(bx, by, d, d, d / 2f, 1f, t.colors.outline);
      if (this.hover(mouseX, mouseY, bx - 6f, by - 6f, d + 12f, d + 12f)) {
         this.canvas.strokeRoundRect(bx - 5f, by - 5f, d + 10f, d + 10f, (d + 10f) / 2f,
                 1f, Md3Colors.alpha(t.colors.primary, 0.5f));
      }

      RowBox box = new RowBox();
      box.kind = row.kind;
      box.key = row.key;
      box.x = x;
      box.y = y;
      box.w = w;
      box.h = h;
      box.ctrlX = x;
      box.ctrlY = y;
      box.ctrlW = w;
      box.ctrlH = h;
      this.boxes.add(box);
   }

   private void renderCycleRow(Md3Theme t, Row row, float x, float y, float w, float h,
                               int mouseX, int mouseY) {
      float cy = y + h / 2f;
      String value = SettingsValues.cycleText(row.key);
      float cw = Math.max(92f, this.canvas.textWidth(value, Md3Type.LABEL_LARGE) + 42f);
      ClientUi.textFit(this.canvas, this.label(row.key), x + 4f, cy, Md3Type.TITLE_SMALL,
              w - cw - 12f, t.colors.onSurface);
      float cx = x + w - cw - 2f;
      float ch = 30f;
      boolean hot = this.hover(mouseX, mouseY, cx, cy - ch / 2f, cw, ch);
      this.canvas.roundRect(cx, cy - ch / 2f, cw, ch, ch / 2f,
              hot ? Md3Colors.composite(Md3Colors.alpha(t.colors.primary, 0.08f),
                      t.colors.surfaceContainerHighest) : t.colors.surfaceContainerHighest);
      Md3Text.leftMiddle(this.canvas, ClientUi.fit(this.canvas, value, Md3Type.LABEL_MEDIUM,
              cw - 34f), cx + 12f, cy, Md3Type.LABEL_MEDIUM, t.colors.onSurface);
      this.canvas.icon("expand_more", cx + cw - 24f, cy - 9f, 18f,
              t.colors.onSurfaceVariant);

      RowBox box = new RowBox();
      box.kind = row.kind;
      box.key = row.key;
      box.x = x;
      box.y = y;
      box.w = w;
      box.h = h;
      box.ctrlX = cx;
      box.ctrlY = cy - ch / 2f;
      box.ctrlW = cw;
      box.ctrlH = ch;
      this.boxes.add(box);
   }

   private void renderActionRow(Md3Theme t, Row row, float x, float y, float w, float h,
                                int mouseX, int mouseY) {
      float cy = y + h / 2f;
      String label = row.key == 64 && this.bindingKey
              ? tr("按下按键…", "press a key...") : this.actionText(row.key);
      float aw = Math.max(92f, this.canvas.textWidth(label, Md3Type.LABEL_LARGE) + 40f);
      ClientUi.textFit(this.canvas, this.label(row.key), x + 4f, cy, Md3Type.TITLE_SMALL,
              w - aw - 12f, t.colors.onSurface);
      float ax = x + w - aw - 2f;
      float ah = 30f;
      boolean hot = this.hover(mouseX, mouseY, ax, cy - ah / 2f, aw, ah);
      this.canvas.roundRect(ax, cy - ah / 2f, aw, ah, ah / 2f,
              hot ? t.colors.primary : t.colors.primaryContainer);
      this.canvas.icon(row.key == 45 ? "my_location" : "keyboard",
              ax + 10f, cy - 9f, 18f, hot ? t.colors.onPrimary : t.colors.onPrimaryContainer);
      Md3Text.leftMiddle(this.canvas, ClientUi.fit(this.canvas, label, Md3Type.LABEL_MEDIUM,
              aw - 34f), ax + 32f, cy, Md3Type.LABEL_MEDIUM,
              hot ? t.colors.onPrimary : t.colors.onPrimaryContainer);

      RowBox box = new RowBox();
      box.kind = row.kind;
      box.key = row.key;
      box.x = x;
      box.y = y;
      box.w = w;
      box.h = h;
      box.ctrlX = ax;
      box.ctrlY = cy - ah / 2f;
      box.ctrlW = aw;
      box.ctrlH = ah;
      this.boxes.add(box);
   }

   /* ------------------------------------------------------------------ */
   /* dialogs                                                            */
   /* ------------------------------------------------------------------ */

   private void renderMenu(Md3Theme t, int mouseX, int mouseY) {
      if (this.menuKey < 0) {
         return;
      }
      this.menu.update(this.dt);
      this.menu.render(this.canvas, t, mouseX, mouseY);
   }

   private void renderSnack(Md3Theme t) {
      String text = this.currentSnack();
      if (text == null) {
         return;
      }
      float w = Math.min(this.contentW - 24f,
              this.canvas.textWidth(text, Md3Type.BODY_MEDIUM) + 44f);
      float h = 40f;
      float x = this.contentX + (this.contentW - w) / 2f;
      float y = this.contentY + this.contentH - h - 12f;
      Md3Surfaces.snackbar(this.canvas, t, x, y, w, h);
      Md3Text.centered(this.canvas, ClientUi.fit(this.canvas, text, Md3Type.BODY_MEDIUM, w - 28f),
              x + w / 2f, y + h / 2f, Md3Type.BODY_MEDIUM, t.colors.inverseOnSurface);
   }

   private String currentSnack() {
      long now = System.currentTimeMillis();
      if (now < this.snackUntil) {
         return this.snack;
      }
      if (now < this.resetConfirmUntil) {
         return tr("再次点击右上角菜单里的「重置所有设置」确认", "Open the overflow menu and choose Reset again to confirm");
      }
      return null;
   }

   private void renderColorDialog(Md3Theme t, int mouseX, int mouseY) {
      float cardW = Math.min(400f, this.width - 24f);
      float cardH = Math.min(430f, this.height - 12f);
      float x = (this.width - cardW) / 2f;
      float y = (this.height - cardH) / 2f;
      this.dialogX = x;
      this.dialogY = y;
      this.dialogW = cardW;
      this.dialogH = cardH;

      this.canvas.fill(0f, 0f, this.width, this.height,
              Md3Colors.alpha(t.colors.scrim, 0.58f));
      Md3Surfaces.dialog(this.canvas, t, x, y, cardW, cardH);

      this.canvas.text(tr("选择颜色", "Pick a color"), x + 24f, y + 18f,
              Md3Type.TITLE_MEDIUM, t.colors.onSurface);
      int argb = SettingsValues.hsvToArgb(this.pickerH, this.pickerS, this.pickerV, this.pickerA);
      String hex = ClientConfig.toHex(argb).toUpperCase();
      float hw = this.canvas.textWidth(hex, Md3Type.LABEL_MEDIUM);
      this.canvas.roundRect(x + cardW - 24f - hw - 16f, y + 18f, hw + 16f, 24f, 12f,
              t.colors.surfaceContainerHighest);
      Md3Text.centered(this.canvas, hex, x + cardW - 24f - hw / 2f - 8f, y + 30f,
              Md3Type.LABEL_MEDIUM, t.colors.onSurfaceVariant);

      float sw = 48f;
      float sx = x + 24f;
      float sy = y + 52f;
      SettingsValues.drawChecker(this.canvas, sx, sy, sw, sw, 6f);
      this.canvas.roundRect(sx, sy, sw, sw, 12f, argb);
      this.canvas.strokeRoundRect(sx, sy, sw, sw, 12f, 1f, t.colors.outline);

      float cell = 20f;
      float gap = 4f;
      float gx = x + 24f;
      float gy = y + 116f;
      for (int i = 0; i < PRESETS.length; i++) {
         float cx = gx + (i % 6) * (cell + gap) + cell / 2f;
         float cy = gy + (i / 6) * (cell + gap) + cell / 2f;
         SettingsValues.drawChecker(this.canvas, cx - cell / 2f, cy - cell / 2f, cell, cell, 4f);
         this.canvas.roundRect(cx - cell / 2f + 1f, cy - cell / 2f + 1f,
                 cell - 2f, cell - 2f, (cell - 2f) / 2f, PRESETS[i]);
         if (ClientUi.hit(mouseX, mouseY, cx - cell / 2f - 1f,
                 cy - cell / 2f - 1f, cell + 2f, cell + 2f)) {
            this.canvas.strokeRoundRect(cx - cell / 2f - 2f, cy - cell / 2f - 2f,
                    cell + 4f, cell + 4f, (cell + 4f) / 2f, 2f, t.colors.primary);
         }
      }

      float barX = x + 190f;
      if (barX > x + cardW - 120f) {
         barX = x + cardW * 0.47f;
      }
      float barW = Math.max(60f, cardW - (barX - x) - 52f);
      float barY = y + 58f;
      float step = Math.min(44f, (cardH - 128f) / 4f);
      this.pickerBarX = barX;
      this.pickerBarY = barY;
      this.pickerBarW = barW;
      this.pickerBarStep = step;
      String[] tags = new String[]{"H", "S", "V", "A"};
      float[] vals = new float[]{this.pickerH, this.pickerS, this.pickerV,
              this.pickerA / 255f};
      for (int i = 0; i < 4; i++) {
         float by = barY + i * step + 14f;
         this.canvas.text(tags[i], barX - 22f, by - 7f, Md3Type.LABEL_MEDIUM,
                 t.colors.onSurfaceVariant);
         this.drawGradientBar(i, barX, by, barW, vals[i]);
         String num = i == 0 ? String.format("%.0f", vals[i] * 360f)
                 : String.format("%d%%", Math.round(vals[i] * 100f));
         this.canvas.text(num, barX + barW + 8f, by - 7f, Md3Type.LABEL_MEDIUM,
                 t.colors.onSurfaceVariant);
      }

      float bw = 100f;
      float bh = 38f;
      float bx = x + cardW - 24f - bw;
      float by = y + cardH - 16f - bh;
      boolean hot = ClientUi.hit(mouseX, mouseY, bx, by, bw, bh);
      this.canvas.roundRect(bx, by, bw, bh, bh / 2f,
              hot ? Md3Colors.composite(Md3Colors.alpha(t.colors.onPrimary, 0.12f),
                      t.colors.primary) : t.colors.primary);
      Md3Text.centered(this.canvas, tr("完成", "Done"), bx + bw / 2f, by + bh / 2f,
              Md3Type.LABEL_LARGE, t.colors.onPrimary);
   }

   private void drawGradientBar(int kind, float x, float y, float w, float value) {
      if (kind == 3) {
         SettingsValues.drawChecker(this.canvas, x - 2f, y - 4f, w + 4f, 16f, 4f);
      }
      int bands = 40;
      for (int i = 0; i < bands; i++) {
         float p0 = i / (float) bands;
         float p1 = (i + 1) / (float) bands;
         int color = Md3Colors.lerp(this.gradientColor(kind, p0),
                 this.gradientColor(kind, p1), 0.5f);
         this.canvas.fill(x + 1f + (w - 2f) * p0, y - 2f,
                 (w - 2f) * (p1 - p0) + 0.5f, 12f, color);
      }
      this.canvas.strokeRoundRect(x, y - 3f, w, 14f, 7f, 1f,
              Md3Colors.alpha(ClientTheme.get().colors.outline, 0.35f));
      float thumbX = x + 7f + value * Math.max(0f, w - 14f);
      this.canvas.roundRect(thumbX - 7f, y - 5f, 14f, 18f, 7f, -1);
      this.canvas.strokeRoundRect(thumbX - 7f, y - 5f, 14f, 18f, 7f, 1f,
              Md3Colors.alpha(ClientTheme.get().colors.onSurface, 0.22f));
   }

   private int gradientColor(int kind, float v) {
      if (kind == 0) {
         return SettingsValues.hsvToArgb(v, 1f, 1f, 255);
      }
      if (kind == 1) {
         return SettingsValues.hsvToArgb(this.pickerH, v, this.pickerV, 255);
      }
      if (kind == 2) {
         return SettingsValues.hsvToArgb(this.pickerH, this.pickerS, v, 255);
      }
      return SettingsValues.hsvToArgb(this.pickerH, this.pickerS, this.pickerV,
              Math.round(v * 255f));
   }

   private void renderCrosshairDialog(Md3Theme t, int mouseX, int mouseY) {
      float cardW = Math.min(340f, this.width - 24f);
      float cardH = Math.min(410f, this.height - 12f);
      float x = (this.width - cardW) / 2f;
      float y = (this.height - cardH) / 2f;
      this.dialogX = x;
      this.dialogY = y;
      this.dialogW = cardW;
      this.dialogH = cardH;

      this.canvas.fill(0f, 0f, this.width, this.height,
              Md3Colors.alpha(t.colors.scrim, 0.58f));
      Md3Surfaces.dialog(this.canvas, t, x, y, cardW, cardH);

      this.canvas.text(tr("编辑准星", "Edit crosshair"), x + 22f, y + 16f,
              Md3Type.TITLE_MEDIUM, t.colors.onSurface);
      this.canvas.text(tr("左键点亮 / 右键擦掉，可拖动", "Left: paint / right: erase"),
              x + 22f, y + 38f, Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);

      float preview = 48f;
      float px = x + cardW - 22f - preview;
      float py = y + 14f;
      this.canvas.roundRect(px, py, preview, preview, 10f, t.colors.surfaceContainerHighest);
      float cellP = preview / 9f;
      for (int r = 0; r < 9; r++) {
         for (int c = 0; c < 9; c++) {
            if (SettingsValues.maskAt(r, c)) {
               this.canvas.fill(px + c * cellP + 1f, py + r * cellP + 1f,
                       cellP - 2f, cellP - 2f, ClientConfig.crosshairColor);
            }
         }
      }

      float grid = this.crosshairGrid(cardW, cardH);
      float gx = x + (cardW - grid) / 2f;
      float gy = y + 66f;
      float cell = grid / 9f;
      this.canvas.roundRect(gx - 6f, gy - 6f, grid + 12f, grid + 12f, 12f,
              t.colors.surfaceContainerHighest);
      for (int r = 0; r < 9; r++) {
         for (int c = 0; c < 9; c++) {
            float cx = gx + c * cell;
            float cy = gy + r * cell;
            boolean on = SettingsValues.maskAt(r, c);
            boolean hot = ClientUi.hit(mouseX, mouseY, cx, cy, cell, cell);
            this.canvas.roundRect(cx + 1f, cy + 1f, cell - 2f, cell - 2f, 4f,
                    on ? ClientConfig.crosshairColor
                            : (hot ? Md3Colors.alpha(t.colors.primary, 0.18f)
                                    : t.colors.surface));
         }
      }

      float bh = 40f;
      float bw = Math.min(96f, (cardW - 60f) / 2f);
      float by = y + cardH - 18f - bh;
      float bx2 = x + cardW - 22f - bw;
      float bx1 = bx2 - 10f - bw;
      boolean doneHot = ClientUi.hit(mouseX, mouseY, bx2, by, bw, bh);
      this.canvas.roundRect(bx2, by, bw, bh, bh / 2f,
              doneHot ? Md3Colors.composite(Md3Colors.alpha(t.colors.onPrimary, 0.12f),
                      t.colors.primary) : t.colors.primary);
      Md3Text.centered(this.canvas, tr("完成", "Done"), bx2 + bw / 2f, by + bh / 2f,
              Md3Type.LABEL_LARGE, t.colors.onPrimary);

      boolean resetHot = ClientUi.hit(mouseX, mouseY, bx1, by, bw, bh);
      this.canvas.roundRect(bx1, by, bw, bh, bh / 2f,
              resetHot ? Md3Colors.composite(Md3Colors.alpha(t.colors.primary, 0.06f),
                      t.colors.surfaceContainerHighest) : t.colors.surfaceContainerHighest);
      Md3Text.centered(this.canvas, tr("重置", "Reset"), bx1 + bw / 2f, by + bh / 2f,
              Md3Type.LABEL_LARGE, t.colors.onSurface);
   }

   /* ------------------------------------------------------------------ */
   /* input                                                              */
   /* ------------------------------------------------------------------ */

   @Override
   public void handleMouse() {
      int wheel = Mouse.getEventDWheel();
      if (wheel != 0 && this.pickerKey < 0 && !this.crosshairEditor
              && this.menuKey < 0 && !this.appMenuOpen) {
         if (this.detailGid < 0 && ClientUi.hit(this.mouseNowX, this.mouseNowY,
                 this.tabsX, this.tabsY, this.tabsW, this.tabsH)) {
            this.tabScrollX += -Integer.signum(wheel) * 54f;
         } else if (ClientUi.hit(this.mouseNowX, this.mouseNowY,
                 this.contentX, this.contentY, this.contentW, this.contentH)) {
            this.scroll.scrollBy(-Integer.signum(wheel) * 56f);
         }
      }
      super.handleMouse();
   }

   @Override
   protected void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.openFrames < 3) {
         return;
      }
      this.mouseNowX = mouseX;
      this.mouseNowY = mouseY;
      if (button == 0) {
         this.mouseDown = true;
      }

      if (this.appMenuOpen) {
         if (ClientUi.hit(mouseX, mouseY, this.menuBtnX, this.menuBtnY, this.menuBtnS, this.menuBtnS)) {
            // 再点一次三点按钮收回菜单。
            this.appMenuOpen = false;
            return;
         }
         int index = this.appMenu.indexAt(mouseX, mouseY);
         this.appMenuOpen = false;
         if (index == 0) {
            this.client.setScreen(new ModListScreen(this));
            return;
         }
         if (index == 1) {
            long now = System.currentTimeMillis();
            if (now < this.resetConfirmUntil) {
               this.resetAll();
            } else {
               this.resetConfirmUntil = now + 3000L;
               this.snack = tr("再点一次「重置所有设置」确认", "Use Reset again to confirm");
               this.snackUntil = now + 3000L;
               ClientUi.click();
            }
            return;
         }
         return;
      }

      if (this.menuKey >= 0) {
         int index = this.menu.indexAt(mouseX, mouseY);
         if (index >= 0) {
            SettingsValues.setCycle(this.menuKey, index);
            ClientConfig.saveSoon();
            ClientUi.click();
         }
         this.closeMenu();
         return;
      }

      if (this.pickerKey >= 0) {
         this.handlePickerClick(mouseX, mouseY);
         return;
      }

      if (this.crosshairEditor) {
         this.crosshairClick(mouseX, mouseY, button);
         return;
      }

      if (this.detailGid >= 0) {
         this.detailClick(mouseX, mouseY, button);
         return;
      }

      if (button == 1) {
         for (RowBox box : this.boxes) {
            if (box.kind == SettingsSpec.K_VALUE
                    && ClientUi.hit(mouseX, mouseY, box.ctrlX, box.ctrlY - 6f,
                            box.ctrlW, box.ctrlH + 12f)) {
               this.stepSlider(box.key, -1);
               return;
            }
         }
         return;
      }
      if (button != 0) {
         return;
      }

      if (ClientUi.hit(mouseX, mouseY, this.searchX, this.searchY, this.searchW, this.searchH)) {
         if (!this.search.text.isEmpty() && mouseX >= this.searchX + this.searchW - 36f) {
            this.search.clear();
            this.searchText = "";
            this.applyFilter();
            this.searchFocus = true;
            this.mouseDown = false;
            return;
         }
         this.searchFocus = true;
         this.search.focused = true;
         this.mouseDown = false;
         return;
      }
      this.searchFocus = false;
      this.search.focused = false;

      if (ClientUi.hit(mouseX, mouseY, this.closeBtnX, this.closeBtnY,
              this.closeBtnS, this.closeBtnS)) {
         this.close();
         return;
      }
      if (ClientUi.hit(mouseX, mouseY, this.menuBtnX, this.menuBtnY,
            this.menuBtnS, this.menuBtnS)) {
         // 图标按钮的 click 边沿要靠渲染帧采样，快速一点会漏；这里按下即切换，保证一定响应。
         if (this.appMenuOpen) {
            this.appMenuOpen = false;
         } else {
            this.openAppMenu();
         }
         return;
      }
      if (ClientUi.hit(mouseX, mouseY, this.themeBtnX, this.themeBtnY, this.themeBtnS, this.themeBtnS)) {
         return;
      }

      for (TabBox tab : this.tabBoxes) {
         if (ClientUi.hit(mouseX, mouseY, tab.x, tab.y, tab.w, tab.h)) {
            ClientUi.click();
            this.selectedCat = tab.index;
            this.applyFilter();
            return;
         }
      }

      // 1.0.2_2：命中区必须和画出来的条对齐（renderBar 的 x 是 contentW + 9），
      // 之前写在 +3，条右边那一段点不动，手感就是「条偏右、点不中」。
      if (this.scroll.press(mouseX, mouseY, this.contentX + this.contentW + 8f,
              this.contentY + 2f, 5f, 8f)) {
         return;
      }

      // 列表内容同样是被 pushClip 裁在 contentY..+contentH 里的：滚到顶栏 /
      // 标签栏底下的卡片不能在那些位置被点中（同样不能动 mouseDown）。
      if (!ClientUi.hit(mouseX, mouseY, this.contentX, this.contentY,
              this.contentW, this.contentH)) {
         return;
      }

      for (TileBox box : this.tileBoxes) {
         if (ClientUi.hit(mouseX, mouseY, box.x, box.y, box.w, box.h)) {
            ClientUi.click();
            if (box.gid == -1) {
               // 回放卡片：游戏内进录制页，主菜单直接开控制台。
               if (this.client.world != null) {
                  this.client.setScreen(new net.client.replay.gui.ReplayControlScreen(this));
               } else {
                  this.close();
                  net.client.replay.ReplayClient.openConsole();
               }
               return;
            }
            Group group = this.findGroup(box.gid);
            if (group != null) {
               this.openDetail(group);
            }
            return;
         }
      }

      for (RowBox box : this.boxes) {
         boolean inside = ClientUi.hit(mouseX, mouseY, box.x, box.y, box.w, box.h);
         if (box.kind == SettingsSpec.K_VALUE) {
            if (ClientUi.hit(mouseX, mouseY, box.ctrlX, box.ctrlY - 6f,
                    box.ctrlW, box.ctrlH + 12f)) {
               this.sliderKey = box.key;
               float track = Math.max(1f, box.trackW - 14f);
               float firstNorm = SettingsValues.clamp01((mouseX - box.trackX - 7f) / track);
               this.sliderNorm = firstNorm;
               this.sliderStartX = mouseX;
               this.sliderStartNorm = firstNorm;
               this.applyNormSmooth(box.key, firstNorm);
               return;
            }
         } else if (box.kind == SettingsSpec.K_TOGGLE) {
            if (inside) {
               SettingsValues.toggle(box.key);
               this.syncSwitch(box.key);
               ClientConfig.save();
               ClientUi.click();
               if (box.key == SettingsSpec.UI_OVERRIDE) {
                  this.close();
                  return;
               }
            }
         } else {
            if (inside || ClientUi.hit(mouseX, mouseY, box.ctrlX, box.ctrlY,
                    box.ctrlW, box.ctrlH)) {
               this.rowAction(box);
               return;
            }
         }
      }
   }

   private void rowAction(RowBox box) {
      if (box.kind == SettingsSpec.K_COLOR) {
         this.openPicker(box.key);
         ClientUi.click();
      } else if (box.kind == SettingsSpec.K_CYCLE) {
         this.openMenu(box.key, box);
         ClientUi.click();
      } else if (box.kind == SettingsSpec.K_ACTION) {
         if (box.key == 45) {
            this.crosshairEditor = true;
            this.editorDrag = -1;
            ClientUi.click();
         } else if (box.key == 64) {
            this.bindingKey = true;
            ClientUi.click();
         } else if (box.key == SettingsSpec.SCORE_EDIT) {
            this.client.setScreen(new ScoreboardSettingsScreen(this));
            ClientUi.click();
         }
      }
   }

   @Override
   protected void mouseReleased(int mouseX, int mouseY, int button) {
      this.mouseDown = false;
      this.sliderKey = -1;
      this.scroll.release();
      this.pickerDrag = -1;
      this.editorDrag = -1;
   }

   @Override
   protected void mouseDragged(int mouseX, int mouseY, int button, long mouseLastClicked) {
      this.mouseNowX = mouseX;
      this.mouseNowY = mouseY;
      if (this.pickerKey >= 0 && this.pickerDrag >= 0) {
         this.updatePicker(mouseX);
         return;
      }
      if (this.crosshairEditor && this.editorDrag >= 0) {
         this.crosshairDrag(mouseX, mouseY);
         return;
      }
      if (this.menuKey >= 0) {
         return;
      }
      if (this.sliderKey >= 0) {
         RowBox box = this.boxFor(this.sliderKey);
         if (box != null) {
            this.updateSlider(mouseX, box);
         }
         return;
      }
      if (this.scroll.dragging) {
         this.scroll.drag(mouseY, this.contentY + 2f);
      }
   }

   @Override
   protected void keyPressed(char id, int code) {
      if (this.openFrames < 3) {
         return;
      }

      if (this.appMenuOpen) {
         if (code == 1) {
            this.appMenuOpen = false;
         }
         return;
      }
      if (this.pickerKey >= 0) {
         if (code == 1) {
            this.pickerKey = -1;
         }
         return;
      }
      if (this.crosshairEditor) {
         if (code == 1) {
            this.crosshairEditor = false;
         }
         return;
      }
      if (this.menuKey >= 0) {
         if (code == 1) {
            this.closeMenu();
         }
         return;
      }
      if (this.bindingKey) {
         if (code != 1) {
            ClientConfig.freelookKey = code;
            ClientConfig.saveSoon();
         }
         this.bindingKey = false;
         return;
      }
      if (this.detailGid >= 0) {
         if (code == 1) {
            this.closeDetail();
         } else if (code == 208) {
            this.scroll.scrollBy(56f);
         } else if (code == 200) {
            this.scroll.scrollBy(-56f);
         }
         return;
      }
      if (this.searchFocus) {
         if (ImeBridge.handleKey(id, code)) {
            return;
         }
         if (code == 1) {
            this.search.clear();
            this.searchText = "";
            this.searchFocus = false;
            this.search.focused = false;
            this.applyFilter();
            return;
         }
         if (code == 28 || code == 156) {
            this.searchFocus = false;
            this.search.focused = false;
            return;
         }
         if (code == 14) {
            this.search.backspace();
            this.searchText = this.search.text;
            this.applyFilter();
            return;
         }
         if (id >= 32 && id != 127) {
            this.search.typeChar(id);
            this.searchText = this.search.text;
            this.applyFilter();
         }
         return;
      }
      if (code == 1) {
         this.close();
      } else if (code == 208) {
         this.scroll.scrollBy(56f);
      } else if (code == 200) {
         this.scroll.scrollBy(-56f);
      }
   }

   /* ------------------------------------------------------------------ */
   /* actions                                                            */
   /* ------------------------------------------------------------------ */

   private void close() {
      ClientConfig.save();
      // 标题界面没有世界，setScreen(null) 可能留下空屏；重建 TitleScreen，
      // 让“接管原版 UI”开关切换后立即按新值重新初始化原版按钮。
      if (this.client.world == null) {
         this.client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
      } else {
         this.client.setScreen(null);
      }
   }

   private void openMenu(int key, RowBox box) {
      this.menuKey = key;
      this.menuIndex = SettingsValues.cycleIndex(key);
      this.menu.items = SettingsValues.cycleNames(key);
      this.menu.selected = this.menuIndex;
      float width = Math.max(140f, box.ctrlW + 8f);
      float height = this.menu.height();
      float mx = box.ctrlX + box.ctrlW - width;
      if (mx < this.contentX + 8f) {
         mx = this.contentX + 8f;
      }
      float my = box.ctrlY + box.ctrlH + 4f;
      if (my + height > this.height - 8f) {
         my = box.ctrlY - height - 4f;
      }
      if (my < 8f) {
         my = 8f;
      }
      this.menu.x = mx;
      this.menu.y = my;
      this.menu.w = width;
      this.menu.open();
   }

   private void closeMenu() {
      this.menuKey = -1;
      this.menuIndex = -1;
   }

   private void openPicker(int key) {
      int color = SettingsValues.colorOf(key);
      float[] hsv = SettingsValues.argbToHsv(color);
      this.pickerH = hsv[0];
      this.pickerS = hsv[1];
      this.pickerV = hsv[2];
      this.pickerA = color >> 24 & 0xFF;
      this.pickerKey = key;
      this.pickerDrag = -1;
   }

   private void handlePickerClick(int mouseX, int mouseY) {
      float x = this.dialogX;
      float y = this.dialogY;
      float cardW = this.dialogW;
      float cardH = this.dialogH;

      float bw = 100f;
      float bh = 38f;
      float bx = x + cardW - 24f - bw;
      float by = y + cardH - 16f - bh;
      if (ClientUi.hit(mouseX, mouseY, bx, by, bw, bh)) {
         this.pickerKey = -1;
         this.pickerDrag = -1;
         ClientUi.click();
         return;
      }

      float cell = 20f;
      float gap = 4f;
      float gx = x + 24f;
      float gy = y + 116f;
      for (int i = 0; i < PRESETS.length; i++) {
         float cx = gx + (i % 6) * (cell + gap) + cell / 2f;
         float cy = gy + (i / 6) * (cell + gap) + cell / 2f;
         if (ClientUi.hit(mouseX, mouseY, cx - cell / 2f - 1f,
                 cy - cell / 2f - 1f, cell + 2f, cell + 2f)) {
            float[] hsv = SettingsValues.argbToHsv(PRESETS[i]);
            this.pickerH = hsv[0];
            this.pickerS = hsv[1];
            this.pickerV = hsv[2];
            this.pickerA = PRESETS[i] >> 24 & 0xFF;
            this.applyPicker();
            ClientUi.click();
            return;
         }
      }

      for (int i = 0; i < 4; i++) {
         float by2 = this.pickerBarY + i * this.pickerBarStep + 14f;
         if (ClientUi.hit(mouseX, mouseY, this.pickerBarX - 8f, by2 - 12f,
                 this.pickerBarW + 16f, 28f)) {
            this.pickerDrag = i;
            this.updatePicker(mouseX);
            return;
         }
      }
   }

   private void updatePicker(int mouseX) {
      float v = (mouseX - this.pickerBarX) / Math.max(1f, this.pickerBarW);
      if (v < 0f) {
         v = 0f;
      }
      if (v > 1f) {
         v = 1f;
      }
      if (this.pickerDrag == 0) {
         this.pickerH = v;
      } else if (this.pickerDrag == 1) {
         this.pickerS = v;
      } else if (this.pickerDrag == 2) {
         this.pickerV = v;
      } else if (this.pickerDrag == 3) {
         this.pickerA = Math.round(v * 255f);
      }
      this.applyPicker();
   }

   private void applyPicker() {
      if (this.pickerKey >= 0) {
         SettingsValues.setColor(this.pickerKey,
                 SettingsValues.hsvToArgb(this.pickerH, this.pickerS, this.pickerV, this.pickerA));
         ClientConfig.saveSoon();
      }
   }

   private float crosshairGrid(float cardW, float cardH) {
      float grid = Math.min(234f, cardW - 48f);
      grid = Math.min(grid, cardH - 140f);
      return Math.max(54f, grid);
   }

   private void crosshairClick(int mouseX, int mouseY, int button) {
      float cardW = this.dialogW;
      float cardH = this.dialogH;
      float x = this.dialogX;
      float y = this.dialogY;

      float bh = 40f;
      float bw = Math.min(96f, (cardW - 60f) / 2f);
      float by = y + cardH - 18f - bh;
      float bx2 = x + cardW - 22f - bw;
      float bx1 = bx2 - 10f - bw;
      if (button == 0 && ClientUi.hit(mouseX, mouseY, bx2, by, bw, bh)) {
         this.crosshairEditor = false;
         this.editorDrag = -1;
         ClientConfig.saveSoon();
         ClientUi.click();
         return;
      }
      if (button == 0 && ClientUi.hit(mouseX, mouseY, bx1, by, bw, bh)) {
         ClientConfig.crosshairMask = DEFAULT_MASK;
         ClientConfig.saveSoon();
         ClientUi.click();
         return;
      }
      if (button != 0 && button != 1) {
         return;
      }

      float grid = this.crosshairGrid(cardW, cardH);
      float gx = x + (cardW - grid) / 2f;
      float gy = y + 66f;
      float cell = grid / 9f;
      if (ClientUi.hit(mouseX, mouseY, gx, gy, grid, grid)) {
         int col = (int) ((mouseX - gx) / cell);
         int row = (int) ((mouseY - gy) / cell);
         if (col >= 0 && col < 9 && row >= 0 && row < 9) {
            SettingsValues.setMask(row, col, button == 0);
            ClientConfig.saveSoon();
            this.editorDrag = button == 0 ? 1 : 0;
         }
      }
   }

   private void crosshairDrag(int mouseX, int mouseY) {
      float grid = this.crosshairGrid(this.dialogW, this.dialogH);
      float gx = this.dialogX + (this.dialogW - grid) / 2f;
      float gy = this.dialogY + 66f;
      float cell = grid / 9f;
      if (ClientUi.hit(mouseX, mouseY, gx, gy, grid, grid)) {
         int col = (int) ((mouseX - gx) / cell);
         int row = (int) ((mouseY - gy) / cell);
         boolean on = this.editorDrag == 1;
         if (col >= 0 && col < 9 && row >= 0 && row < 9 && SettingsValues.maskAt(row, col) != on) {
            SettingsValues.setMask(row, col, on);
            ClientConfig.saveSoon();
         }
      }
   }

   private void updateSlider(int mouseX, RowBox box) {
      float track = Math.max(1f, (box.trackW > 0f ? box.trackW : box.ctrlW) - 14f);
      // 按下时先跳到点击位置，拖动时按相对位移计算；全程不做 step 吸附，
      // 否则 0~20 之类的范围会表现为“只能 5% 一跳”。
      float v = this.sliderStartNorm + (mouseX - this.sliderStartX) / track;
      this.sliderNorm = SettingsValues.clamp01(v);
      this.applyNormSmooth(box.key, this.sliderNorm);
   }

   private void applyNormSmooth(int key, float norm) {
      float min = SettingsValues.minOf(key);
      float max = SettingsValues.maxOf(key);
      SettingsValues.setValue(key, min + norm * (max - min));
      ClientConfig.saveSoon();
   }

   private void stepSlider(int key, int dir) {
      float norm = SettingsValues.clamp01(SettingsValues.normOf(key)
              + dir * SettingsValues.stepOf(key) / (SettingsValues.maxOf(key) - SettingsValues.minOf(key)));
      SettingsValues.applyNorm(key, norm);
      ClientUi.click();
   }

   private RowBox boxFor(int key) {
      for (RowBox box : this.boxes) {
         if (box.key == key) {
            return box;
         }
      }
      return null;
   }

   private void resetAll() {
      ClientConfig.resetAll();
      // 模糊强度 / 淡入时长 / 渐变色 / 模糊聊天 存在 Blur 模组自己的 blur.json 里，
      // ClientConfig 的反射重置覆盖不到，必须单独写回出厂值（1.0.2_7）。
      com.amethyst.blur.Blur.resetDefaults();
      ClientConfig.save();
      ImeBridge.onConfigChanged();
      this.lastSwitchOn.clear();
      this.pickerKey = -1;
      this.crosshairEditor = false;
      this.resetConfirmUntil = 0L;
      this.snack = tr("已恢复默认设置", "Defaults restored");
      this.snackUntil = System.currentTimeMillis() + 2400L;
      this.buildGroups();
      this.applyFilter();
      this.scroll.jump(0f);
      ClientUi.click();
   }

   /* ------------------------------------------------------------------ */
   /* model helpers                                                      */
   /* ------------------------------------------------------------------ */

   private String label(int key) {
      int i = key - 1;
      return i >= 0 && i < SettingsSpec.LABELS.length
              ? SettingsSpec.LABELS[i][ClientConfig.langIndex()] : "";
   }

   private String actionText(int key) {
      if (key == 45) {
         return tr("编辑", "Edit");
      }
      if (key == 64) {
         return Keyboard.getKeyName(ClientConfig.freelookKey);
      }
      return tr("打开", "Open");
   }

   private void syncSwitch(int key) {
      Md3Switch sw = this.switches.get(key);
      if (sw != null) {
         sw.setCheckedAnimated(SettingsValues.isOn(key));
      }
      this.lastSwitchOn.put(key, Boolean.valueOf(SettingsValues.isOn(key)));
   }

   private String tr(String zh, String en) {
      return ClientConfig.t(zh, en);
   }

   @Override
   public boolean shouldPauseGame() {
      return false;
   }

   /* ------------------------------------------------------------------ */
   /* small data holders                                                 */
   /* ------------------------------------------------------------------ */

   private static final class Row {
      final int kind;
      final int key;

      Row(int kind, int key) {
         this.kind = kind;
         this.key = key;
      }
   }

   private static final class Unit {
      final Row head;
      final List<Row> rows = new ArrayList<Row>();

      Unit(Row head) {
         this.head = head;
      }
   }

   private static final class Group {
      final int id;
      final List<Unit> units = new ArrayList<Unit>();

      Group(int id) {
         this.id = id;
      }

      String nameZh() {
         int i = this.id - 200;
         return i >= 0 && i < SettingsSpec.GROUP_NAME_ZH.length
                 ? SettingsSpec.GROUP_NAME_ZH[i] : "";
      }

      String nameEn() {
         int i = this.id - 200;
         return i >= 0 && i < SettingsSpec.GROUP_NAME_EN.length
                 ? SettingsSpec.GROUP_NAME_EN[i] : "";
      }

      String name() {
         return ClientConfig.langIndex() == 0 ? this.nameZh() : this.nameEn();
      }

      int rowCount() {
         int n = 0;
         for (Unit unit : this.units) {
            n += 1 + unit.rows.size();
         }
         return n;
      }
   }

   private static final class Tile {
      final Group group;
      float x;
      float y;
      float w;

      Tile(Group group) {
         this.group = group;
      }
   }

   private static final class TileBox {
      int gid;
      float x;
      float y;
      float w;
      float h;
   }

   private static final class TabBox {
      int index;
      float x;
      float y;
      float w;
      float h;
   }

   private static final class RowBox {
      int kind;
      int key;
      float x;
      float y;
      float w;
      float h;
      float ctrlX;
      float ctrlY;
      float ctrlW;
      float ctrlH;
      /** 1.0.2_1：滑块轨道的实际范围，点击位置换算百分比时用它，避免命中区放大后数值跳偏。 */
      float trackX;
      float trackW;
   }
}
