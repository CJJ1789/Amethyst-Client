// Upstream: FastPacks / McPacks 1.0 (Forge 1.8.9), MIT
//   https://modrinth.com/mod/fastpacks (author: McPacks Team; Modrinth owner: Dulandor)
// Ported and modified for Legacy Fabric by this project; upstream MIT notice retained
// (see licenses/FASTPACKS-MIT.txt). The port was never released standalone.

package com.mcpacks.gui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Button;
import net.client.ui.md3.widget.Md3IconButton;
import net.client.ui.md3.widget.Md3Text;
import net.client.ui.md3.widget.Md3TextField;
import com.mcpacks.async.AsyncIconLoader;
import com.mcpacks.core.PackEntry;
import com.mcpacks.core.PackManager;
import com.mcpacks.core.PackType;
import com.mcpacks.repository.FolderPackRepository;
import com.mcpacks.util.FolderUtils;
import com.mcpacks.util.Lang;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.client.ime.ImeBridge;
import net.client.ui.ClientScroll;
import net.client.ui.ClientTheme;
import net.client.ui.ClientUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.ResourcePackLoader.Entry;
import net.minecraft.util.Identifier;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Two-pane resource-pack browser rebuilt on MD3.  The left pane lists
 * available packs / folders (with breadcrumb navigation), the right pane lists
 * the enabled stack in priority order.
 */
public class GuiFastResourcePacks extends Screen {
   private int rowH = 56;

   private final Screen parentScreen;
   private final FolderPackRepository repo;
   private final List<PackEntry> selectedPacks = new ArrayList<PackEntry>();
   private final List<PackEntry> availablePacks = new ArrayList<PackEntry>();
   private final Md3Canvas canvas = new Md3Canvas();
   private final Md3TextField search = new Md3TextField();
   private final ClientScroll availableScroll = new ClientScroll();
   private final ClientScroll selectedScroll = new ClientScroll();
   private final List<Crumb> crumbs = new ArrayList<Crumb>();
   private final Md3IconButton closeButton = new Md3IconButton();
   private final Md3Button btnDone = new Md3Button(Md3Button.Variant.FILLED, "");
   private final Md3Button btnOpen = new Md3Button(Md3Button.Variant.TONAL, "");
   private final Md3Button btnNew = new Md3Button(Md3Button.Variant.OUTLINED, "");
   private final Md3Button btnRefresh = new Md3Button(Md3Button.Variant.OUTLINED, "");
   private float[] availRowYs = new float[0];
   private float[] selRowYs = new float[0];

   private float cardX;
   private float cardY;
   private float cardH;
   private float availX;
   private float rightX;
   private float colW;
   private float footerY;
   private float availListY;
   private float availListH;
   private float selListY;
   private float selListH;
   private float crumbY;
   private boolean compactHeader;
   private boolean ultraCompact;
   private boolean iconOnlyFooter;

   private boolean searchFocus;
   private boolean isOpeningSubDialog;
   private boolean awaitingIndexRefresh;
   private boolean saved;
   private long lastNanos;
   private float dt;
   private boolean mouseDown;
   private float mouseNowX;
   private float mouseNowY;

   // RowGlide-style drag state.  The press is deferred until release so a
   // click on the text area still selects a pack, while a real drag can move
   // it to another position or to the other column.
   private static final float DRAG_THRESHOLD = 5f;
   private boolean dragPending;
   private boolean dragActive;
   private boolean dragFromSelected;
   private PackEntry dragEntry;
   private int dragSourceDisplay;
   private float dragStartX;
   private float dragStartY;
   private int dragMode; // 0 none, 1 selected column, 2 available column
   private int dragSlot;

   public GuiFastResourcePacks(Screen parentScreen) {
      this.parentScreen = parentScreen;
      this.repo = new FolderPackRepository(MinecraftClient.getInstance());
   }

