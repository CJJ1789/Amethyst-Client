package net.client;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Button;
import net.client.ui.md3.widget.Md3IconButton;
import net.client.ui.md3.widget.Md3ListItem;
import net.client.ui.md3.widget.Md3Surfaces;
import net.client.ui.md3.widget.Md3Text;
import java.util.List;
import net.client.ui.ClientScroll;
import net.client.ui.ClientTheme;
import net.client.ui.ClientUi;
import net.client.util.Accounts;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Account manager rebuilt as a Material 3 dialog with a scrollable list. */
public class AccountScreen extends Screen {
   

   private final Screen parent;
   private final Md3Canvas canvas = new Md3Canvas();
   private final ClientScroll scroll = new ClientScroll();
   private final Md3IconButton closeButton = new Md3IconButton();
   private final Md3Button[] addButtons = new Md3Button[4];
   private final String[] addTypes = new String[]{"offline", "microsoft", "skin", "token"};

   private float cardX;
   private float cardY;
   private float cardW;
   private float cardH;
   private float listX;
   private float listY;
   private float listW;
   private float listH;
   private float[] rowYs = new float[0];

   private String toast;
   private long toastUntil;
   private int pendingDelete = -1;
   private long lastNanos;
   private float dt;
   private boolean mouseDown;
   private float mouseNowX;
   private float mouseNowY;
   private boolean stackedAddButtons;
   private boolean compactVertical;
   private float rowH = 56f;

   public AccountScreen(Screen parent) {
      this.parent = parent;
   }

   @Override
   public void init() {
      Keyboard.enableRepeatEvents(true);
      this.buttons.clear();
      this.cardH = Math.min(430f, this.height - 24f);
      // At 1920x1080 with GUI scale 4 the logical canvas is only 480x270.
      // Prioritise the standard 1080 layout: keep the card around 1.5:1
      // instead of shrinking it to a narrow strip, and compact the vertical
      // spacing so two full account rows remain visible.
      this.compactVertical = this.cardH < 300f;
      this.cardW = Math.min(440f, this.width - 24f);
      this.cardW = Math.min(this.cardW, Math.max(340f, this.cardH * 1.55f));
      this.cardX = (this.width - this.cardW) / 2f;
      this.cardY = (this.height - this.cardH) / 2f;
      this.listX = this.cardX + 18f;
      this.listY = this.cardY + (this.compactVertical ? 56f : 64f);
      this.listW = this.cardW - 36f;
      this.rowH = this.compactVertical ? 48f : 56f;
      this.stackedAddButtons = this.cardW < 400f;

      String[] labels = new String[]{
         Lang.tr("acc.offline"), Lang.tr("acc.ms"), Lang.tr("acc.skin"), Lang.tr("acc.token")
      };
      String[] icons = new String[]{"person", "account_circle", "face", "vpn_key"};
      if (this.stackedAddButtons) {
         float bh = this.compactVertical ? 32f : 36f;
         float gap = this.compactVertical ? 6f : 8f;
         float bottom = this.compactVertical ? 6f : 12f;
         float bw = (this.listW - gap) / 2f;
         float by = this.cardY + this.cardH - bottom - bh * 2f - gap;
         this.listH = Math.max(54f, by - (this.compactVertical ? 6f : 10f) - this.listY);
         for (int i = 0; i < 4; i++) {
            this.addButtons[i] = new Md3Button(Md3Button.Variant.OUTLINED, labels[i]);
            this.addButtons[i].icon = icons[i];
            this.addButtons[i].setBounds(this.listX + (i % 2) * (bw + gap),
                    by + (i / 2) * (bh + gap), bw, bh);
         }
      } else {
         this.listH = this.cardH - 142f;
         float bw = (this.listW - 24f) / 4f;
         float by = this.cardY + this.cardH - 60f;
         for (int i = 0; i < 4; i++) {
            this.addButtons[i] = new Md3Button(Md3Button.Variant.OUTLINED, labels[i]);
            this.addButtons[i].icon = icons[i];
            this.addButtons[i].setBounds(this.listX + i * (bw + 8f), by, bw, 40f);
         }
      }

      this.closeButton.variant = Md3IconButton.Variant.STANDARD;
      this.closeButton.icon = "close";
      this.closeButton.setBounds(this.cardX + this.cardW - 50f, this.cardY + 12f, 38f, 38f);
      this.scroll.jump(0f);
   }

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
      ClientUi.backdrop(this.canvas, t, this.width, this.height,
              this.client != null && this.client.world != null);
      if (this.pendingDelete >= 0) {
         this.canvas.fill(0f, 0f, this.width, this.height,
                 Md3Colors.alpha(t.colors.scrim, 0.58f));
      }

