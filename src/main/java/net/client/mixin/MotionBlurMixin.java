package net.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import net.client.ClientConfig;
import net.client.util.ClientLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 动态模糊：四套样式，按<b>模糊机制</b>命名（1.0.1_28 起不再借用其他客户端名字）。
 *
 * <ul>
 *   <li><b>0 时间累积</b>：帧反馈式累积，把上一帧按权重混回本帧，并加左右各 0.6px
 *       亚像素重采样，避免硬残影。</li>
 *   <li><b>1 线性拖影</b>：子帧叠加的硬拖尾，残影更重、双影更明显。</li>
 *   <li><b>2 速度模糊</b>：逐像素速度场在没有 GLSL 的环境里退化为整屏位移——用摄像机
 *       「旋转 + 平移」的每帧变化量估算屏幕位移，再沿该方向做等权重抽样，
 *       得到定向拉丝而不是全屏残影。</li>
 *   <li><b>3 柔化累积</b>：帧率无关的累积缓冲，按时间衰减 + 少量亚像素柔化。</li>
 * </ul>
 *
 * <p><b>衰减必须按时间、不能按帧</b>：1.0.1_24 最初用「每帧固定保留率」，在 120Hz 手机上
 * 一帧只有 8ms，残影不到 20ms 就衰减干净，看起来跟没开一样。现在统一换算成
 * {@code pow(retain60, dt * 60)}——以 60fps 的保留率为基准按帧间隔折算，
 * 30 / 60 / 120fps 下拖影的<b>时长</b>一致。
 *
 * <p><b>累积必须与显示解耦（1.0.1_27）</b>：屏幕/纹理都是 8 位色深。250fps 下每帧混合
 * 权重只有 0.02 左右，{@code 0.02 × 像素色差 < 0.5} 会被四舍五入成 0——残影根本无法
 * 累积，只有高对比边缘留一点痕迹，表现就是「100% 看着像别家 50%」。现在反馈累积
 * 固定按 60Hz 步进执行（单次权重 0.05–0.5，远高于舍入阈值，与 60fps 客户端工况一致），
 * 高刷的中间帧只把累积好的残影纹理叠显出来、不回写，拖影浓度不再随帧率被稀释。
 *
 * <p>全部固定管线（Tessellator + {@code glCopyTexSubImage2D}），不使用 GLSL，
 * 也不使用加色混合（{@code SRC_ALPHA/ONE}）——后者在 Krypton / gl4es 的 GL ES 转译层会过曝。
 */
@Mixin(
   value = {GameRenderer.class},
   priority = 1100
)
public class MotionBlurMixin {
   /** 时间累积：帧反馈 + 亚像素重采样。 */
   private static final int STYLE_TEMPORAL = 0;
   /** 线性拖影：子帧叠加的硬拖尾（延续旧配置里 blurStyle=1 的语义）。 */
   private static final int STYLE_LINEAR = 1;
   /** 速度模糊：沿摄像机运动方向的定向拉丝。 */
   private static final int STYLE_VELOCITY = 2;
   /** 柔化累积：帧率无关的累积缓冲 + 柔化。 */
   private static final int STYLE_SOFT = 3;

   private static final int GL_QUADS = 7;
   private static final int GL_TEXTURE_2D = 3553;
   /** 单帧保留率安全上限：必须小于 1，保证当前帧始终透入、残影收敛不糊屏。 */
   private static final float MAX_FRAME_RETAIN = 0.995F;
   /** 定向模糊的抽样数（连当前帧本身一共 VELOCITY_TAPS+1 个采样）。 */
   private static final int VELOCITY_TAPS = 5;
   /** 位移换算用的参考距离（方块）：按 4 格处的物体估算屏幕位移。 */
   private static final float REF_DISTANCE = 4.0F;
   private static final long LOG_INTERVAL_NANO = 10000000000L;
   /** 反馈累积的固定步长（秒）：60Hz。8 位色深下单次权重必须够大才不会被舍入吃掉。 */
   private static final float ACC_STEP = 1.0F / 60.0F;

