package net.client.util;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ServerEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

/**
 * Mouse drag reordering for the vanilla multiplayer server list.
 *
 * <p>The row is only moved on release; while dragging, the original list stays
 * untouched. Dropping onto row N moves the source row to that exact index, so
 * dragging down past a row places the source below it and dragging up places
 * it above it.</p>
 */
public final class ServerListDrag {
   private static boolean active;
   private static MultiplayerScreen screen;
   private static MultiplayerServerListWidget widget;
   private static ServerList list;
   private static int source = -1;
   private static int target = -1;
   private static int startX;
   private static int startY;
   private static boolean moved;
   private static long pickupNanos;
   private static long ghostFrameNanos;
   private static float ghostScale;

   private ServerListDrag() {
   }

   public static void reset() {
      active = false;
      screen = null;
      widget = null;
      list = null;
      source = -1;
      target = -1;
      moved = false;
      pickupNanos = 0L;
      ghostFrameNanos = 0L;
      ghostScale = 0.9f;
      startX = 0;
      startY = 0;
   }

   /** True while a server row is being held for reordering. */
   public static boolean isActive() {
      return active;
   }

   /** Called from MultiplayerScreen.mouseClicked before vanilla selection. */
   public static void onPress(MultiplayerScreen owner, MultiplayerServerListWidget view,
                              ServerList serverList, int mouseX, int mouseY, int button) {
      reset();
      if (button != 0 || owner == null || view == null || serverList == null) {
         return;
      }
      int index = serverEntryAt(owner, view, serverList, mouseX, mouseY);
      if (index < 0) {
         return;
      }
      // Do not start a drag on the left icon / sort-button strip: vanilla
      // would immediately connect or reorder when the press lands there.
      int rowWidth = view.getRowWidth();
      int rowLeft = owner.width / 2 - rowWidth / 2;
      if (mouseX < rowLeft + 36) {
         return;
      }
      active = true;
      screen = owner;
      widget = view;
      list = serverList;
      source = index;
      target = index;
      startX = mouseX;
      startY = mouseY;
      moved = false;
      pickupNanos = System.nanoTime();
      ghostFrameNanos = 0L;
      ghostScale = 0.9f;
   }

   /**
    * Called from Screen.mouseDragged for the multiplayer screen.  Returning
    * true makes the mixin cancel the event so the vanilla list cannot also
    * scroll while a server row is being dragged.
    */
   public static boolean onDrag(MultiplayerScreen owner, int mouseX, int mouseY, int button) {
      if (!active || owner != screen || button != 0) {
         return false;
      }
      if (!moved && (Math.abs(mouseX - startX) >= 5 || Math.abs(mouseY - startY) >= 5)) {
         moved = true;
         // Start the pickup animation when the drag actually begins, not when
         // the mouse button went down: a slow press would otherwise finish the
         // animation before the ghost is first visible.
         pickupNanos = System.nanoTime();
         ghostFrameNanos = 0L;
         ghostScale = 0.9f;
      }
      if (moved) {
         int index = serverEntryAt(owner, widget, list, mouseX, mouseY);
         if (index >= 0) {
            target = index;
         }
      }
      return true;
   }

   /** Called from MultiplayerScreen.mouseReleased at HEAD; true means consume the event. */
   public static boolean onRelease(MultiplayerScreen owner, int mouseX, int mouseY, int button) {
      if (!active || owner != screen) {
         return false;
      }
      boolean apply = button == 0 && moved && target >= 0 && target != source;
      LocalState state = new LocalState(source, target);
      ServerList serverList = list;
      MultiplayerServerListWidget view = widget;
      reset();
      if (!apply || serverList == null || view == null) {
         return false;
      }

      int from = state.from;
      int to = state.to;
      if (to < 0 || to >= serverList.size()) {
         return false;
      }
      while (from < to) {
         serverList.swapEntries(from, from + 1);
         from++;
      }
      while (from > to) {
         serverList.swapEntries(from, from - 1);
         from--;
      }
      view.setServers(serverList);
      owner.selectEntry(to);
      serverList.saveFile();
      return true;
   }


   /** Floating name tag with a short lift-in animation while dragging. */
   public static void renderGhost(int mouseX, int mouseY) {
      if (!active || !moved || list == null || source < 0 || source >= list.size()) {
         return;
      }
      ServerInfo info = list.get(source);
      if (info == null) {
         return;
      }
      // Scrolling is allowed while dragging. Keep the drop target in sync with
      // the row currently under the stationary cursor after a wheel scroll.
      if (screen != null && widget != null && list != null) {
         int index = serverEntryAt(screen, widget, list, mouseX, mouseY);
         if (index >= 0) {
            target = index;
         }
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null) {
         return;
      }
      long now = System.nanoTime();
      if (ghostFrameNanos != 0L) {
         float dt = Math.min(0.05f, (now - ghostFrameNanos) / 1.0E9f);
         ghostScale += (1.0f - ghostScale) * Math.min(1f, dt * 16f);
      }
      ghostFrameNanos = now;
      float age = pickupNanos == 0L ? 1f : Math.min(1f, (now - pickupNanos) / 160_000_000f);
      float ease = 1.0f - (1.0f - age) * (1.0f - age) * (1.0f - age);
      float scale = 0.9f + 0.1f * Math.max(ease, ghostScale);
      float lift = (1.0f - ease) * 10f;

      String name = info.name == null ? "" : info.name;
      int textW = Math.max(44, mc.textRenderer.getStringWidth(name));
      int boxW = textW + 24;
      int boxH = 22;
      GlStateManager.pushMatrix();
      GlStateManager.translate(mouseX + 10f + boxW / 2f, mouseY - 8f + lift + boxH / 2f, 0f);
      GlStateManager.scale(scale, scale, 1f);
      // Soft drop shadow first, then the elevated MD3-toned tag.
      DrawableHelper.fill(-boxW / 2 - 3, -boxH / 2 + 3, boxW / 2 + 3, boxH / 2 + 3, 0x55000000);
      DrawableHelper.fill(-boxW / 2, -boxH / 2, boxW / 2, boxH / 2, 0xEE2B2138);
      mc.textRenderer.draw(name, -textW / 2, -4, 0xFFF3EDFF);
      GlStateManager.popMatrix();
   }

   private static int serverEntryAt(MultiplayerScreen owner, MultiplayerServerListWidget view,
                                    ServerList serverList, int mouseX, int mouseY) {
      if (view == null || serverList == null || !view.isMouseInList(mouseY)) {
         return -1;
      }
      int index = view.getEntryAt(mouseX, mouseY);
      if (index < 0 || index >= serverList.size()) {
         return -1;
      }
      Object entry = view.getEntry(index);
      if (!(entry instanceof ServerEntry)) {
         return -1;
      }
      ServerInfo info = ((ServerEntry) entry).getServer();
      return info == null ? -1 : index;
   }

   private static final class LocalState {
      final int from;
      final int to;

      LocalState(int from, int to) {
         this.from = from;
         this.to = to;
      }
   }
}