   @Override
   public void init() {
      Keyboard.enableRepeatEvents(true);
      this.buttons.clear();
      this.saved = false;
      this.isOpeningSubDialog = false;
      this.clearResourceDrag();
      AsyncIconLoader.getInstance().initDefaultTextures();
      if (!PackManager.getInstance().getIndexer().hasIndex()) {
         PackManager.getInstance().refresh();
      }

      // 1920x1080 at GUI scale 4 is a 480x270 logical window: much narrower
      // than the phone layout this screen was tuned on.  Switch margins,
      // header height, row height and footer buttons together.
      this.ultraCompact = this.width < 560f || this.height < 300f;
      float margin = this.ultraCompact ? 8f : 12f;
      float gap = this.ultraCompact ? 8f : 12f;
      this.cardX = margin;
      this.cardY = margin;
      this.cardH = this.height - margin * 2f;
      this.colW = (this.width - margin * 2f - gap) / 2f;
      this.availX = this.cardX;
      this.rightX = this.cardX + this.colW + gap;
      this.footerY = this.cardY + this.cardH - (this.ultraCompact ? 44f : 50f);
      this.compactHeader = this.colW < 390f;
      this.rowH = this.ultraCompact ? 40 : (this.compactHeader ? 46 : 56);
      this.crumbY = this.cardY + (this.ultraCompact ? 80f : (this.compactHeader ? 78f : 64f));
      this.availListY = this.cardY + (this.ultraCompact ? 106f : (this.compactHeader ? 98f : 112f));
      this.availListH = Math.max(30f, this.footerY - (this.ultraCompact ? 6f : 8f) - this.availListY);
      this.selListY = this.cardY + (this.ultraCompact ? 56f : (this.compactHeader ? 50f : 52f));
      this.selListH = Math.max(30f, this.footerY - (this.ultraCompact ? 20f : 28f) - this.selListY);

      this.search.variant = Md3TextField.Variant.OUTLINED;
      this.search.label = "";
      this.search.supporting = "";
      this.search.leadingIcon = "search";
      this.search.maxLength = 40;
      float searchW = this.compactHeader ? this.colW - (this.ultraCompact ? 24f : 36f)
              : Math.min(210f, this.colW * 0.46f);
      float searchX = this.compactHeader ? this.availX + (this.ultraCompact ? 12f : 18f)
              : this.availX + this.colW - 18f - searchW;
      float searchY = this.cardY + (this.compactHeader ? 40f : 12f);
      this.search.setBounds(searchX, searchY, searchW,
              this.ultraCompact ? 28f : (this.compactHeader ? 34f : 40f));

      this.closeButton.variant = Md3IconButton.Variant.STANDARD;
      this.closeButton.icon = "close";
      float closeSize = this.ultraCompact ? 32f : 38f;
      this.closeButton.setBounds(this.rightX + this.colW - (this.ultraCompact ? 8f : 10f) - closeSize,
              this.cardY + (this.ultraCompact ? 8f : 10f), closeSize, closeSize);

      float footerPad = this.ultraCompact ? 10f : 18f;
      float footerH = this.ultraCompact ? 34f : 40f;
      float leftAvail = this.colW - footerPad * 2f;
      this.iconOnlyFooter = leftAvail < 286f;
      if (this.iconOnlyFooter) {
         float bs = this.ultraCompact ? 34f : 40f;
         this.btnOpen.setBounds(this.availX + footerPad, this.footerY, bs, footerH);
         this.btnNew.setBounds(this.btnOpen.x + bs + 8f, this.footerY, bs, footerH);
         this.btnRefresh.setBounds(this.btnNew.x + bs + 8f, this.footerY, bs, footerH);
      } else {
         float normal = 306f;
         float scale = leftAvail >= normal ? 1f : Math.max(0.7f, (leftAvail - 16f) / 290f);
         float w1 = 104f * scale;
         float w2 = 110f * scale;
         float w3 = 76f * scale;
         this.btnOpen.setBounds(this.availX + footerPad, this.footerY, w1, footerH);
         this.btnNew.setBounds(this.btnOpen.x + w1 + 8f, this.footerY, w2, footerH);
         this.btnRefresh.setBounds(this.btnNew.x + w2 + 8f, this.footerY, w3, footerH);
      }

      float rightAvail = this.colW - footerPad * 2f;
      float rightGap = 8f;
      float doneW = Math.min(100f, Math.max(70f, rightAvail * 0.47f));
      this.btnDone.setBounds(this.rightX + this.colW - footerPad - doneW,
              this.footerY, doneW, footerH);
      this.btnDone.label = Lang.s("Done", "完成");
      this.btnOpen.label = Lang.s("Open Folder", "打开文件夹");
      this.btnNew.label = Lang.s("New Folder", "新建文件夹");
      this.btnRefresh.label = Lang.s("Refresh", "刷新");
      if (this.iconOnlyFooter) {
         this.btnOpen.label = "";
         this.btnNew.label = "";
         this.btnRefresh.label = "";
      }
      this.btnOpen.icon = "folder_open";
      this.btnNew.icon = "create_new_folder";
      this.btnRefresh.icon = "refresh";
      this.btnDone.icon = "check";

      if (this.selectedPacks.isEmpty()) {
         this.loadInitialSelectedPacks();
      } else {
         this.rebuildSelectedList();
      }
      this.refreshAvailableEntries();
      this.awaitingIndexRefresh = PackManager.getInstance().getIndexer().isIndexing();
      this.availableScroll.jump(0f);
      this.selectedScroll.jump(0f);
   }

   private void loadInitialSelectedPacks() {
      this.selectedPacks.clear();
      File rootDir = PackManager.getInstance().getRootDir();
      for (Object obj : this.repo.getSelectedRepositoryEntries()) {
         if (obj instanceof Entry) {
            Entry entry = (Entry) obj;
            File packFile = FolderPackRepository.getFileFromEntry(entry);
            if (packFile != null && packFile.exists()) {
               int depth = FolderUtils.calculateDepth(rootDir, packFile.getParentFile());
               PackEntry pe = new PackEntry(PackType.PACK, packFile, rootDir, depth);
               pe.setRepositoryEntry(entry);
               this.selectedPacks.add(pe);
            }
         }
      }
      this.rebuildSelectedList();
   }

   public void refreshAvailableEntries() {
      this.availablePacks.clear();
      PackManager pm = PackManager.getInstance();
      String query = this.search.text.trim();
      for (PackEntry entry : query.length() >= 2
              ? pm.getIndexer().searchGlobal(query)
              : pm.getEntriesInCurrentFolder(query)) {
         if (entry.isPack() && this.isPackAlreadySelected(entry)) {
            continue;
         }
         this.availablePacks.add(entry);
      }
      this.btnNew.enabled = FolderUtils.canCreateSubfolder(pm.getRootDir(), pm.getCurrentDir());
   }

   public void rebuildSelectedList() {
      // display order is high -> low priority (top of the list wins)
      this.selRowYs = new float[this.selectedPacks.size()];
   }

   private boolean isPackAlreadySelected(PackEntry pack) {
      if (pack == null || pack.getFile() == null) {
         return false;
      }
      String path = pack.getFile().getAbsolutePath();
      for (PackEntry selected : this.selectedPacks) {
         if (selected.getFile() != null
                 && selected.getFile().getAbsolutePath().equalsIgnoreCase(path)) {
            return true;
         }
      }
      return false;
   }

   public void selectPack(PackEntry entry) {
      if (entry != null && entry.isPack() && !this.isPackAlreadySelected(entry)) {
         this.selectedPacks.add(entry);
         this.rebuildSelectedList();
         this.refreshAvailableEntries();
      }
   }

   public void unselectPack(PackEntry entry) {
      if (entry != null) {
         this.selectedPacks.remove(entry);
         this.rebuildSelectedList();
         this.refreshAvailableEntries();
      }
   }

