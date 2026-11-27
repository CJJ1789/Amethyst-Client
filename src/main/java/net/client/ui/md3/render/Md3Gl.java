package net.client.ui.md3.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Low level GL helpers: texture upload and packed-pixel conversion. */
public final class Md3Gl {
    private Md3Gl() {
    }

    public static int upload(BufferedImage image, boolean mipmap) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        return uploadArgb(pixels, w, h, mipmap);
    }

    /**
     * Font atlases are supersampled bitmaps that are normally magnified on a
     * high-DPI GUI.  Mipmapping and GL_LINEAR magnification turn the 2x atlas
     * into a soft, washed-out blur, so text gets a nearest-magnified texture
     * without mipmaps while minification still uses linear filtering.
     */
    public static int uploadText(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        // 字体图集只有 alpha 有意义（笔画全是纯白，颜色在绘制时乘上去）。所以 PNG 里
        // 只存 1 个通道：L = alpha，RGB 恒为白。这样图集体积约为 RGBA 的 55%，
        // 解码时直接按「白字 + L 当透明度」展开，画面和原来逐位一致。
        if (isGrayscale(image)) {
            // 1.0.2_1：图集本来就是「白色笔画 + 单通道透明度」，没必要在显存里存成
            // RGBA。这里直接按 1 字节/像素的 GL_ALPHA 上传，显存从 4 字节/像素降到 1，
            // 15 套图集约占 309 MB → 约 77 MB。颜色由绘制时的当前颜色给出
            // （Md3Font.draw 里把 RGB 设成 PRIMARY_COLOR，A 用纹理 alpha 调制）。
            ByteBuffer buffer = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder());
            buffer.put(grayPixels(image, w, h));
            buffer.flip();
            return uploadAlpha(buffer, w, h);
        }
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            buffer.put((byte) ((p >> 16) & 0xFF));
            buffer.put((byte) ((p >> 8) & 0xFF));
            buffer.put((byte) (p & 0xFF));
            buffer.put((byte) ((p >> 24) & 0xFF));
        }
        buffer.flip();
        return uploadBuffer(buffer, w, h, false, true, GL11.GL_NEAREST);
    }

    private static boolean isGrayscale(BufferedImage image) {
        return image.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY;
    }

    /** 灰度图直接读光栅字节，省掉 getRGB 的 int[]（4096×4096 图集就是 64 MB）。 */
    private static byte[] grayPixels(BufferedImage image, int w, int h) {
        java.awt.image.Raster raster = image.getRaster();
        java.awt.image.DataBuffer db = raster.getDataBuffer();
        if (raster.getNumBands() == 1 && db instanceof java.awt.image.DataBufferByte) {
            byte[] data = ((java.awt.image.DataBufferByte) db).getData();
            if (data.length >= w * h) {
                return data;
            }
        }
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        byte[] out = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            out[i] = (byte) ((pixels[i] >> 16) & 0xFF);
        }
        return out;
    }

    public static int uploadArgb(int[] pixels, int w, int h, boolean mipmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            buffer.put((byte) ((p >> 16) & 0xFF));
            buffer.put((byte) ((p >> 8) & 0xFF));
            buffer.put((byte) (p & 0xFF));
            buffer.put((byte) ((p >> 24) & 0xFF));
        }
        buffer.flip();
        return uploadBuffer(buffer, w, h, mipmap, true);
    }

    public static int uploadAlpha(byte[] alpha, int w, int h) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < alpha.length; i++) {
            buffer.put((byte) 255);
            buffer.put((byte) 255);
            buffer.put((byte) 255);
            buffer.put(alpha[i]);
        }
        buffer.flip();
        return uploadBuffer(buffer, w, h, false, true);
    }

    /** 单通道（GL_ALPHA）图集上传：内部格式与数据格式都用 ALPHA，显存 1 字节/像素。 */
    private static int uploadAlpha(ByteBuffer buffer, int w, int h) {
        int id = GL11.glGenTextures();
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA, w, h, 0,
                GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        return id;
    }

    private static int uploadBuffer(ByteBuffer buffer, int w, int h, boolean mipmap, boolean clamp) {
        return uploadBuffer(buffer, w, h, mipmap, clamp, GL11.GL_LINEAR);
    }

    private static int uploadBuffer(ByteBuffer buffer, int w, int h, boolean mipmap, boolean clamp,
                                    int magFilter) {
        int id = GL11.glGenTextures();
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
                clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
                clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
        if (mipmap) {
            // Compatibility-profile way of asking the driver to build mips for us.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
        }
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        return id;
    }

    public static void delete(int id) {
        if (id > 0) GL11.glDeleteTextures(id);
    }

    /** Load a classpath PNG. Returns null when missing. */
    public static BufferedImage loadPng(String path) {
        try (java.io.InputStream in = Md3Gl.class.getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[MD3] missing texture " + path);
                return null;
            }
            return javax.imageio.ImageIO.read(in);
        } catch (Exception e) {
            System.err.println("[MD3] failed to read " + path + ": " + e);
            return null;
        }
    }
}