   private static int lc$tex = -1;
   /** 速度模糊样式专用：上一帧最终合成结果，用来给本体运动（挥剑、挥臂）留残影。 */
   private static int lc$texPrev = -1;
   private static boolean lc$prevValid = false;
   private static int lc$w = 0;
   private static int lc$h = 0;
   private static int lc$style = -1;
   private static WeakReference<World> lc$world = new WeakReference<>(null);

   private static boolean lc$hasCamera = false;
   private static float lc$lastYaw = 0.0F;
   private static float lc$lastPitch = 0.0F;
   private static double lc$lastCamX = 0.0;
   private static double lc$lastCamY = 0.0;
   private static double lc$lastCamZ = 0.0;
   private static float lc$velX = 0.0F;
   private static float lc$velY = 0.0F;

   private static long lc$lastFrameNano = -1L;
   private static long lc$lastLogNano = -1L;
   /** 距上一次反馈累积经过的时间；≥ ACC_STEP 才执行一次「混合 + 回写」。 */
   private static float lc$accTime = 0.0F;

   // 进入绘制前记录的 GL 开关，画完按原样恢复，避免影响 HUD / 后续渲染
   private static boolean lc$savedScissor = false;
   private static boolean lc$savedAlphaTest = false;
   private static boolean lc$savedLighting = false;

   @Inject(
      method = {"renderWorld(FJ)V"},
      at = {@At("TAIL")}
   )
   private void client$motionBlur(float tickDelta, long limitTime, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      // 导出/回放采集期间临时关掉动态模糊：帧反馈累积在逐帧抓图链路里会把上一帧
      // 残影混进当前帧，导出画面发花；采集结束后下一帧自动恢复。
      boolean capturing = net.client.replay.ReplayClient.isReplayCaptureActive()
         || (net.client.replay.ReplayClient.getRenderManager() != null
             && net.client.replay.ReplayClient.getRenderManager().isRendering());
      if (!ClientConfig.motionBlur || client.world == null || client.player == null || capturing
              || (client.currentScreen != null && ClientConfig.backdropBlur > 0.001f)) {
         // 只有背景模糊真的生效时才叠不上：菜单背景本身就是整屏模糊，两者叠在一起
         // 又晃又糊。背景模糊关掉时，界面里保留原来的动态模糊。
         lc$hasCamera = false;
         lc$lastFrameNano = -1L;
         return;
      }

      Framebuffer framebuffer = client.getFramebuffer();
      if (framebuffer == null) {
         return;
      }
      int w = framebuffer.viewportWidth;
      int h = framebuffer.viewportHeight;
      if (w <= 0 || h <= 0) {
         return;
      }

      int style = ClientConfig.blurStyle;
      if (style < STYLE_TEMPORAL || style > STYLE_SOFT) {
         style = STYLE_TEMPORAL;
      }
      float strength = ClientConfig.blurAmount;
      if (strength <= 0.0F) {
         return;
      }
      if (strength > 1.0F) {
         strength = 1.0F;
      }

      World world = client.world;
      World tracked = lc$world.get();
      if (tracked != world) {
         // 换世界 / 重生：累积缓冲里的旧画面没有意义
         lc$world = new WeakReference<>(world);
         lc$hasCamera = false;
         lc$lastFrameNano = -1L;
         lc$velX = 0.0F;
         lc$velY = 0.0F;
         lc$accTime = 0.0F;
      }

      boolean recreate = lc$tex == -1 || lc$w != w || lc$h != h || lc$style != style;
      if (recreate) {
         lc$releaseTexture();
         lc$tex = lc$createTexture(w, h);
         lc$prevValid = false;
         if (style == STYLE_VELOCITY) {
            lc$texPrev = lc$createTexture(w, h);
         }
         lc$w = w;
         lc$h = h;
         lc$style = style;
         lc$lastFrameNano = -1L;
         lc$velX = 0.0F;
         lc$velY = 0.0F;
         lc$hasCamera = false;
         lc$accTime = 0.0F;
      }

      long now = System.nanoTime();
      float dt = 1.0F / 60.0F;
      if (lc$lastFrameNano > 0L) {
         dt = (float)((now - lc$lastFrameNano) / 1.0e9);
      }
      lc$lastFrameNano = now;
      // 卡顿或切后台回来时 dt 会很大，截断避免一帧吃掉整段拖影
      if (dt < 0.004F) {
         dt = 0.004F;
      }
      if (dt > 0.05F) {
         dt = 0.05F;
      }

      lc$updateVelocity(client, w, h, dt);

      // 新建或换样式时纹理里还没有可用历史，只存一帧当前画面，下一帧再开始出效果
      if (recreate) {
         lc$copyScreenToTexture(lc$tex, w, h);
         return;
      }

      lc$accTime += dt;
      // 累积帧：距上次回写 ≥ 1/60s，用覆盖整段时间的大权重做「混合 + 回写」；
      // 显示帧：只把累积好的残影按 60Hz 步长的权重叠出来，不回写（避免小权重被 8 位舍入吃掉）
      boolean accFrame = lc$accTime >= ACC_STEP;
      float blendDt = accFrame ? lc$accTime : ACC_STEP;

      lc$begin(w, h);
      try {
         switch (style) {
            case STYLE_LINEAR:
               lc$drawLinear(strength, blendDt, w, h);
               if (accFrame) {
                  lc$copyScreenToTexture(lc$tex, w, h);
                  lc$accTime = 0.0F;
               }
               break;
            case STYLE_VELOCITY:
               // 1) 抓干净当前帧，沿摄像机运动方向做定向抽样（大范围转视角的拉丝）
               lc$copyScreenToTexture(lc$tex, w, h);
               lc$drawVelocity(strength, w, h);
               // 2) 再叠一层上一帧合成结果：摄像机不动、只有挥剑/挥手/实体在动时，
               //    这一层只作用在「变化了的像素」上，于是武器有残影、静止的世界依然清晰
               if (lc$prevValid && lc$texPrev != -1) {
                  lc$quadTex(lc$texPrev, 0.0F, 0.0F, (float)w, (float)h,
                     lc$retain(lc$halfLife(STYLE_VELOCITY, strength), blendDt));
               }
               // 3) 只在累积帧把合成结果存成「上一帧」，显示帧不回写
               if (accFrame) {
                  lc$copyScreenToTexture(lc$texPrev, w, h);
                  lc$prevValid = true;
                  lc$accTime = 0.0F;
               }
               break;
            case STYLE_SOFT:
               lc$drawSoft(strength, blendDt, w, h);
               if (accFrame) {
                  lc$copyScreenToTexture(lc$tex, w, h);
                  lc$accTime = 0.0F;
               }
               break;
            case STYLE_TEMPORAL:
            default:
               lc$drawTemporal(strength, blendDt, w, h);
               if (accFrame) {
                  lc$copyScreenToTexture(lc$tex, w, h);
                  lc$accTime = 0.0F;
               }
               break;
         }
      } finally {
         lc$end();
      }

      lc$logOnce(style, strength, w, h, dt, now);
   }

