// Upstream: FastPacks / McPacks 1.0 (Forge 1.8.9), MIT
//   https://modrinth.com/mod/fastpacks (author: McPacks Team; Modrinth owner: Dulandor)
// Ported and modified for Legacy Fabric by this project; upstream MIT notice retained
// (see licenses/FASTPACKS-MIT.txt). The port was never released standalone.

package com.mcpacks.gui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Surfaces;
import net.client.ui.md3.widget.Md3Text;
import net.client.ui.md3.widget.Md3TextField;
import com.mcpacks.core.PackManager;
import com.mcpacks.util.FolderUtils;
import com.mcpacks.util.Lang;
import net.client.ime.ImeBridge;
import net.client.ui.ClientTheme;
import net.client.ui.ClientUi;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;

/** Create-folder dialog rebuilt as an MD3 modal with a filled text field. */
public class GuiCreateFolderDialog extends Screen {
   private final Screen parentScreen;
   private final Md3Canvas canvas = new Md3Canvas();
   private final Md3TextField nameField = new Md3TextField();
   private String errorMessage = "";

   private float cardX;
   private float cardY;
   private float cardW;
   private float cardH;
   private boolean mouseDown;
   private long lastNanos;
   private float dt;

   public GuiCreateFolderDialog(Screen parentScreen) {
      this.parentScreen = parentScreen;
   }

   @Override
   public void init() {
      Keyboard.enableRepeatEvents(true);
      this.buttons.clear();
      this.cardW = Math.min(400f, this.width - 24f);
      this.cardH = 236f;
      this.cardX = (this.width - this.cardW) / 2f;
      this.cardY = (this.height - this.cardH) / 2f;

      this.nameField.variant = Md3TextField.Variant.FILLED;
      this.nameField.label = Lang.s("Folder name", "文件夹名");
      this.nameField.supporting = "";
      this.nameField.maxLength = 32;
      this.nameField.focused = true;
      this.nameField.setBounds(this.cardX + 24f, this.cardY + 82f, this.cardW - 48f, 52f);
   }