   public void movePackUp(int index) {
      int internal = this.selectedPacks.size() - 1 - index;
      if (internal >= 0 && internal < this.selectedPacks.size() - 1) {
         Collections.swap(this.selectedPacks, internal, internal + 1);
         this.rebuildSelectedList();
      }
   }

   public void movePackDown(int index) {
      int internal = this.selectedPacks.size() - 1 - index;
      if (internal > 0 && internal < this.selectedPacks.size()) {
         Collections.swap(this.selectedPacks, internal, internal - 1);
         this.rebuildSelectedList();
      }
   }

   private void cancelAndClose() {
      this.saved = true;
      this.client.setScreen(this.parentScreen);
   }

   public void saveAndClose() {
      if (!this.saved) {
         this.saved = true;
         this.repo.applySelectedPacks(this.selectedPacks);
      }
      this.client.setScreen(this.parentScreen);
   }

   @Override
   public void tick() {
      if (this.awaitingIndexRefresh && !PackManager.getInstance().getIndexer().isIndexing()) {
         this.awaitingIndexRefresh = false;
         this.refreshAvailableEntries();
      }
      super.tick();
   }

   @Override
   public void removed() {
      Keyboard.enableRepeatEvents(false);
      if (!this.saved && !this.isOpeningSubDialog) {
         this.saved = true;
         this.repo.applySelectedPacks(this.selectedPacks);
      }
      this.isOpeningSubDialog = false;
      super.removed();
   }

   /* ------------------------------------------------------------------ */
   /* render                                                             */
   /* ------------------------------------------------------------------ */