   /**
    * 由「半衰期（秒）」算出本帧保留率。
    *
    * <p>先换算出 60fps 下应有的每帧保留率 {@code 0.5^(1/(hl*60))}，再按本帧实际间隔
    * 折算 {@code pow(retain60, dt*60)}——这样 30 / 60 / 250fps 下拖影的<b>时长</b>一致。
    * 之前按「每帧固定保留率 + 0.94 上限」写，250fps 时 4ms 一帧，100% 强度只剩 45ms
    * 半衰期，所以看着只有别的客户端 50%–70% 的量。
    */
   private static float lc$retain(float halfLife, float dt) {
      if (halfLife < 0.005F) {
         halfLife = 0.005F;
      }
      double retain60 = Math.pow(0.5, 1.0 / ((double)halfLife * 60.0));
      float r = (float)Math.pow(retain60, (double)(dt * 60.0F));
      return r > MAX_FRAME_RETAIN ? MAX_FRAME_RETAIN : r;
   }

   /** 各样式在给定强度下的半衰期（秒）。 */
   private static float lc$halfLife(int style, float strength) {
      switch (style) {
         case STYLE_LINEAR:
            return 0.03F + 0.27F * strength;
         case STYLE_VELOCITY:
            return 0.02F + 0.10F * strength;
         case STYLE_SOFT:
            return 0.03F + 0.23F * strength;
         case STYLE_TEMPORAL:
         default:
            return 0.03F + 0.19F * strength;
      }
   }

