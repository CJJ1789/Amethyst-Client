package net.client.ui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;

/**
 * Pixel-based scroll model with a short eased follow, a draggable M3 scrollbar
 * and the usual wheel handling.  Screens only provide content/viewport height.
 */
public final class ClientScroll {
   public float value;
   public float target;
   public boolean dragging;
   private float grabOffset;
   private float content;
   private float viewport;

   public void measure(float content, float viewport) {
      this.content = Math.max(0f, content);
      this.viewport = Math.max(1f, viewport);
      clamp();
   }

   public float max() {
      return Math.max(0f, content - viewport);
   }

   private void clamp() {
      float max = max();
      if (value < 0f) value = 0f;
      if (value > max) value = max;
      if (target < 0f) target = 0f;
      if (target > max) target = max;
   }

   public void jump(float v) {
      value = v;
      target = v;
      clamp();
   }

   public void scrollBy(float delta) {
      target += delta;
      clamp();
   }

   public void update(float dt) {
      float speed = Math.min(1f, dt * 15f);
      value += (target - value) * speed;
      if (Math.abs(target - value) < 0.6f) {
         value = target;
      }
      clamp();
   }

   public float thumbHeight() {
      if (content <= 0f) return viewport;
      return Math.max(24f, viewport * viewport / content);
   }

   public float thumbY(float trackY) {
      float max = max();
      float track = viewport - thumbHeight();
      return trackY + (max <= 0f ? 0f : (value / max) * Math.max(0f, track));
   }

   public boolean scrollable() {
      return content > viewport + 0.5f;
   }

   public void renderBar(Md3Canvas c, Md3Theme t, float x, float trackY, float width) {
      if (!scrollable()) return;
      float h = thumbHeight();
      float y = thumbY(trackY);
      c.roundRect(x, trackY, width, viewport, width / 2f,
              Md3Colors.alpha(t.colors.onSurface, 0.06f));
      c.roundRect(x, y, width, h, width / 2f,
              dragging ? t.colors.primary : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.62f));
   }

   /** @return true when the press was consumed by the scrollbar. */
   public boolean press(float mx, float my, float x, float trackY, float width, float slop) {
      if (!scrollable()) return false;
      if (mx < x - slop || mx > x + width + slop) return false;
      if (my < trackY - slop || my > trackY + viewport + slop) return false;

      float thumbY = thumbY(trackY);
      float thumbH = thumbHeight();
      if (my >= thumbY && my <= thumbY + thumbH) {
         grabOffset = my - thumbY;
      } else {
         grabOffset = thumbH / 2f;
         dragTo(my - grabOffset, trackY);
      }
      dragging = true;
      return true;
   }

   public void drag(float my, float trackY) {
      if (dragging) {
         dragTo(my - grabOffset, trackY);
      }
   }

   private void dragTo(float thumbTop, float trackY) {
      float track = viewport - thumbHeight();
      if (track <= 0f) {
         jump(0f);
         return;
      }
      float ratio = (thumbTop - trackY) / track;
      if (ratio < 0f) ratio = 0f;
      if (ratio > 1f) ratio = 1f;
      jump(ratio * max());
   }

   public void release() {
      dragging = false;
   }

   /** Returns the row index at a Y coordinate, given content height and row height. */
   public int rowAt(float my, float listTop, float rowHeight, int count) {
      if (rowHeight <= 0f) return -1;
      int index = (int) Math.floor((my - listTop + value) / rowHeight);
      return index >= 0 && index < count ? index : -1;
   }
}
