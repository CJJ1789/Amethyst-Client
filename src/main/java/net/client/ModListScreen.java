package net.client;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3IconButton;
import net.client.ui.md3.widget.Md3ListItem;
import net.client.ui.md3.widget.Md3Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.client.ui.ClientScroll;
import net.client.ui.ClientTheme;
import net.client.ui.ClientUi;
import net.client.util.CurseForgeUpdater;
import net.client.util.EnvCheck;
import net.client.util.ClientLog;
import net.client.util.ModrinthUpdater;
import net.client.util.OptiFineUpdater;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;

/** Installed mods / environment / update status, rebuilt as an MD3 list. */
public class ModListScreen extends Screen {
   private static final int ROW_H = 48;

   private final Screen parent;
   private final List<Entry> mods = new ArrayList<Entry>();
   private final Md3Canvas canvas = new Md3Canvas();
   private final Md3IconButton closeButton = new Md3IconButton();
   private final ClientScroll scroll = new ClientScroll();

   private float cardX;
   private float cardY;
   private float cardW;
   private float cardH;
   private float listX;
   private float listY;
   private float listW;
   private float listH;
   private float[] rowYs = new float[0];
   private long lastNanos;
   private float dt;
   private boolean mouseDown;

   public ModListScreen(Screen parent) {
      this.parent = parent;
      this.loadMods();
      this.startUpdateCheck();
      CurseForgeUpdater.checkAll(this.ids(), this.names(), this.versions());
      EnvCheck.ensureStarted();
      OptiFineUpdater.ensureStarted();
   }

   private List<String> ids() {
      List<String> out = new ArrayList<String>();
      for (Entry e : this.mods) {
         out.add(e.id);
      }
      return out;
   }

   private Map<String, String> names() {
      Map<String, String> out = new HashMap<String, String>();
      for (Entry e : this.mods) {
         out.put(e.id, e.name);
      }
      return out;
   }

   private Map<String, String> versions() {
      Map<String, String> out = new HashMap<String, String>();
      for (Entry e : this.mods) {
         out.put(e.id, e.version);
      }
      return out;
   }

   private void startUpdateCheck() {
      List<String> ids = new ArrayList<String>();
      Map<String, String> versions = new HashMap<String, String>();
      for (Entry e : this.mods) {
         ids.add(e.id);
         versions.put(e.id, e.version);
      }
      ModrinthUpdater.checkAll(ids, versions);
   }