   @Override
   public void render(int mouseX, int mouseY, float tickDelta) {
      long now = System.nanoTime();
      this.dt = this.lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - this.lastNanos) / 1.0E9f);
      this.lastNanos = now;
      super.render(mouseX, mouseY, tickDelta);

      Md3Theme t = ClientTheme.get();
      this.canvas.begin(this.width, this.height);
      ClientUi.backdrop(this.canvas, t, this.width, this.height,
              this.client != null && this.client.world != null);
      this.canvas.fill(0f, 0f, this.width, this.height,
              Md3Colors.alpha(t.colors.scrim, 0.58f));
      Md3Surfaces.dialog(this.canvas, t, this.cardX, this.cardY, this.cardW, this.cardH);

      this.canvas.text(Lang.s("Create New Resource Pack Folder", "新建资源包文件夹"),
              this.cardX + 24f, this.cardY + 20f, Md3Type.TITLE_MEDIUM, t.colors.onSurface);
      PackManager pm = PackManager.getInstance();
      int currentDepth = pm.getCurrentDepth();
      String locInfo = Lang.isChinese()
              ? "创建位置：" + (pm.isAtRoot() ? "根目录" : pm.getCurrentDir().getName())
              + " (第 " + (currentDepth + 1) + "/3 层)"
              : "Creating inside: " + (pm.isAtRoot() ? "Root" : pm.getCurrentDir().getName())
              + " (Layer " + (currentDepth + 1) + " of 3)";
      this.canvas.text(ClientUi.fit(this.canvas, locInfo, Md3Type.BODY_MEDIUM, this.cardW - 48f),
              this.cardX + 24f, this.cardY + 46f, Md3Type.BODY_MEDIUM,
              t.colors.onSurfaceVariant);

      this.nameField.surface = t.colors.surfaceContainerHigh;
      this.nameField.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      ImeBridge.onFieldRender((int) this.nameField.x + 16,
              (int) this.nameField.y + 28, (int) this.nameField.w - 32,
              this.nameField.text, this.nameField.text.length(), true);

      if (!this.errorMessage.isEmpty()) {
         this.canvas.icon("warning", this.cardX + 24f, this.cardY + 146f, 16f,
                 t.colors.error);
         this.canvas.text(ClientUi.fit(this.canvas, this.errorMessage, Md3Type.BODY_MEDIUM,
                 this.cardW - 68f), this.cardX + 46f, this.cardY + 145f,
                 Md3Type.BODY_MEDIUM, t.colors.error);
      }

      float bw = 92f;
      float bh = 40f;
      float by = this.cardY + this.cardH - 20f - bh;
      float createX = this.cardX + this.cardW - 24f - bw;
      float cancelX = createX - 8f - bw;

      boolean createHot = ClientUi.hit(mouseX, mouseY, createX, by, bw, bh);
      this.canvas.roundRect(createX, by, bw, bh, bh / 2f,
              createHot ? Md3Colors.composite(Md3Colors.alpha(t.colors.onPrimary, 0.12f),
                      t.colors.primary) : t.colors.primary);
      Md3Text.centered(this.canvas, Lang.s("Create", "创建"), createX + bw / 2f,
              by + bh / 2f, Md3Type.LABEL_LARGE, t.colors.onPrimary);

      boolean cancelHot = ClientUi.hit(mouseX, mouseY, cancelX, by, bw, bh);
      this.canvas.roundRect(cancelX, by, bw, bh, bh / 2f,
              cancelHot ? t.colors.surfaceContainerHighest : t.colors.surfaceContainerLow);
      Md3Text.centered(this.canvas, Lang.s("Cancel", "取消"), cancelX + bw / 2f,
              by + bh / 2f, Md3Type.LABEL_LARGE, t.colors.onSurface);

      this.canvas.end();
   }

   @Override
   protected void mouseClicked(int mouseX, int mouseY, int button) {
      if (button == 0) {
         this.mouseDown = true;
      }
      if (button != 0) {
         return;
      }
      float bw = 92f;
      float bh = 40f;
      float by = this.cardY + this.cardH - 20f - bh;
      float createX = this.cardX + this.cardW - 24f - bw;
      float cancelX = createX - 8f - bw;
      if (ClientUi.hit(mouseX, mouseY, createX, by, bw, bh)) {
         this.handleCreate();
         return;
      }
      if (ClientUi.hit(mouseX, mouseY, cancelX, by, bw, bh)) {
         this.client.setScreen(this.parentScreen);
      }
   }

   @Override
   protected void mouseReleased(int mouseX, int mouseY, int button) {
      this.mouseDown = false;
   }

   private void handleCreate() {
      String text = this.nameField.text.trim();
      if (text.isEmpty()) {
         this.errorMessage = Lang.s("Folder name cannot be empty!", "文件夹名不能为空！");
         return;
      }
      PackManager pm = PackManager.getInstance();
      if (!FolderUtils.canCreateSubfolder(pm.getRootDir(), pm.getCurrentDir())) {
         this.errorMessage = Lang.s("Maximum folder depth (3 layers) reached!",
                 "已达到最大嵌套层数 (3 层)！");
         return;
      }
      if (pm.createNewFolder(text)) {
         this.client.setScreen(this.parentScreen);
      } else {
         this.errorMessage = Lang.s("Failed to create folder (invalid name or already exists).",
                 "创建失败：名称非法或已存在。");
      }
   }

   @Override
   protected void keyPressed(char id, int code) {
      if (code == 28 || code == 156) {
         this.handleCreate();
      } else if (code == 1) {
         this.client.setScreen(this.parentScreen);
      } else if (ImeBridge.handleKey(id, code)) {
         // consumed by the IME
      } else if (code == 14) {
         this.nameField.backspace();
      } else if (id >= 32 && id != 127) {
         this.nameField.typeChar(id);
      }
   }

   @Override
   public void removed() {
      Keyboard.enableRepeatEvents(false);
      super.removed();
   }

   @Override
   public boolean shouldPauseGame() {
      return false;
   }
}