   // ---- 样式 0：时间累积（帧反馈 + 亚像素重采样）----
   private static void lc$drawTemporal(float strength, float dt, int w, int h) {
      // 半衰期 0.03..0.22 秒，拉满可见拖影约 0.5 秒
      float retain = lc$retain(lc$halfLife(STYLE_TEMPORAL, strength), dt);
      // 左右各 0.6px 的重采样让残影边缘变软，接近 LC 那种「糊过去」而不是「贴一张旧图」
      float shift = 0.6F;
      lc$quad(-shift, 0.0F, (float)w - shift, (float)h, retain * 0.14F);
      lc$quad(shift, 0.0F, (float)w + shift, (float)h, retain * 0.14F);
      lc$quad(0.0F, 0.0F, (float)w, (float)h, retain * 0.72F);
   }

   // ---- 样式 1：线性拖影（子帧叠加的硬拖尾）----
   private static void lc$drawLinear(float strength, float dt, int w, int h) {
      float retain = lc$retain(lc$halfLife(STYLE_LINEAR, strength), dt);
      lc$quad(0.0F, 0.0F, (float)w, (float)h, retain);
   }

   // ---- 样式 2：速度模糊（沿位移方向定向抽样）----
   private static void lc$drawVelocity(float strength, int w, int h) {
      float vx = lc$velX * strength;
      float vy = lc$velY * strength;
      float len = (float)Math.sqrt((double)(vx * vx + vy * vy));
      if (len < 0.5F) {
         // 静止或极慢：位移不足半个像素，抽样没有意义
         return;
      }
      float maxLen = 0.10F * (float)w;
      if (len > maxLen) {
         float k = maxLen / len;
         vx *= k;
         vy *= k;
      }
      /*
       * 等权重抽样：第 i 次以 1/(i+1) 的 alpha 叠加，N 次之后每个采样（含原始帧）
       * 的权重都收敛到 1/(N+1)，等价于「沿速度方向取平均」，不需要多目标 FBO。
       */
      for (int i = 1; i <= VELOCITY_TAPS; i++) {
         float k = (float)i / (float)VELOCITY_TAPS;
         float alpha = 1.0F / (float)(i + 1);
         float ox = vx * k;
         float oy = vy * k;
         lc$quad(ox, oy, (float)w + ox, (float)h + oy, alpha);
      }
   }

   // ---- 样式 3：柔化累积（帧率无关累积 + 柔化）----
   private static void lc$drawSoft(float strength, float dt, int w, int h) {
      // 半衰期 0.03..0.26 秒
      float retain = lc$retain(lc$halfLife(STYLE_SOFT, strength), dt);
      float radius = 0.5F + 1.5F * strength;
      // 3 抽样：中心 72%、两侧对角各 14%，总权重等于 retain
      lc$quad(0.0F, 0.0F, (float)w, (float)h, retain * 0.72F);
      lc$quad(-radius, -radius, (float)w - radius, (float)h - radius, retain * 0.14F);
      lc$quad(radius, radius, (float)w + radius, (float)h + radius, retain * 0.14F);
   }

