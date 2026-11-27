package net.client;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3IconButton;
import net.client.ui.md3.widget.Md3Progress;
import net.client.ui.md3.widget.Md3Text;
import net.client.ui.md3.widget.Md3TextField;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import net.client.ime.ImeBridge;
import net.client.ui.ClientTheme;
import net.client.ui.ClientUi;
import net.client.util.Accounts;
import net.client.util.Auth;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;

/** Login dialog (offline / Microsoft device flow / skin site / token) on MD3. */
public class AccountLoginScreen extends Screen {
   private final Screen parent;
   private final String type;
   private final List<Field> fields = new ArrayList<Field>();
   private final Md3Canvas canvas = new Md3Canvas();
   private final Md3IconButton closeButton = new Md3IconButton();

   private int focus;
   private float cardX;
   private float cardY;
   private float cardW;
   private float cardH;
   private float fieldH = 52f;
   private float fieldGap = 8f;
   private String status = "";
   private boolean busy;
   private volatile String deviceUserCode;
   private volatile String deviceUri;
   private volatile Accounts.Account deviceAccount;
   private volatile String deviceError;

   private long lastNanos;
   private float dt;
   private boolean mouseDown;
   private float time;

   public AccountLoginScreen(Screen parent, String type) {
      this.parent = parent;
      this.type = type;
   }

   private String title() {
      if ("microsoft".equals(this.type)) {
         return Lang.tr("acc.ms");
      }
      if ("skin".equals(this.type)) {
         return Lang.tr("acc.skin");
      }
      return "token".equals(this.type) ? Lang.tr("acc.token") : Lang.tr("acc.offline");
   }

   private String icon() {
      if ("microsoft".equals(this.type)) {
         return "account_circle";
      }
      if ("skin".equals(this.type)) {
         return "language";
      }
      return "token".equals(this.type) ? "vpn_key" : "person_add";
   }

   @Override
   public void init() {
      this.buttons.clear();
      this.fields.clear();
      if ("offline".equals(this.type)) {
         this.addField(Lang.tr("acc.f.name"), false, "");
      } else if ("token".equals(this.type)) {
         this.addField(Lang.tr("acc.f.token"), true, "");
      } else if ("microsoft".equals(this.type)) {
         this.addField(Lang.tr("acc.f.client"), false,
                 ClientConfig.msClientId == null ? "" : ClientConfig.msClientId.trim());
      } else if ("skin".equals(this.type)) {
         this.addField(Lang.tr("acc.f.site"), false, "");
         this.addField(Lang.tr("acc.f.email"), false, "");
         this.addField(Lang.tr("acc.f.password"), true, "");
      }

      this.cardW = Math.min(440f, this.width - 24f);
      float infoH = "microsoft".equals(this.type) ? 52f : 34f;
      float desired = 64f + this.fields.size() * (this.fieldH + this.fieldGap)
              + infoH + 76f;
      this.cardH = Math.min(desired, this.height - 16f);
      if (this.cardH < desired && !this.fields.isEmpty()) {
         float usable = this.cardH - 64f - infoH - 76f - this.fieldGap * this.fields.size();
         this.fieldH = Math.max(44f, usable / this.fields.size());
      }
      this.cardX = (this.width - this.cardW) / 2f;
      this.cardY = (this.height - this.cardH) / 2f;

      float fx = this.cardX + 24f;
      float fw = this.cardW - 48f;
      float fy = this.cardY + 64f;
      for (Field field : this.fields) {
         field.widget.setBounds(fx, fy, fw, this.fieldH);
         field.widget.surface = ClientTheme.get().colors.surfaceContainerHigh;
         fy += this.fieldH + this.fieldGap;
      }

      this.closeButton.variant = Md3IconButton.Variant.STANDARD;
      this.closeButton.icon = "close";
      this.closeButton.setBounds(this.cardX + this.cardW - 50f, this.cardY + 12f, 38f, 38f);

      this.focus = 0;
      if ("microsoft".equals(this.type) && this.deviceUserCode == null && this.deviceError == null) {
         this.startDeviceFlow();
      }
   }

   private void addField(String label, boolean secret, String value) {
      Md3TextField widget = new Md3TextField();
      widget.variant = Md3TextField.Variant.FILLED;
      widget.label = label;
      widget.supporting = "";
      widget.masked = secret;
      widget.maxLength = 128;
      widget.text = value;
      this.fields.add(new Field(widget));
   }