      this.canvas.shadow(this.cardX, this.cardY, this.cardW, this.cardH, 28f, 4);
      this.canvas.roundRect(this.cardX, this.cardY, this.cardW, this.cardH, 28f,
              t.colors.surfaceContainerHigh);

      this.canvas.text(Lang.tr("acc.title"), this.cardX + 24f, this.cardY + 18f,
              Md3Type.TITLE_LARGE, t.colors.onSurface);
      List<Accounts.Account> list = Accounts.list();
      int totalRows = list.size() + (Accounts.launcher() != null ? 1 : 0);
      String count = Integer.toString(totalRows);
      float tw = this.canvas.textWidth(Lang.tr("acc.title"), Md3Type.TITLE_LARGE);
      this.canvas.roundRect(this.cardX + 24f + tw + 10f, this.cardY + 18f, 26f, 22f, 11f,
              t.colors.primaryContainer);
      Md3Text.centered(this.canvas, count, this.cardX + 24f + tw + 23f, this.cardY + 29f,
              Md3Type.LABEL_MEDIUM, t.colors.onPrimaryContainer);
      this.canvas.text(Lang.tr("acc.hint"), this.cardX + 24f, this.cardY + 42f,
              Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);

      this.closeButton.surface = t.colors.surfaceContainerHigh;
      this.closeButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.closeButton.consumeClick()) {
         this.back();
      }

      this.canvas.roundRect(this.listX, this.listY, this.listW, this.listH, 16f,
              t.colors.surfaceContainerLow);
      this.renderList(t, list, mouseX, mouseY);

      for (Md3Button button : this.addButtons) {
         button.surface = t.colors.surfaceContainerHigh;
         button.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      }

      this.renderToast(t);
      if (this.pendingDelete >= 0 && this.pendingDelete < list.size()) {
         this.renderConfirm(t, list.get(this.pendingDelete), mouseX, mouseY);
      }

      this.canvas.end();
   }

   private void renderList(Md3Theme t, List<Accounts.Account> list, int mouseX, int mouseY) {
      float innerH = this.listH - 12f;
      Accounts.Account launcher = Accounts.launcher();
      int offset = launcher != null ? 1 : 0;
      int total = list.size() + offset;
      this.scroll.measure(total * this.rowH, innerH);
      this.scroll.update(this.dt);
      this.canvas.pushClip(this.listX + 4f, this.listY + 6f, this.listW - 8f, innerH);
      this.rowYs = new float[total];

      if (total == 0) {
         this.canvas.icon("person_add", this.listX + this.listW / 2f - 16f,
                 this.listY + this.listH / 2f - 34f, 32f,
                 Md3Colors.alpha(t.colors.onSurfaceVariant, 0.55f));
         Md3Text.centered(this.canvas, tr("还没有账号", "No accounts yet"),
                 this.listX + this.listW / 2f, this.listY + this.listH / 2f + 2f,
                 Md3Type.TITLE_SMALL, t.colors.onSurfaceVariant);
         Md3Text.centered(this.canvas, tr("从下面添加一个", "Add one below"),
                 this.listX + this.listW / 2f, this.listY + this.listH / 2f + 24f,
                 Md3Type.BODY_MEDIUM, Md3Colors.alpha(t.colors.onSurfaceVariant, 0.7f));
         this.canvas.popClip();
         return;
      }

      int current = Accounts.currentIndex();
      float base = this.listY + 6f - this.scroll.value;
      for (int row = 0; row < total; row++) {
         float y = base + row * this.rowH;
         this.rowYs[row] = y;
         if (y + this.rowH < this.listY || y > this.listY + this.listH) {
            continue;
         }
         Md3ListItem item = new Md3ListItem();
         if (launcher != null && row == 0) {
            item.headline = launcher.name;
            item.supporting = this.typeLabel(launcher.type)
                    + (Accounts.usingLauncher() ? " · " + Lang.tr("acc.current") : "");
            item.leadingText = launcher.name;
            item.trailingIcon = null;
            item.selected = Accounts.usingLauncher();
         } else {
            int index = row - offset;
            Accounts.Account account = list.get(index);
            item.headline = account.name;
            item.supporting = this.typeLabel(account.type)
                    + (!Accounts.usingLauncher() && index == current
                    ? " · " + Lang.tr("acc.current") : "");
            item.leadingText = account.name;
            item.trailingIcon = "delete";
            item.selected = !Accounts.usingLauncher() && index == current;
         }
         item.setBounds(this.listX + 6f, y, this.listW - 12f, this.rowH);
         item.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
         if (row < total - 1) {
            this.canvas.fill(this.listX + 30f, y + this.rowH - 1f,
                    this.listW - 60f, 1f, t.colors.outlineVariant);
         }
      }
      this.canvas.popClip();
      this.scroll.renderBar(this.canvas, t, this.listX + this.listW - 8f,
              this.listY + 8f, 4f);
   }

   private void renderConfirm(Md3Theme t, Accounts.Account account, int mouseX, int mouseY) {
      float w = Math.min(340f, this.width - 24f);
      float h = 180f;
      float x = (this.width - w) / 2f;
      float y = (this.height - h) / 2f;
      Md3Surfaces.dialog(this.canvas, t, x, y, w, h);
      this.canvas.text(Lang.tr("acc.del.title"), x + 24f, y + 20f,
              Md3Type.TITLE_MEDIUM, t.colors.onSurface);
      this.canvas.text(account.name, x + 24f, y + 46f, Md3Type.BODY_LARGE, t.colors.primary);
      this.canvas.text(tr("此操作不可撤销", "This cannot be undone"),
              x + 24f, y + 68f, Md3Type.BODY_MEDIUM, t.colors.onSurfaceVariant);

      float bw = 86f;
      float bh = 38f;
      float by = y + h - 20f - bh;
      float delX = x + w - 24f - bw;
      float cancelX = delX - 8f - bw;
      boolean delHot = ClientUi.hit(mouseX, mouseY, delX, by, bw, bh);
      this.canvas.roundRect(delX, by, bw, bh, bh / 2f,
              delHot ? Md3Colors.composite(Md3Colors.alpha(t.colors.onError, 0.12f),
                      t.colors.error) : t.colors.error);
      Md3Text.centered(this.canvas, Lang.tr("acc.del.ok"), delX + bw / 2f, by + bh / 2f,
              Md3Type.LABEL_LARGE, t.colors.onError);

      boolean cancelHot = ClientUi.hit(mouseX, mouseY, cancelX, by, bw, bh);
      this.canvas.roundRect(cancelX, by, bw, bh, bh / 2f,
              cancelHot ? t.colors.surfaceContainerHighest : t.colors.surfaceContainerLow);
      Md3Text.centered(this.canvas, Lang.tr("acc.cancel"), cancelX + bw / 2f,
              by + bh / 2f, Md3Type.LABEL_LARGE, t.colors.onSurface);
   }

   private void renderToast(Md3Theme t) {
      if (this.toast == null || System.currentTimeMillis() >= this.toastUntil) {
         return;
      }
      float w = Math.min(this.cardW - 40f,
              this.canvas.textWidth(this.toast, Md3Type.BODY_MEDIUM) + 44f);
      float x = this.cardX + (this.cardW - w) / 2f;
      float y = this.cardY + this.cardH - (this.stackedAddButtons ? 112f : 48f);
      Md3Surfaces.snackbar(this.canvas, t, x, y, w, 38f);
      Md3Text.centered(this.canvas, ClientUi.fit(this.canvas, this.toast, Md3Type.BODY_MEDIUM, w - 28f),
              x + w / 2f, y + 19f, Md3Type.BODY_MEDIUM, t.colors.inverseOnSurface);
   }

   private String typeLabel(String type) {
      if ("offline".equals(type)) {
         return Lang.tr("acc.offline");
      }
      if ("token".equals(type)) {
         return Lang.tr("acc.token");
      }
      if ("launcher".equals(type)) {
         return tr("启动器账号", "Launcher account");
      }
      return "skin".equals(type) ? Lang.tr("acc.skin") : Lang.tr("acc.ms");
   }

   @Override
   public void handleMouse() {
      int wheel = Mouse.getEventDWheel();
      if (wheel != 0 && this.pendingDelete < 0) {
         this.scroll.scrollBy(-Integer.signum(wheel) * 48f);
      }
      super.handleMouse();
   }

   @Override
   protected void mouseClicked(int mouseX, int mouseY, int button) {
      this.mouseNowX = mouseX;
      this.mouseNowY = mouseY;
      if (button == 0) {
         this.mouseDown = true;
      }
      List<Accounts.Account> list = Accounts.list();

      if (this.pendingDelete >= 0) {
         if (this.pendingDelete < list.size()) {
            float w = Math.min(340f, this.width - 24f);
            float h = 180f;
            float x = (this.width - w) / 2f;
            float y = (this.height - h) / 2f;
            float bw = 86f;
            float bh = 38f;
            float by = y + h - 20f - bh;
            float delX = x + w - 24f - bw;
            float cancelX = delX - 8f - bw;
            if (button == 0 && ClientUi.hit(mouseX, mouseY, delX, by, bw, bh)) {
               Accounts.remove(this.pendingDelete);
               this.pendingDelete = -1;
               this.scroll.jump(0f);
               this.toast = tr("账号已删除", "Account removed");
               this.toastUntil = System.currentTimeMillis() + 2200L;
               ClientUi.click();
               return;
            }
            if (button == 0 && ClientUi.hit(mouseX, mouseY, cancelX, by, bw, bh)) {
               this.pendingDelete = -1;
               ClientUi.click();
               return;
            }
         } else {
            this.pendingDelete = -1;
         }
         return;
      }

      if (button == 0 && ClientUi.hit(mouseX, mouseY, this.closeButton.x, this.closeButton.y,
              this.closeButton.w, this.closeButton.h)) {
         return;
      }

      if (button == 0) {
         for (int i = 0; i < this.addButtons.length; i++) {
            Md3Button b = this.addButtons[i];
            if (ClientUi.hit(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
               ClientUi.click();
               this.client.setScreen(new AccountLoginScreen(this, this.addTypes[i]));
               return;
            }
         }
      }

      Accounts.Account launcher = Accounts.launcher();
      int offset = launcher != null ? 1 : 0;
      int total = list.size() + offset;
      for (int row = 0; row < this.rowYs.length && row < total; row++) {
         float y = this.rowYs[row];
         if (y < this.listY || y + this.rowH > this.listY + this.listH) {
            continue;
         }
         if (!ClientUi.hit(mouseX, mouseY, this.listX + 6f, y, this.listW - 12f, this.rowH)) {
            continue;
         }
         if (launcher != null && row == 0) {
            if (button == 0) {
               Accounts.selectLauncher();
               this.toast = tr("已切换到启动器账号", "Switched to launcher account");
               this.toastUntil = System.currentTimeMillis() + 2200L;
               ClientUi.click();
               return;
            }
            continue;
         }
         int index = row - offset;
         if (ClientUi.hit(mouseX, mouseY, this.listX + this.listW - 56f, y + 10f, 36f, 36f)) {
            this.pendingDelete = index;
            ClientUi.click();
            return;
         }
         if (button == 1) {
            this.pendingDelete = index;
            ClientUi.click();
            return;
         }
         if (button == 0) {
            Accounts.select(index);
            this.toast = Lang.tr("acc.applied");
            this.toastUntil = System.currentTimeMillis() + 2200L;
            ClientUi.click();
            return;
         }
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
         if (this.pendingDelete >= 0) {
            this.pendingDelete = -1;
         } else {
            this.back();
         }
      }
   }

   private void back() {
      this.client.setScreen(this.parent != null ? this.parent : new TitleScreen());
   }

   private String tr(String zh, String en) {
      return ClientConfig.t(zh, en);
   }

   @Override
   public boolean shouldPauseGame() {
      return false;
   }
}