   /**
    * 用摄像机的「旋转 + 平移」估算屏幕空间位移（像素）。
    * 逐像素速度场需要深度 + 投影矩阵 + GLSL，这里退一步用整屏一致的位移向量：
    * <ul>
    *   <li>旋转：yaw/pitch 每帧变化量 × 每度像素数；</li>
    *   <li>平移：位置变化在摄像机右方向上的投影 × 每度像素数 / 参考距离，
    *       这样横移、后退也会出拉丝，不再只有「转视角」才有模糊。</li>
    * </ul>
    * 位移按 {@code 1/(60dt)} 归一化到「60fps 一帧的快门长度」，帧率变了拖影长度不变。
    */
   private static void lc$updateVelocity(MinecraftClient client, int w, int h, float dt) {
      Entity camera = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
      if (camera == null) {
         return;
      }
      float yaw = camera.yaw;
      float pitch = camera.pitch;
      double px = camera.x;
      double py = camera.y;
      double pz = camera.z;

      if (!lc$hasCamera) {
         lc$lastYaw = yaw;
         lc$lastPitch = pitch;
         lc$lastCamX = px;
         lc$lastCamY = py;
         lc$lastCamZ = pz;
         lc$hasCamera = true;
         lc$velX = 0.0F;
         lc$velY = 0.0F;
         return;
      }

      float dyaw = yaw - lc$lastYaw;
      while (dyaw > 180.0F) {
         dyaw -= 360.0F;
      }
      while (dyaw < -180.0F) {
         dyaw += 360.0F;
      }
      float dpitch = pitch - lc$lastPitch;
      double dx = px - lc$lastCamX;
      double dy = py - lc$lastCamY;
      double dz = pz - lc$lastCamZ;

      lc$lastYaw = yaw;
      lc$lastPitch = pitch;
      lc$lastCamX = px;
      lc$lastCamY = py;
      lc$lastCamZ = pz;

      // 传送/回城：位置一次跳十几格，丢弃这一帧的平移量
      double moveLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (moveLen > 12.0) {
         dx = 0.0;
         dy = 0.0;
         dz = 0.0;
      }

      float fovV = client.options.fov;
      if (fovV < 10.0F) {
         fovV = 10.0F;
      }
      float aspect = (float)w / (float)h;
      float fovH = (float)Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians((double)fovV) * 0.5) * (double)aspect));
      float pxPerDegX = (float)w / fovH;
      float pxPerDegY = (float)h / fovV;

      // 摄像机右方向 = (-cos yaw, -sin yaw)（MC 里 yaw=0 朝南，右手边是西）
      float yawRad = (float)Math.toRadians((double)yaw);
      float rightX = -(float)Math.cos((double)yawRad);
      float rightZ = -(float)Math.sin((double)yawRad);
      float lateral = (float)(dx * (double)rightX + dz * (double)rightZ);
      float vertical = (float)dy;

      // 归一化到 60fps 一帧的快门长度
      float shutter = 1.0F / (60.0F * dt);
      if (shutter < 0.25F) {
         shutter = 0.25F;
      }
      if (shutter > 4.0F) {
         shutter = 4.0F;
      }

      // 向右转（yaw 增大）内容左移；向右平移内容也左移；低头/下移内容上移
      float targetX = (-dyaw * pxPerDegX - lateral * pxPerDegX / REF_DISTANCE) * shutter;
      float targetY = (-dpitch * pxPerDegY + vertical * pxPerDegY / REF_DISTANCE) * shutter;

      // 鼠标输入是离散的，平滑一下否则每帧方向都在跳
      float ease = 0.35F;
      lc$velX += (targetX - lc$velX) * ease;
      lc$velY += (targetY - lc$velY) * ease;
   }

   /** 日志里用的样式名，与设置界面的「模糊机制」命名保持一致。 */
   private static String lc$styleName(int style) {
      switch (style) {
         case STYLE_LINEAR:
            return "linear";
         case STYLE_VELOCITY:
            return "velocity";
         case STYLE_SOFT:
            return "soft";
         default:
            return "temporal";
      }
   }

   /** 每 10 秒打一行诊断，方便在真机上确认样式/强度/帧率/位移有没有生效。 */
   private static void lc$logOnce(int style, float strength, int w, int h, float dt, long now) {
      if (lc$lastLogNano > 0L && now - lc$lastLogNano < LOG_INTERVAL_NANO) {
         return;
      }
      lc$lastLogNano = now;
      int fps = Math.round(1.0F / dt);
      float halfLife = lc$halfLife(style, strength);
      float stepAlpha = 1.0F - lc$retain(halfLife, ACC_STEP);
      ClientLog.info(String.format(
         "[MotionBlur] style=%s strength=%.2f size=%dx%d fps=%d halfLife=%.0fms stepAlpha=%.3f vel=%.1f,%.1f",
         lc$styleName(style), strength, w, h, fps, halfLife * 1000.0F, stepAlpha, lc$velX, lc$velY
      ));
   }

   // ---- 绘制与 GL 状态 ----
   private static void lc$begin(int w, int h) {
      /*
       * 裁剪测试 / 透明度测试 / 光照如果还开着，我们的全屏 quad 会被裁掉或者整片丢弃，
       * 表现就是「开了跟没开一样」。这里显式关掉、画完再按原状态恢复。
       */
      lc$savedScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      lc$savedAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
      lc$savedLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
      if (lc$savedScissor) {
         GL11.glDisable(GL11.GL_SCISSOR_TEST);
      }
      if (lc$savedAlphaTest) {
         GlStateManager.disableAlphaTest();
      }
      if (lc$savedLighting) {
         GlStateManager.disableLighting();
      }
      GlStateManager.matrixMode(GL11.GL_PROJECTION);
      GlStateManager.pushMatrix();
      GlStateManager.loadIdentity();
      GL11.glOrtho(0.0, (double)w, (double)h, 0.0, -1.0, 1.0);
      GlStateManager.matrixMode(GL11.GL_MODELVIEW);
      GlStateManager.pushMatrix();
      GlStateManager.loadIdentity();
      GlStateManager.disableDepthTest();
      GlStateManager.disableCull();
      GlStateManager.enableBlend();
      GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GlStateManager.enableTexture();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static void lc$end() {
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GlStateManager.disableBlend();
      GlStateManager.enableDepthTest();
      GlStateManager.enableCull();
      GlStateManager.matrixMode(GL11.GL_PROJECTION);
      GlStateManager.popMatrix();
      GlStateManager.matrixMode(GL11.GL_MODELVIEW);
      GlStateManager.popMatrix();
      GlStateManager.bindTexture(0);
      if (lc$savedLighting) {
         GlStateManager.enableLighting();
      }
      if (lc$savedAlphaTest) {
         GlStateManager.enableAlphaTest();
      }
      if (lc$savedScissor) {
         GL11.glEnable(GL11.GL_SCISSOR_TEST);
      }
   }

   /**
    * 画一张铺满纹理的全屏四边形。y 轴向下为正（与 glOrtho 的 top-down 投影一致），
    * 纹理 v=0 对应拷贝进来的屏幕底行，所以底边取 v=0。
    */
   private static void lc$quad(float x0, float y0, float x1, float y1, float alpha) {
      lc$quadTex(lc$tex, x0, y0, x1, y1, alpha);
   }

   private static void lc$quadTex(int texture, float x0, float y0, float x1, float y1, float alpha) {
      if (texture == -1 || alpha <= 0.0F) {
         return;
      }
      GlStateManager.bindTexture(texture);
      Tessellator tessellator = Tessellator.getInstance();
      BufferBuilder buffer = tessellator.getBuffer();
      buffer.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      buffer.vertex((double)x0, (double)y1, 0.0).texture(0.0, 0.0).color(1.0F, 1.0F, 1.0F, alpha).next();
      buffer.vertex((double)x1, (double)y1, 0.0).texture(1.0, 0.0).color(1.0F, 1.0F, 1.0F, alpha).next();
      buffer.vertex((double)x1, (double)y0, 0.0).texture(1.0, 1.0).color(1.0F, 1.0F, 1.0F, alpha).next();
      buffer.vertex((double)x0, (double)y0, 0.0).texture(0.0, 1.0).color(1.0F, 1.0F, 1.0F, alpha).next();
      tessellator.draw();
   }

   private static void lc$copyScreenToTexture(int texture, int w, int h) {
      if (texture == -1) {
         return;
      }
      GlStateManager.bindTexture(texture);
      GL11.glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
      GlStateManager.bindTexture(0);
   }

   private static int lc$createTexture(int w, int h) {
      int id = GL11.glGenTextures();
      GlStateManager.bindTexture(id);
      GL11.glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      ByteBuffer empty = ByteBuffer.allocateDirect(w * h * 4);
      GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, empty);
      return id;
   }

   private static void lc$releaseTexture() {
      if (lc$tex != -1) {
         GL11.glDeleteTextures(lc$tex);
         lc$tex = -1;
      }
      if (lc$texPrev != -1) {
         GL11.glDeleteTextures(lc$texPrev);
         lc$texPrev = -1;
      }
      lc$prevValid = false;
      lc$w = 0;
      lc$h = 0;
   }
}