   private void loadMods() {
      this.mods.clear();
      try {
         for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            String name = meta.getName();
            String id = meta.getId();
            String version = meta.getVersion() == null ? "" : meta.getVersion().getFriendlyString();
            if (name == null || name.trim().isEmpty()) {
               name = id;
            }
            this.mods.add(new Entry(name, id, version));
         }
      } catch (Exception e) {
         ClientLog.warn("读取模组元数据失败，跳过异常条目", e);
      }
      this.mods.sort(Comparator.comparing(e -> e.name == null ? "" : e.name.toLowerCase()));
   }

   @Override
   public void init() {
      this.buttons.clear();
      // 模组菜单是居中卡片，不贴屏幕边框；纯色 surface 底，宽约 62%、高约 78%。
      this.cardW = panelWidth(this.width);
      this.cardH = panelHeight(this.height);
      this.cardX = (this.width - this.cardW) / 2f;
      this.cardY = (this.height - this.cardH) / 2f;
      this.listX = this.cardX + 18f;
      // 不再在列表上方放 Java / Legacy Fabric / OptiFine 黑框；检测结果直接
      // 并入对应模组行（Fabric Loader、OptiFine、Minecraft），列表占据剩余高度。
      this.listY = this.cardY + 62f;
      this.listW = this.cardW - 36f;
      this.listH = Math.max(96f, this.cardH - 76f);

      this.closeButton.variant = Md3IconButton.Variant.STANDARD;
      this.closeButton.icon = "close";
      this.closeButton.setBounds(this.cardX + this.cardW - 50f, this.cardY + 12f, 38f, 38f);
      this.scroll.jump(0f);
   }

   @Override
   public void render(int mouseX, int mouseY, float tickDelta) {
      long now = System.nanoTime();
      this.dt = this.lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - this.lastNanos) / 1.0E9f);
      this.lastNanos = now;

      super.render(mouseX, mouseY, tickDelta);
      Md3Theme t = ClientTheme.get();
      ClientUi.panoramaLayer(this.width, this.height);
      this.canvas.begin(this.width, this.height);
      ClientUi.backdrop(this.canvas, t, this.width, this.height,
              this.client != null && this.client.world != null);

      this.canvas.shadow(this.cardX, this.cardY, this.cardW, this.cardH, 28f, 4);
      this.canvas.roundRect(this.cardX, this.cardY, this.cardW, this.cardH, 28f,
              t.colors.surfaceContainerHigh);

      this.canvas.text(tr("已安装模组", "Installed mods"), this.cardX + 24f, this.cardY + 18f,
              Md3Type.TITLE_LARGE, t.colors.onSurface);
      String count = Integer.toString(this.mods.size());
      float tw = this.canvas.textWidth(tr("已安装模组", "Installed mods"), Md3Type.TITLE_LARGE);
      this.canvas.roundRect(this.cardX + 24f + tw + 10f, this.cardY + 18f, 28f, 22f, 11f,
              t.colors.primaryContainer);
      Md3Text.centered(this.canvas, count, this.cardX + 24f + tw + 24f, this.cardY + 29f,
              Md3Type.LABEL_MEDIUM, t.colors.onPrimaryContainer);

      this.closeButton.surface = t.colors.surfaceContainerHigh;
      this.closeButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.closeButton.consumeClick()) {
         this.close();
      }

      this.renderList(t, mouseX, mouseY);

      this.canvas.end();
   }

   /** 1.0.2_1：设置页与模组页共用同一组尺寸常量，保证两张卡完全一致。 */
   public static float panelWidth(float screenWidth) {
      return Math.min(720f, screenWidth * 0.62f);
   }

   public static float panelHeight(float screenHeight) {
      return Math.min(540f, screenHeight * 0.78f);
   }

   private void renderList(Md3Theme t, int mouseX, int mouseY) {
      this.canvas.roundRect(this.listX, this.listY, this.listW, this.listH, 16f,
              t.colors.surfaceContainerLow);
      float innerH = this.listH - 8f;
      this.scroll.measure(this.mods.size() * ROW_H, innerH);
      this.scroll.update(this.dt);
      this.canvas.pushClip(this.listX + 4f, this.listY + 4f, this.listW - 8f, innerH);
      this.rowYs = new float[this.mods.size()];

      float base = this.listY + 4f - this.scroll.value;
      for (int i = 0; i < this.mods.size(); i++) {
         float y = base + i * ROW_H;
         this.rowYs[i] = y;
         if (y + ROW_H < this.listY || y > this.listY + this.listH) {
            continue;
         }
         Entry entry = this.mods.get(i);
         Md3ListItem item = new Md3ListItem();
         item.headline = entry.name;
         item.supporting = this.supporting(entry);
         item.leadingText = entry.name;
         item.trailingText = entry.version;
         item.setBounds(this.listX + 6f, y, this.listW - 12f, ROW_H);
         item.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
         if (i < this.mods.size() - 1) {
            this.canvas.fill(this.listX + 30f, y + ROW_H - 1f, this.listW - 60f, 1f,
                    t.colors.outlineVariant);
         }
      }
      this.canvas.popClip();
      this.scroll.renderBar(this.canvas, t, this.listX + this.listW - 8f,
              this.listY + 8f, 4f);
   }

   private String supporting(Entry entry) {
      List<String> notes = new ArrayList<String>();

      if (isLoader(entry)) {
         notes.add(this.loaderNote(entry));
      }
      if (isOptiFine(entry)) {
         notes.add(this.optiFineNote());
      }

      ModrinthUpdater.Info info = ModrinthUpdater.get(entry.id);
      if (info.state == ModrinthUpdater.State.CHECKING) {
         notes.add(tr("检查中…", "checking…"));
      } else if (info.state == ModrinthUpdater.State.OUTDATED) {
         notes.add(tr("Modrinth 可更新 " + info.latest + " · 点击打开",
                 "Modrinth update " + info.latest + " · click to open"));
      } else {
         CurseForgeUpdater.Info cf = CurseForgeUpdater.get(entry.id);
         if (cf.state == CurseForgeUpdater.State.OUTDATED) {
            notes.add(tr("CurseForge " + cf.latest + " · 点击打开",
                    "CurseForge " + cf.latest + " · click to open"));
         }
      }

      if (notes.isEmpty()) {
         return entry.id;
      }
      StringBuilder out = new StringBuilder(entry.id);
      for (String note : notes) {
         if (note == null || note.isEmpty()) {
            continue;
         }
         out.append("  ·  ").append(note);
      }
      return out.toString();
   }

   /** Legacy Fabric 加载器检测结果并入 Fabric Loader 行，不再占用上方独立黑框。 */
   private String loaderNote(Entry entry) {
      EnvCheck.Status status = EnvCheck.loader();
      String text = ClientConfig.langIndex() == 0 ? status.zh : status.en;
      if (text == null) {
         return "";
      }
      String zhHead = "Legacy Fabric 加载器 ";
      String enHead = "Legacy Fabric loader ";
      if (text.startsWith(zhHead)) {
         text = text.substring(zhHead.length());
      } else if (text.startsWith(enHead)) {
         text = text.substring(enHead.length());
      }
      String versionHead = entry.version + " · ";
      if (text.startsWith(versionHead)) {
         text = text.substring(versionHead.length());
      }
      return text;
   }

   /** OptiFine 本体已在模组列表里时，把官网版本/是否最新写进它自己的说明行。 */
   private String optiFineNote() {
      OptiFineUpdater.Info info = OptiFineUpdater.get();
      if (info == null) {
         return tr("OptiFine 检查中…", "checking OptiFine…");
      }
      if (info.local == null) {
         return tr("OptiFine 未检测到本体", "OptiFine not installed");
      }
      if (info.latest == null) {
         return tr("OptiFine " + info.local + " · 官网版本未知",
                 "OptiFine " + info.local + " · official version unknown");
      }
      if (info.outdated) {
         return tr("OptiFine " + info.local + " · 有新版 " + stripHdU(info.latest),
                 "OptiFine " + info.local + " · newer " + stripHdU(info.latest));
      }
      return tr("OptiFine " + info.local + " · 已是官网最新",
              "OptiFine " + info.local + " · latest on optifine.net");
   }

   private static boolean isLoader(Entry entry) {
      String id = entry.id == null ? "" : entry.id.toLowerCase();
      String name = entry.name == null ? "" : entry.name.toLowerCase();
      return id.equals("fabricloader") || name.contains("fabric loader");
   }

   private static boolean isOptiFine(Entry entry) {
      String id = entry.id == null ? "" : entry.id.toLowerCase();
      String name = entry.name == null ? "" : entry.name.toLowerCase();
      return id.contains("optifine") || id.contains("optifabric")
            || name.contains("optifine") || name.contains("optifabric");
   }

   private static String stripHdU(String v) {
      return v == null ? "" : v.replace("HD_U_", "");
   }

   @Override
   public void handleMouse() {
      int wheel = Mouse.getEventDWheel();
      if (wheel != 0) {
         this.scroll.scrollBy(-Integer.signum(wheel) * 52f);
      }
      super.handleMouse();
   }

   @Override
   protected void mouseClicked(int mouseX, int mouseY, int button) {
      if (button == 0) {
         this.mouseDown = true;
      }
      if (button != 0) {
         return;
      }
      if (ClientUi.hit(mouseX, mouseY, this.closeButton.x, this.closeButton.y,
              this.closeButton.w, this.closeButton.h)) {
         return;
      }
      if (this.scroll.press(mouseX, mouseY, this.listX + this.listW - 8f,
              this.listY + 8f, 4f, 4f)) {
         return;
      }
      for (int i = 0; i < this.rowYs.length && i < this.mods.size(); i++) {
         float y = this.rowYs[i];
         if (y < this.listY || y + ROW_H > this.listY + this.listH) {
            continue;
         }
         if (!ClientUi.hit(mouseX, mouseY, this.listX + 6f, y, this.listW - 12f, ROW_H)) {
            continue;
         }
         Entry entry = this.mods.get(i);
         ModrinthUpdater.Info info = ModrinthUpdater.get(entry.id);
         if (info.state == ModrinthUpdater.State.OUTDATED && info.page != null) {
            ModrinthUpdater.openPage(info.page);
            return;
         }
         CurseForgeUpdater.Info cf = CurseForgeUpdater.get(entry.id);
         if (cf.state == CurseForgeUpdater.State.OUTDATED && cf.page != null) {
            ModrinthUpdater.openPage(cf.page);
            return;
         }
         return;
      }
   }

   @Override
   protected void mouseReleased(int mouseX, int mouseY, int button) {
      this.mouseDown = false;
      this.scroll.release();
   }

   @Override
   protected void mouseDragged(int mouseX, int mouseY, int button, long lastClick) {
      if (this.scroll.dragging) {
         this.scroll.drag(mouseY, this.listY + 8f);
      }
   }

   @Override
   protected void keyPressed(char id, int code) {
      if (code == 1) {
         this.close();
      } else if (code == 208) {
         this.scroll.scrollBy(52f);
      } else if (code == 200) {
         this.scroll.scrollBy(-52f);
      }
   }

   private void close() {
      this.client.setScreen(this.parent);
   }

   private String tr(String zh, String en) {
      return ClientConfig.t(zh, en);
   }

   @Override
   public boolean shouldPauseGame() {
      return true;
   }

   private static final class Entry {
      final String name;
      final String id;
      final String version;

      Entry(String name, String id, String version) {
         this.name = name;
         this.id = id;
         this.version = version;
      }
   }
}