   private String typedClientId() {
      return "microsoft".equals(this.type) ? this.value(0) : "";
   }

   private void startDeviceFlow() {
      this.busy = true;
      this.status = Lang.tr("acc.ms.waiting");
      Thread thread = new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               Auth.DeviceCode code = Auth.requestDeviceCode(AccountLoginScreen.this.typedClientId());
               AccountLoginScreen.this.deviceUri = code.verificationUri;
               AccountLoginScreen.this.deviceUserCode = code.userCode;
               AccountLoginScreen.this.deviceAccount = Auth.awaitMicrosoft(code,
                       AccountLoginScreen.this.typedClientId());
            } catch (Exception e) {
               AccountLoginScreen.this.deviceError = e.getMessage() == null
                       ? e.getClass().getSimpleName() : e.getMessage();
            }
         }
      }, "Client-AccountLogin");
      thread.setDaemon(true);
      thread.start();
   }

   @Override
   public void render(int mouseX, int mouseY, float tickDelta) {
      long now = System.nanoTime();
      this.dt = this.lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - this.lastNanos) / 1.0E9f);
      this.lastNanos = now;
      this.time += this.dt;

      super.render(mouseX, mouseY, tickDelta);
      Md3Theme t = ClientTheme.get();
      this.canvas.begin(this.width, this.height);
      ClientUi.backdrop(this.canvas, t, this.width, this.height,
              this.client != null && this.client.world != null);

      this.canvas.shadow(this.cardX, this.cardY, this.cardW, this.cardH, 28f, 4);
      this.canvas.roundRect(this.cardX, this.cardY, this.cardW, this.cardH, 28f,
              t.colors.surfaceContainerHigh);

      float iconX = this.cardX + 24f;
      float iconY = this.cardY + 18f;
      this.canvas.roundRect(iconX, iconY, 36f, 36f, 12f, t.colors.primaryContainer);
      this.canvas.icon(this.icon(), iconX + 8f, iconY + 8f, 20f,
              t.colors.onPrimaryContainer);
      this.canvas.text(this.title(), iconX + 46f, iconY + 9f,
              Md3Type.TITLE_LARGE, t.colors.onSurface);

      this.closeButton.surface = t.colors.surfaceContainerHigh;
      this.closeButton.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
      if (this.closeButton.consumeClick()) {
         this.back();
      }

      for (int i = 0; i < this.fields.size(); i++) {
         Field field = this.fields.get(i);
         field.widget.focused = i == this.focus;
         field.widget.surface = t.colors.surfaceContainerHigh;
         field.widget.render(this.canvas, t, mouseX, mouseY, this.mouseDown, this.dt);
         if (i == this.focus) {
            ImeBridge.onFieldRender((int) field.widget.x + 16, (int) field.widget.y + 28,
                    (int) field.widget.w - 32, field.widget.text,
                    field.widget.text.length(), true);
         }
      }

      float infoY = this.cardY + 64f + this.fields.size() * (this.fieldH + this.fieldGap) + 2f;
      this.renderStatus(t, infoY);

      float by = this.cardY + this.cardH - 20f - 40f;
      float bw = 100f;
      float okX = this.cardX + this.cardW - 24f - bw;
      float cancelX = okX - 8f - bw;
      boolean okHot = ClientUi.hit(mouseX, mouseY, okX, by, bw, 40f);
      int okBase = this.busy ? Md3Colors.composite(
              Md3Colors.alpha(t.colors.onSurface, 0.12f), t.colors.primary) : t.colors.primary;
      if (okHot && !this.busy) {
         okBase = Md3Colors.composite(Md3Colors.alpha(t.colors.onPrimary, 0.12f),
                 t.colors.primary);
      }
      this.canvas.roundRect(okX, by, bw, 40f, 20f, okBase);
      String okLabel = this.busy ? Lang.tr("acc.waiting") : Lang.tr("acc.ok");
      Md3Text.centered(this.canvas, ClientUi.fit(this.canvas, okLabel, Md3Type.LABEL_LARGE, bw - 16f),
              okX + bw / 2f, by + 20f, Md3Type.LABEL_LARGE,
              this.busy ? Md3Colors.alpha(t.colors.onPrimary, 0.7f) : t.colors.onPrimary);

      boolean cancelHot = ClientUi.hit(mouseX, mouseY, cancelX, by, bw, 40f);
      this.canvas.roundRect(cancelX, by, bw, 40f, 20f,
              cancelHot ? t.colors.surfaceContainerHighest : t.colors.surfaceContainerLow);
      Md3Text.centered(this.canvas, Lang.tr("acc.cancel"), cancelX + bw / 2f, by + 20f,
              Md3Type.LABEL_LARGE, t.colors.onSurface);

      this.canvas.end();
   }

   private void renderStatus(Md3Theme t, float y) {
      if ("microsoft".equals(this.type)) {
         if (this.deviceError != null) {
            this.canvas.text(ClientUi.fit(this.canvas,
                    Lang.tr("acc.ms.error") + ": " + this.deviceError,
                    Md3Type.BODY_MEDIUM, this.cardW - 48f),
                    this.cardX + 24f, y, Md3Type.BODY_MEDIUM, t.colors.error);
            return;
         }
         if (this.deviceAccount != null) {
            this.canvas.icon("check_circle", this.cardX + 24f, y - 2f, 18f, t.colors.primary);
            this.canvas.text(Lang.tr("acc.ms.ok"), this.cardX + 48f, y,
                    Md3Type.BODY_MEDIUM, t.colors.primary);
            return;
         }
         if (this.deviceUserCode != null) {
            this.canvas.text(ClientUi.fit(this.canvas,
                    Lang.tr("acc.ms.go") + "  " + this.deviceUri, Md3Type.BODY_SMALL,
                    this.cardW - 48f), this.cardX + 24f, y, Md3Type.BODY_SMALL,
                    t.colors.onSurfaceVariant);
            String code = this.deviceUserCode;
            float cw = Math.max(120f, this.canvas.textWidth(code, Md3Type.TITLE_SMALL) + 28f);
            this.canvas.roundRect(this.cardX + 24f, y + 18f, cw, 26f, 8f,
                    t.colors.surfaceContainerHighest);
            Md3Text.centered(this.canvas, code, this.cardX + 24f + cw / 2f, y + 31f,
                    Md3Type.TITLE_SMALL, t.colors.primary);
            return;
         }
         this.renderBusyLine(t, y);
         return;
      }
      if (!this.status.isEmpty()) {
         boolean error = this.status.startsWith(Lang.tr("acc.err"))
                 || this.status.startsWith("失败");
         this.canvas.text(ClientUi.fit(this.canvas, this.status, Md3Type.BODY_MEDIUM,
                this.cardW - 48f), this.cardX + 24f, y, Md3Type.BODY_MEDIUM,
                error ? t.colors.error : t.colors.onSurfaceVariant);
      } else if (this.busy) {
         this.renderBusyLine(t, y);
      }
   }

   private void renderBusyLine(Md3Theme t, float y) {
      Md3Progress.circular(this.canvas, this.cardX + 32f, y + 8f, 16f, 3f,
              t.colors.primary, t.colors.surfaceContainerHighest, 0f, true, this.time);
      this.canvas.text(ClientUi.fit(this.canvas, this.status, Md3Type.BODY_MEDIUM,
              this.cardW - 64f), this.cardX + 48f, y, Md3Type.BODY_MEDIUM,
              t.colors.onSurfaceVariant);
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
      for (int i = 0; i < this.fields.size(); i++) {
         Md3TextField field = this.fields.get(i).widget;
         if (ClientUi.hit(mouseX, mouseY, field.x, field.y, field.w, field.h)) {
            this.focus = i;
            return;
         }
      }

      float by = this.cardY + this.cardH - 20f - 40f;
      float bw = 100f;
      float okX = this.cardX + this.cardW - 24f - bw;
      float cancelX = okX - 8f - bw;
      if (ClientUi.hit(mouseX, mouseY, okX, by, bw, 40f)) {
         if (!this.busy) {
            ClientUi.click();
            this.submit();
         }
         return;
      }
      if (ClientUi.hit(mouseX, mouseY, cancelX, by, bw, 40f)) {
         ClientUi.click();
         this.back();
      }
   }

   @Override
   protected void mouseReleased(int mouseX, int mouseY, int button) {
      this.mouseDown = false;
   }

   @Override
   protected void keyPressed(char id, int code) {
      if (code == 1) {
         this.back();
         return;
      }
      if (code == 15) {
         if (!this.fields.isEmpty()) {
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            this.focus = (this.focus + (shift ? this.fields.size() - 1 : 1))
                    % this.fields.size();
         }
         return;
      }
      if (code == 28 || code == 156) {
         this.submit();
         return;
      }
      if (this.fields.isEmpty() || this.focus >= this.fields.size()) {
         return;
      }
      Md3TextField field = this.fields.get(this.focus).widget;
      if (ImeBridge.handleKey(id, code)) {
         return;
      }
      if (code == 14) {
         field.backspace();
      } else if (id >= 32 && id != 127) {
         field.typeChar(id);
      }
   }

   private void back() {
      if (this.parent != null) {
         this.client.setScreen(this.parent);
      } else {
         this.client.setScreen(new AccountScreen(null));
      }
   }

   private void submit() {
      if (this.busy) {
         return;
      }
      try {
         if ("offline".equals(this.type)) {
            String name = this.value(0);
            if (name.isEmpty()) {
               this.status = Lang.tr("acc.err.name");
               return;
            }
            this.finish(Auth.offline(name));
         } else if ("token".equals(this.type)) {
            final String token = this.value(0);
            if (token.isEmpty()) {
               this.status = Lang.tr("acc.err.token");
               return;
            }
            this.busy = true;
            this.status = Lang.tr("acc.waiting");
            Thread thread = new Thread(new Runnable() {
               @Override
               public void run() {
                  try {
                     final Accounts.Account account = Auth.fromToken(token);
                     MinecraftClient.getInstance().execute(new Callable<Void>() {
                        public Void call() {
                           AccountLoginScreen.this.finish(account);
                           return null;
                        }
                     });
                  } catch (Exception e) {
                     final String message = e.getMessage() == null
                            ? e.getClass().getSimpleName() : e.getMessage();
                     MinecraftClient.getInstance().execute(new Callable<Void>() {
                        public Void call() {
                           AccountLoginScreen.this.busy = false;
                           AccountLoginScreen.this.status = Lang.tr("acc.err") + ": " + message;
                           return null;
                        }
                     });
                  }
               }
            }, "Client-TokenLogin");
            thread.setDaemon(true);
            thread.start();
         } else if ("skin".equals(this.type)) {
            final String site = this.value(0);
            final String email = this.value(1);
            final String password = this.value(2);
            if (site.isEmpty() || email.isEmpty() || password.isEmpty()) {
               this.status = Lang.tr("acc.err.empty");
               return;
            }
            this.busy = true;
            this.status = Lang.tr("acc.waiting");
            Thread thread = new Thread(new Runnable() {
               @Override
               public void run() {
                  try {
                     final Accounts.Account account = Auth.skinSite(site, email, password);
                     MinecraftClient.getInstance().execute(new Callable<Void>() {
                        public Void call() {
                           AccountLoginScreen.this.finish(account);
                           return null;
                        }
                     });
                  } catch (Exception e) {
                     final String message = e.getMessage() == null
                            ? e.getClass().getSimpleName() : e.getMessage();
                     MinecraftClient.getInstance().execute(new Callable<Void>() {
                        public Void call() {
                           AccountLoginScreen.this.busy = false;
                           AccountLoginScreen.this.status = Lang.tr("acc.err") + ": " + message;
                           return null;
                        }
                     });
                  }
               }
            }, "Client-SkinLogin");
            thread.setDaemon(true);
            thread.start();
         } else if ("microsoft".equals(this.type)) {
            if (this.deviceAccount != null) {
               this.finish(this.deviceAccount);
            } else if (this.deviceError != null) {
               this.status = this.deviceError;
            } else {
               this.status = Lang.tr("acc.ms.waiting");
            }
         }
      } catch (Exception e) {
         this.busy = false;
         this.status = Lang.tr("acc.err") + ": " + e.getMessage();
      }
   }

   private void finish(Accounts.Account account) {
      this.busy = false;
      if ("microsoft".equals(this.type)) {
         String typed = this.typedClientId();
         if (typed != null && !typed.trim().isEmpty()) {
            ClientConfig.msClientId = typed.trim();
            ClientConfig.saveSoon();
         }
      }
      Accounts.add(account);
      this.back();
   }

   private String value(int index) {
      return index < this.fields.size() ? this.fields.get(index).widget.text.trim() : "";
   }

   @Override
   public boolean shouldPauseGame() {
      return false;
   }

   private static final class Field {
      final Md3TextField widget;

      Field(Md3TextField widget) {
         this.widget = widget;
      }
   }
}