   @Override
   public void render(int mouseX, int mouseY, float tickDelta) {
      this.mouseNowX = mouseX;
      this.mouseNowY = mouseY;
      long now = System.nanoTime();
      this.dt = this.lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - this.lastNanos) / 1.0E9f);
      this.lastNanos = now;

      super.render(mouseX, mouseY, tickDelta);

      Md3Theme t = ClientTheme.get();
      this.canvas.begin(this.width, this.height);
      // 纯色底：不再使用带光晕的 backdrop，避免页面显得花和挤。
      this.canvas.fill(0f, 0f, this.width, this.height, t.colors.surface);
      // 左右两块大背景卡片占满高度，不再套一层外框。
      this.canvas.roundRect(this.availX, this.cardY, this.colW, this.cardH, 24f,
              t.colors.surfaceContainerLow);
      this.canvas.roundRect(this.rightX, this.cardY, this.colW, this.cardH, 24f,
              t.colors.surfaceContainerLow);

      this.renderHeader(t, mouseX, mouseY);
      this.renderBreadcrumbs(t, mouseX, mouseY);
      this.renderAvailable(t, mouseX, mouseY);
      this.renderSelected(t, mouseX, mouseY);
      this.renderFooter(t, mouseX, mouseY);
      this.renderDragGhost(t);

      this.canvas.end();
   }

   private void renderHeader(Md3Theme t, int mouseX, int mouseY) {
      this.canvas.text(Lang.s("Resource Packs", "资源包"), this.availX + 18f, this.cardY + 14f,
              Md3Type.TITLE_LARGE, t.colors.onSurface);
      String count = this.selectedPacks.size() + " / " + (this.selectedPacks.size()
              + this.availablePacks.size());
      float tw = this.canvas.textWidth(Lang.s("Resource Packs", "资源包"), Md3Type.TITLE_LARGE);
      this.canvas.roundRect(this.availX + 18f + tw + 10f, this.cardY + 14f,
              this.canvas.textWidth(count, Md3Type.LABEL_MEDIUM) + 16f, 22f, 11f,
              t.colors.primaryContainer);
      Md3Text.centered(this.canvas, count, this.availX + 18f + tw + 18f
              + this.canvas.textWidth(count, Md3Type.LABEL_MEDIUM) / 2f,
              this.cardY + 25f, Md3Type.LABEL_MEDIUM, t.colors.onPrimaryContainer);

      this.search.trailingIcon = this.search.text.isEmpty() ? null : "close";
      this.search.surface = t.colors.surfaceContainer;
      this.search.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.searchFocus) {
         ImeBridge.onFieldRender((int) this.search.x + 16, (int) this.search.y + 28,
                 (int) this.search.w - 32, this.search.text,
                 this.search.text.length(), true);
      }
      if (this.search.text.isEmpty()) {
         this.canvas.text(Lang.s("Search packs...", "搜索资源包..."),
                 this.search.x + (this.ultraCompact ? 36f : 44f),
                 Md3Text.topForLineCenter(this.search.y + this.search.h / 2f, Md3Type.BODY_MEDIUM),
                 Md3Type.BODY_MEDIUM, Md3Colors.alpha(t.colors.onSurfaceVariant, 0.72f));
      }

   }

   private void renderBreadcrumbs(Md3Theme t, int mouseX, int mouseY) {
      this.crumbs.clear();
      float y = this.crumbY;
      this.canvas.text(Lang.s("Path: ", "路径: "), this.availX + 18f, y + 7f,
              Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);
      float x = this.availX + 18f + this.canvas.textWidth(Lang.s("Path: ", "路径: "),
              Md3Type.BODY_SMALL) + 4f;
      PackManager pm = PackManager.getInstance();
      List<PackManager.BreadcrumbNode> nodes = pm.getBreadcrumbs();
      float limit = this.availX + this.colW - 96f;
      for (int i = 0; i < nodes.size(); i++) {
         PackManager.BreadcrumbNode node = nodes.get(i);
         String label = ClientUi.fit(this.canvas, i == 0 ? Lang.s("Root", "根目录") : node.name,
                 Md3Type.LABEL_MEDIUM, 110f);
         float pw = this.canvas.textWidth(label, Md3Type.LABEL_MEDIUM) + 16f;
         if (x + pw > limit && i > 0) {
            this.canvas.text("…", x, y + 6f, Md3Type.LABEL_MEDIUM, t.colors.onSurfaceVariant);
            break;
         }
         boolean current = i == nodes.size() - 1;
         boolean hot = ClientUi.hit(mouseX, mouseY, x, y, pw, 22f);
         int bg = current ? t.colors.primaryContainer
                 : (hot ? t.colors.surfaceContainerHighest : t.colors.surfaceContainer);
         this.canvas.roundRect(x, y, pw, 22f, 11f, bg);
         Md3Text.centered(this.canvas, label, x + pw / 2f, y + 11f, Md3Type.LABEL_MEDIUM,
                 current ? t.colors.onPrimaryContainer : t.colors.onSurfaceVariant);
         Crumb crumb = new Crumb();
         crumb.x = x;
         crumb.y = y;
         crumb.w = pw;
         crumb.h = 22f;
         crumb.dir = node.targetDir;
         this.crumbs.add(crumb);
         x += pw + 4f;
         if (i < nodes.size() - 1) {
            this.canvas.icon("chevron_right", x, y + 5f, 12f, t.colors.onSurfaceVariant);
            x += 12f;
         }
      }
      String depth = Lang.s("Layer ", "层级 ") + (pm.getCurrentDepth() + 1) + "/3";
      this.canvas.text(depth, this.availX + this.colW - 18f
              - this.canvas.textWidth(depth, Md3Type.LABEL_MEDIUM), y + 7f,
              Md3Type.LABEL_MEDIUM, t.colors.onSurfaceVariant);
   }

   private void renderAvailable(Md3Theme t, int mouseX, int mouseY) {
      if (!this.compactHeader) {
         float sectionY = this.availListY - 24f;
         String section = Lang.s("Available Packs & Folders", "可用资源包");
         this.canvas.text(section, this.availX + 18f, sectionY,
                 Md3Type.TITLE_SMALL, t.colors.onSurface);
         String n = Integer.toString(this.availablePacks.size());
         this.canvas.text(n, this.availX + this.colW - 18f
                         - this.canvas.textWidth(n, Md3Type.LABEL_MEDIUM),
                 sectionY, Md3Type.LABEL_MEDIUM, t.colors.primary);
      }

      this.availableScroll.measure(this.availablePacks.size() * this.rowH, this.availListH);
      this.availableScroll.update(this.dt);
      this.canvas.pushClip(this.availX + 8f, this.availListY,
              this.colW - 20f, this.availListH);
      this.availRowYs = new float[this.availablePacks.size()];
      if (this.availablePacks.isEmpty()) {
         this.emptyState(t, this.availX + 8f, this.availListY,
                 this.colW - 20f, this.availListH,
                 Lang.s("No resource packs found", "未找到资源包"), "folder");
      } else {
         float base = this.availListY - this.availableScroll.value;
         for (int i = 0; i < this.availablePacks.size(); i++) {
            float y = base + i * this.rowH;
            this.availRowYs[i] = y;
            if (y + this.rowH < this.availListY
                    || y > this.availListY + this.availListH) {
               continue;
            }
            this.renderAvailableRow(t, this.availablePacks.get(i), y,
                    this.colW - 28f, mouseX, mouseY);
         }
      }
      if (this.dragActive && this.dragMode == 2) {
         float slotY = this.availListY - this.availableScroll.value + this.dragSlot * this.rowH;
         slotY = Math.max(this.availListY + 1f,
                 Math.min(this.availListY + this.availListH - 2f, slotY));
         this.canvas.fill(this.availX + 12f, slotY - 1f,
                 this.colW - 28f, 2f, t.colors.primary);
      }
      this.canvas.popClip();
      this.availableScroll.renderBar(this.canvas, t,
              this.availX + this.colW - 10f, this.availListY, 4f);
   }

   private void renderSelected(Md3Theme t, int mouseX, int mouseY) {
      String title = Lang.s("Loaded Resource Packs", "已加载资源包");
      this.canvas.text(title, this.rightX + 18f, this.cardY + 14f,
              Md3Type.TITLE_LARGE, t.colors.onSurface);
      float tw = this.canvas.textWidth(title, Md3Type.TITLE_LARGE);
      String n = Integer.toString(this.selectedPacks.size());
      float chipX = this.rightX + 18f + tw + 10f;
      this.canvas.roundRect(chipX, this.cardY + 14f,
              this.canvas.textWidth(n, Md3Type.LABEL_MEDIUM) + 16f, 22f, 11f,
              t.colors.primaryContainer);
      Md3Text.centered(this.canvas, n, chipX + 8f
              + this.canvas.textWidth(n, Md3Type.LABEL_MEDIUM) / 2f,
              this.cardY + 25f, Md3Type.LABEL_MEDIUM, t.colors.onPrimaryContainer);

      this.closeButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);

      this.selectedScroll.measure(this.selectedPacks.size() * this.rowH, this.selListH);
      this.selectedScroll.update(this.dt);
      this.canvas.pushClip(this.rightX + 8f, this.selListY,
              this.colW - 20f, this.selListH);
      this.selRowYs = new float[this.selectedPacks.size()];
      if (this.selectedPacks.isEmpty()) {
         this.emptyState(t, this.rightX + 8f, this.selListY,
                 this.colW - 20f, this.selListH,
                 Lang.s("Select packs from the left list", "从左侧列表选择资源包"),
                 "inventory_2");
      } else {
         float base = this.selListY - this.selectedScroll.value;
         for (int i = 0; i < this.selectedPacks.size(); i++) {
            float y = base + i * this.rowH;
            this.selRowYs[i] = y;
            if (y + this.rowH < this.selListY
                    || y > this.selListY + this.selListH) {
               continue;
            }
            this.renderSelectedRow(t, this.selectedPacks.get(this.selectedPacks.size() - 1 - i),
                    i, y, this.colW - 28f, mouseX, mouseY);
         }
      }
      if (this.dragActive && this.dragMode == 1) {
         float slotY = this.selListY - this.selectedScroll.value + this.dragSlot * this.rowH;
         slotY = Math.max(this.selListY + 1f,
                 Math.min(this.selListY + this.selListH - 2f, slotY));
         this.canvas.fill(this.rightX + 12f, slotY - 1f,
                 this.colW - 28f, 2f, t.colors.primary);
      }
      this.canvas.popClip();
      this.selectedScroll.renderBar(this.canvas, t,
              this.rightX + this.colW - 10f, this.selListY, 4f);
   }

   private void emptyState(Md3Theme t, float x, float y, float w, float h,
                           String label, String icon) {
      this.canvas.icon(icon, x + w / 2f - 16f, y + h / 2f - 30f, 32f,
              Md3Colors.alpha(t.colors.onSurfaceVariant, 0.55f));
      Md3Text.centered(this.canvas, label, x + w / 2f, y + h / 2f + 6f,
              Md3Type.BODY_MEDIUM, t.colors.onSurfaceVariant);
   }

   private void renderAvailableRow(Md3Theme t, PackEntry entry, float y, float w,
                                   int mouseX, int mouseY) {
      // Keep 4 px between the rounded hover card and the list's scissor edge;
      // previously the card started outside the clip, so its left cap was cut
      // off and the trailing + / arrow sat outside the highlight.
      float x = this.availX + 12f;
      boolean hot = ClientUi.hit(mouseX, mouseY, x, y, w, this.rowH);
      if (hot) {
         this.canvas.roundRect(x, y, w, this.rowH, 12f,
                 Md3Colors.alpha(t.colors.onSurface, 0.055f));
      }
      float iconSize = this.rowH >= 54 ? 38f : 32f;
      this.drawPackIcon(entry, x + 8f, y + (this.rowH - iconSize) / 2f, iconSize);

      float textX = x + (this.rowH >= 54 ? 54f : 48f);
      float textW = w - (textX - x) - 42f;
      String name;
      int nameColor;
      if (entry.isParentNav()) {
         name = Lang.s(".. (Up to Parent Folder)", ".. (返回上级文件夹)");
         nameColor = t.colors.tertiary;
      } else if (entry.isFolder()) {
         name = Lang.s("[Dir] ", "[文件夹] ") + entry.getName();
         nameColor = t.colors.tertiary;
      } else {
         name = entry.getName();
         nameColor = entry.isVersionCompatible() ? t.colors.onSurface : t.colors.error;
      }
      this.canvas.text(ClientUi.fit(this.canvas, name, Md3Type.BODY_LARGE, textW),
              textX, y + (this.rowH >= 54 ? 9f : 6f), Md3Type.BODY_LARGE, nameColor);
      String desc;
      if (entry.isParentNav()) {
         desc = Lang.s("Click to go up one folder level", "点击返回上一层文件夹");
      } else if (entry.isFolder()) {
         desc = entry.getDescription();
         if (desc == null || desc.isEmpty()) {
            desc = Lang.s("Folder", "文件夹");
         }
      } else {
         desc = entry.getDescription();
         if (desc == null || desc.isEmpty()) {
            desc = Lang.s("No description", "无描述");
         }
      }
      this.canvas.text(ClientUi.fit(this.canvas, desc, Md3Type.BODY_MEDIUM, textW),
              textX, y + (this.rowH >= 54 ? 30f : 23f), Md3Type.BODY_MEDIUM, t.colors.onSurfaceVariant);

      float buttonSize = this.rowH >= 54 ? 30f : 26f;
      float buttonX = x + w - buttonSize - 10f;
      float buttonY = y + (this.rowH - buttonSize) / 2f;
      String action = entry.isPack() ? "add" : "chevron_right";
      int actionColor = entry.isPack() ? t.colors.primary : t.colors.onSurfaceVariant;
      if (!entry.isParentNav() && !entry.isFolder()) {
         this.canvas.roundRect(buttonX, buttonY, buttonSize, buttonSize, buttonSize / 2f,
                 hot ? t.colors.primary : t.colors.surfaceContainerHighest);
         this.canvas.icon(action, buttonX + 3f, buttonY + 3f, buttonSize - 6f,
                 hot ? t.colors.onPrimary : actionColor);
      } else {
         this.canvas.icon(action, buttonX + 3f, buttonY + 3f, buttonSize - 6f, actionColor);
      }
   }

   private void renderSelectedRow(Md3Theme t, PackEntry entry, int displayIndex,
                                  float y, float w, int mouseX, int mouseY) {
      float x = this.rightX + 12f;
      boolean hot = ClientUi.hit(mouseX, mouseY, x, y, w, this.rowH);
      if (hot) {
         this.canvas.roundRect(x, y, w, this.rowH, 12f,
                 Md3Colors.alpha(t.colors.onSurface, 0.055f));
      }
      float iconSize = this.rowH >= 54 ? 38f : 32f;
      this.drawPackIcon(entry, x + 8f, y + (this.rowH - iconSize) / 2f, iconSize);

      float bs = this.rowH >= 54 ? 24f : 20f;
      float gap = 2f;
      float rx = x + w - bs - 4f;
      float bx = rx - gap - bs * 2f - gap;
      float by = y + (this.rowH - bs) / 2f;

      float textX = x + (this.rowH >= 54 ? 54f : 48f);
      float textW = w - (textX - x) - (bs * 3f + gap * 2f + 12f);
      this.canvas.text(ClientUi.fit(this.canvas, entry.getName(), Md3Type.BODY_LARGE, textW),
              textX, y + (this.rowH >= 54 ? 9f : 6f), Md3Type.BODY_LARGE, t.colors.onSurface);
      String desc = entry.getDescription();
      if (desc == null || desc.isEmpty()) {
         desc = entry.isFolder() ? Lang.s("Folder", "文件夹") : Lang.s("Resource pack", "资源包");
      }
      this.canvas.text(ClientUi.fit(this.canvas, desc, Md3Type.BODY_MEDIUM, textW),
              textX, y + (this.rowH >= 54 ? 30f : 23f), Md3Type.BODY_MEDIUM, t.colors.onSurfaceVariant);

      boolean canUp = displayIndex > 0;
      boolean canDown = displayIndex < this.selectedPacks.size() - 1;
      this.drawMiniIconButton(t, "keyboard_arrow_up", bx, by, bs, canUp,
              mouseX, mouseY);
      this.drawMiniIconButton(t, "expand_more", bx + bs + gap, by, bs, canDown,
              mouseX, mouseY);
      boolean removeHot = ClientUi.hit(mouseX, mouseY, rx, by, bs, bs);
      if (removeHot) {
         this.canvas.roundRect(rx, by, bs, bs, bs / 2f,
                 Md3Colors.alpha(t.colors.error, 0.10f));
      }
      this.canvas.icon("close", rx + bs / 2f - 9f, by + bs / 2f - 9f, 18f,
              removeHot ? t.colors.error : t.colors.onSurfaceVariant);
   }

   private void drawMiniIconButton(Md3Theme t, String icon, float x, float y, float size,
                                   boolean enabled, int mouseX, int mouseY) {
      boolean hot = enabled && ClientUi.hit(mouseX, mouseY, x, y, size, size);
      if (hot) {
         this.canvas.roundRect(x, y, size, size, size / 2f, t.colors.surfaceContainerHighest);
      }
      float glyph = size - 4f;
      this.canvas.icon(icon, x + 2f, y + 2f, glyph,
              enabled ? (hot ? t.colors.primary : t.colors.onSurfaceVariant)
                      : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.3f));
   }

   private void renderFooter(Md3Theme t, int mouseX, int mouseY) {
      this.btnDone.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      this.btnOpen.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      this.btnNew.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      this.btnRefresh.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.selectedPacks.size() > 1) {
         Md3Text.centered(this.canvas,
                 Lang.s("Top of the list has the highest priority",
                 "列表越靠上优先级越高"),
                 this.rightX + this.colW / 2f, this.footerY - 12f,
                 Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);
      }
   }

   private void drawPackIcon(PackEntry entry, float x, float y, float size) {
      Identifier loc = entry.getIconLocation();
      if (!entry.isIconLoaded() || !entry.isMetadataLoaded()) {
         AsyncIconLoader.getInstance().requestIconAndMeta(entry);
      }
      if (loc == null && this.client != null) {
         loc = AsyncIconLoader.getInstance().getDefaultPackTextureLoc();
      }
      if (loc != null) {
         try {
            this.client.getTextureManager().bindTexture(loc);
            org.lwjgl.opengl.GL11.glEnable(GL11.GL_TEXTURE_2D);
            int id = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            float tw = Math.max(1, entry.getIconTextureWidth());
            float th = Math.max(1, entry.getIconTextureHeight());
            float dw = size;
            float dh = size;
            if (tw > th) {
               dh = size * th / tw;
            } else {
               dw = size * tw / th;
            }
            this.canvas.image(id, x + (size - dw) / 2f, y + (size - dh) / 2f,
                    dw, dh, 0f, 0f, 1f, 1f, -1);
            return;
         } catch (Throwable ignored) {
            // fall through to the Material glyph
         }
      }
      this.canvas.roundRect(x, y, size, size, 10f,
              ClientTheme.get().colors.surfaceContainerHighest);
      String icon = entry.isFolder() || entry.isParentNav() ? "folder" : "image";
      this.canvas.icon(icon, x + 7f, y + 7f, size - 14f,
              ClientTheme.get().colors.onSurfaceVariant);
   }

   /* ------------------------------------------------------------------ */
   /* input                                                              */
   /* ------------------------------------------------------------------ */

   /* ------------------------------------------------------------------ */
   /* RowGlide-style drag and drop                                       */
   /* ------------------------------------------------------------------ */

   private boolean inAvailableList(float x, float y) {
      return ClientUi.hit(x, y, this.availX + 4f, this.availListY,
              this.colW - 8f, this.availListH);
   }

   private boolean inSelectedList(float x, float y) {
      return ClientUi.hit(x, y, this.rightX + 4f, this.selListY,
              this.colW - 8f, this.selListH);
   }

   private int slotAt(float y, float listY, float scroll, int count) {
      float local = y - listY + scroll;
      if (local < 0f) return 0;
      int slot = (int)(local / this.rowH);
      if (local - slot * this.rowH > this.rowH * 0.5f) {
         slot++;
      }
      return Math.max(0, Math.min(count, slot));
   }

   private void beginResourceDrag(boolean fromSelected, int displayIndex,
                                  PackEntry entry, float x, float y) {
      this.dragPending = true;
      this.dragActive = false;
      this.dragFromSelected = fromSelected;
      this.dragEntry = entry;
      this.dragSourceDisplay = displayIndex;
      this.dragStartX = x;
      this.dragStartY = y;
      this.dragMode = fromSelected ? 1 : 0;
      this.dragSlot = fromSelected ? displayIndex : 0;
      this.mouseDown = true;
   }

   private void clearResourceDrag() {
      this.dragPending = false;
      this.dragActive = false;
      this.dragEntry = null;
      this.dragMode = 0;
      this.dragSlot = 0;
   }

   private void updateResourceDragTarget() {
      if (!this.dragActive) {
         return;
      }
      if (this.inSelectedList(this.mouseNowX, this.mouseNowY)) {
         this.dragMode = 1;
         this.dragSlot = this.slotAt(this.mouseNowY, this.selListY,
                 this.selectedScroll.value, this.selectedPacks.size());
      } else if (this.inAvailableList(this.mouseNowX, this.mouseNowY)) {
         this.dragMode = 2;
         this.dragSlot = this.slotAt(this.mouseNowY, this.availListY,
                 this.availableScroll.value, this.availablePacks.size());
      } else {
         this.dragMode = 0;
      }
   }

   private void autoScrollDuringDrag() {
      if (!this.dragActive) {
         return;
      }
      if (this.inSelectedList(this.mouseNowX, this.mouseNowY)
              || this.dragMode == 1) {
         if (this.mouseNowY < this.selListY + 18f) {
            this.selectedScroll.scrollBy(-12f);
         } else if (this.mouseNowY > this.selListY + this.selListH - 18f) {
            this.selectedScroll.scrollBy(12f);
         }
      }
      if (this.inAvailableList(this.mouseNowX, this.mouseNowY)
              || this.dragMode == 2) {
         if (this.mouseNowY < this.availListY + 18f) {
            this.availableScroll.scrollBy(-12f);
         } else if (this.mouseNowY > this.availListY + this.availListH - 18f) {
            this.availableScroll.scrollBy(12f);
         }
      }
   }

   private void finishResourceDrag(int mouseX, int mouseY) {
      if (!this.dragPending) {
         return;
      }
      this.mouseNowX = mouseX;
      this.mouseNowY = mouseY;
      if (!this.dragActive) {
         // A plain click on the text area keeps the original add/select action.
         if (!this.dragFromSelected && this.dragEntry != null && this.dragEntry.isPack()) {
            this.selectPack(this.dragEntry);
            this.selectedScroll.jump(0f);
         }
         this.clearResourceDrag();
         return;
      }

      this.updateResourceDragTarget();
      if (this.dragMode == 1) {
         int slot = Math.max(0, Math.min(this.selectedPacks.size(), this.dragSlot));
         if (this.dragFromSelected) {
            int source = this.selectedPacks.size() - 1 - this.dragSourceDisplay;
            if (source >= 0 && source < this.selectedPacks.size()
                    && this.selectedPacks.get(source) == this.dragEntry) {
               this.selectedPacks.remove(source);
               int n = this.selectedPacks.size();
               int insert = Math.max(0, Math.min(n, n - slot));
               this.selectedPacks.add(insert, this.dragEntry);
               this.rebuildSelectedList();
               this.refreshAvailableEntries();
            }
         } else if (this.dragEntry != null && this.dragEntry.isPack()
                 && !this.isPackAlreadySelected(this.dragEntry)) {
            int n = this.selectedPacks.size();
            int insert = Math.max(0, Math.min(n, n - slot));
            this.selectedPacks.add(insert, this.dragEntry);
            this.rebuildSelectedList();
            this.refreshAvailableEntries();
            this.selectedScroll.jump(0f);
         }
      } else if (this.dragMode == 2 && this.dragFromSelected) {
         int source = this.selectedPacks.size() - 1 - this.dragSourceDisplay;
         if (source >= 0 && source < this.selectedPacks.size()
                 && this.selectedPacks.get(source) == this.dragEntry) {
            this.selectedPacks.remove(source);
            this.rebuildSelectedList();
            this.refreshAvailableEntries();
            this.selectedScroll.jump(0f);
         }
      }
      this.clearResourceDrag();
   }

   private void renderDragGhost(Md3Theme t) {
      if (!this.dragActive || this.dragEntry == null) {
         return;
      }
      String name = this.dragEntry.getName();
      float gw = Math.min(230f, this.width - 24f);
      float gx = Math.max(12f, Math.min(this.width - gw - 12f, this.mouseNowX + 14f));
      float gy = Math.max(12f, Math.min(this.height - 42f, this.mouseNowY - 16f));
      this.canvas.roundRect(gx, gy, gw, 30f, 15f, t.colors.surfaceContainerHighest);
      this.canvas.icon("drag_indicator", gx + 8f, gy + 7f, 16f, t.colors.primary);
      this.canvas.text(ClientUi.fit(this.canvas, name, Md3Type.BODY_MEDIUM, gw - 38f),
              gx + 30f, gy + 8f, Md3Type.BODY_MEDIUM, t.colors.onSurface);
   }

   @Override
   public void handleMouse() {
      int wheel = Mouse.getEventDWheel();
      if (wheel != 0) {
         if (ClientUi.hit(this.mouseNowX, this.mouseNowY, this.availX, this.availListY,
                 this.colW, this.availListH)) {
            this.availableScroll.scrollBy(-Integer.signum(wheel) * 44f);
         } else if (ClientUi.hit(this.mouseNowX, this.mouseNowY, this.rightX, this.selListY,
                 this.colW, this.selListH)) {
            this.selectedScroll.scrollBy(-Integer.signum(wheel) * 44f);
         }
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
      this.mouseNowX = mouseX;
      this.mouseNowY = mouseY;

      if (ClientUi.hit(mouseX, mouseY, this.search.x, this.search.y,
              this.search.w, this.search.h)) {
         if (!this.search.text.isEmpty()
                 && mouseX >= this.search.x + this.search.w - 36f) {
            this.search.clear();
            this.refreshAvailableEntries();
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

      if (ClientUi.hit(mouseX, mouseY, this.closeButton.x, this.closeButton.y,
              this.closeButton.w, this.closeButton.h)) {
         this.cancelAndClose();
         ClientUi.click();
         return;
      }
      if (ClientUi.hit(mouseX, mouseY, this.btnDone.x, this.btnDone.y,
              this.btnDone.w, this.btnDone.h)) {
         this.saveAndClose();
         ClientUi.click();
         return;
      }
      if (ClientUi.hit(mouseX, mouseY, this.btnOpen.x, this.btnOpen.y,
              this.btnOpen.w, this.btnOpen.h)) {
         PackManager.getInstance().openCurrentFolderInOS();
         ClientUi.click();
         return;
      }
      if (this.btnNew.enabled && ClientUi.hit(mouseX, mouseY, this.btnNew.x,
              this.btnNew.y, this.btnNew.w, this.btnNew.h)) {
         this.isOpeningSubDialog = true;
         this.client.setScreen(new GuiCreateFolderDialog(this));
         ClientUi.click();
         return;
      }
      if (ClientUi.hit(mouseX, mouseY, this.btnRefresh.x, this.btnRefresh.y,
              this.btnRefresh.w, this.btnRefresh.h)) {
         PackManager.getInstance().refresh();
         this.refreshAvailableEntries();
         this.awaitingIndexRefresh = true;
         ClientUi.click();
         return;
      }

      for (Crumb crumb : this.crumbs) {
         if (ClientUi.hit(mouseX, mouseY, crumb.x, crumb.y, crumb.w, crumb.h)) {
            PackManager.getInstance().navigateTo(crumb.dir);
            this.refreshAvailableEntries();
            this.availableScroll.jump(0f);
            ClientUi.click();
            return;
         }
      }

      if (this.availableScroll.press(mouseX, mouseY, this.availX + this.colW - 10f,
              this.availListY, 4f, 4f)) {
         return;
      }
      if (this.selectedScroll.press(mouseX, mouseY, this.rightX + this.colW - 10f,
              this.selListY, 4f, 4f)) {
         return;
      }

      for (int i = 0; i < this.availRowYs.length && i < this.availablePacks.size(); i++) {
         float y = this.availRowYs[i];
         if (y < this.availListY || y + this.rowH > this.availListY + this.availListH) {
            continue;
         }
         if (!ClientUi.hit(mouseX, mouseY, this.availX + 12f, y, this.colW - 28f, this.rowH)) {
            continue;
         }
         PackEntry entry = this.availablePacks.get(i);
         float rowX = this.availX + 12f;
         float rowW = this.colW - 28f;
         if (entry.isPack()
                 && mouseX >= rowX + 48f && mouseX <= rowX + rowW - 44f) {
            this.beginResourceDrag(false, i, entry, mouseX, mouseY);
            return;
         }
         if (entry.isParentNav()) {
            PackManager.getInstance().navigateUp();
            this.refreshAvailableEntries();
            this.availableScroll.jump(0f);
            ClientUi.click();
         } else if (entry.isFolder()) {
            PackManager.getInstance().navigateTo(entry.getFile());
            this.refreshAvailableEntries();
            this.availableScroll.jump(0f);
            ClientUi.click();
         } else if (entry.isPack()) {
            this.selectPack(entry);
            this.selectedScroll.jump(0f);
            ClientUi.click();
         }
         return;
      }

      for (int i = 0; i < this.selRowYs.length && i < this.selectedPacks.size(); i++) {
         float y = this.selRowYs[i];
         if (y < this.selListY || y + this.rowH > this.selListY + this.selListH) {
            continue;
         }
         PackEntry entry = this.selectedPacks.get(this.selectedPacks.size() - 1 - i);
         float x = this.rightX + 12f;
         float rowW = this.colW - 28f;
         float bs = this.rowH >= 54 ? 24f : 20f;
         float gap = 2f;
         float rx = x + rowW - bs - 4f;
         float bx = rx - gap - bs * 2f - gap;
         float by = y + (this.rowH - bs) / 2f;
         if (ClientUi.hit(mouseX, mouseY, rx, by, bs, bs)) {
            this.unselectPack(entry);
            ClientUi.click();
            return;
         }
         if (ClientUi.hit(mouseX, mouseY, bx, by, bs, bs) && i > 0) {
            this.movePackUp(i);
            ClientUi.click();
            return;
         }
         if (ClientUi.hit(mouseX, mouseY, bx + bs + gap, by, bs, bs)
                 && i < this.selectedPacks.size() - 1) {
            this.movePackDown(i);
            ClientUi.click();
            return;
         }
         if (mouseX >= x + 48f && mouseX <= bx - 4f) {
            this.beginResourceDrag(true, i, entry, mouseX, mouseY);
            return;
         }
      }
   }

   @Override
   protected void mouseReleased(int mouseX, int mouseY, int button) {
      if (button == 0) {
         this.finishResourceDrag(mouseX, mouseY);
      }
      this.mouseDown = false;
      this.availableScroll.release();
      this.selectedScroll.release();
   }

   @Override
   protected void mouseDragged(int mouseX, int mouseY, int button, long lastClick) {
      this.mouseNowX = mouseX;
      this.mouseNowY = mouseY;
      if (this.availableScroll.dragging) {
         this.availableScroll.drag(mouseY, this.availListY);
         return;
      }
      if (this.selectedScroll.dragging) {
         this.selectedScroll.drag(mouseY, this.selListY);
         return;
      }
      if (this.dragPending) {
         if (!this.dragActive) {
            float dx = mouseX - this.dragStartX;
            float dy = mouseY - this.dragStartY;
            if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
               this.dragActive = true;
            }
         }
         if (this.dragActive) {
            this.updateResourceDragTarget();
            this.autoScrollDuringDrag();
         }
      }
   }

   @Override
   protected void keyPressed(char id, int code) {
      if (this.searchFocus) {
         if (ImeBridge.handleKey(id, code)) {
            return;
         }
         if (code == 1) {
            this.searchFocus = false;
            this.search.focused = false;
            this.cancelAndClose();
            return;
         }
         if (code == 28 || code == 156) {
            this.searchFocus = false;
            this.search.focused = false;
            return;
         }
         if (code == 14) {
            this.search.backspace();
            this.refreshAvailableEntries();
            return;
         }
         if (id >= 32 && id != 127) {
            this.search.typeChar(id);
            this.refreshAvailableEntries();
         }
         return;
      }
      if (code == 1) {
         this.cancelAndClose();
      }
   }

   private static final class Crumb {
      float x;
      float y;
      float w;
      float h;
      File dir;
   }
}
