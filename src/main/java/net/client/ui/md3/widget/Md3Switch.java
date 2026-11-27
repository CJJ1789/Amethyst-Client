package net.client.ui.md3.widget;

import net.client.ui.md3.anim.Md3Anim;
import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;

/** M3 switch. */
public class Md3Switch extends Md3Widget {
    public boolean checked;
    public int surface;
    private final Md3Anim knob = new Md3Anim(0f);

    public void setChecked(boolean checked) {
        this.checked = checked;
        knob.snap(checked ? 1f : 0f);
    }

    /** 外部同步状态但不打断动画，只改变目标值。 */
    public void setCheckedAnimated(boolean checked) {
        this.checked = checked;
    }

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        float a = knob.get();
        float trackW = 52f, trackH = 32f;
        float tx = x + (w - trackW) / 2f;
        float ty = y + (h - trackH) / 2f;
        // 关闭态 track 用外部给的 surface（会带「面板不透明度」和控件托底黑的 alpha），
        // 让开关跟卡片一起变透；开启态往实色 primary 收，保证 on 一眼能认出来。
        int closedTrack = surface != 0 ? surface : t.colors.surfaceContainerHighest;
        int track = Md3Colors.lerp(closedTrack, t.colors.primary, a);
        if (!enabled) {
            track = Md3Paint.disabledContainer(surface != 0 ? surface : t.colors.surface, t.colors.onSurface);
        }
        c.roundRect(tx, ty, trackW, trackH, trackH / 2f, track);
        if (a < 0.5f && enabled) {
            c.strokeRoundRect(tx, ty, trackW, trackH, trackH / 2f, 2f, t.colors.outline);
        }

        if (hoverAnim.get() > 0.01f && enabled) {
            float halo = 40f;
            c.roundRect(tx + trackW / 2f - halo / 2f, ty + trackH / 2f - halo / 2f, halo, halo, halo / 2f,
                    Md3Colors.alpha(t.colors.onSurface, 0.08f * hoverAnim.get()));
        }

        float thumbR = 10f + a * 3f;
        float thumbX = tx + 16f + (trackW - 32f) * a;
        int thumb = Md3Colors.lerp(t.colors.outline, t.colors.onPrimary, a);
        if (!enabled) thumb = Md3Paint.disabledContent(t.colors.onSurface);
        c.roundRect(thumbX - thumbR, ty + trackH / 2f - thumbR, thumbR * 2f, thumbR * 2f, thumbR, thumb);
        if (a < 0.5f && enabled) {
            c.roundRect(thumbX - thumbR + 2f, ty + trackH / 2f - thumbR + 2f,
                    (thumbR - 2f) * 2f, (thumbR - 2f) * 2f, thumbR - 2f, closedTrack);
        }
        knob.target(checked ? 1f : 0f);
        knob.update(dt, 10f);
    }
}
