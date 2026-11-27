package net.client.replay.render;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Small baseline JPEG encoder (4:4:4, no AWT/ImageIO).  It exists so the
 * MJPEG AVI exporter can produce a real video without ffmpeg and without
 * relying on Android's Bitmap APIs.
 */
public final class JpegEncoder {
    private static final int[] ZIGZAG = {
            0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63
    };

    private static final int[] LUMA_Q = {
            16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99
    };
    private static final int[] CHROMA_Q = {
            17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99,
            24, 26, 56, 99, 99, 99, 99, 99, 47, 66, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99
    };

    private static final int[] DC_LUMA_BITS = {0, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] DC_LUMA_VALS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[] DC_CHROMA_BITS = {0, 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0};
    private static final int[] DC_CHROMA_VALS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[] AC_LUMA_BITS = {0, 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d};
    private static final int[] AC_LUMA_VALS = {
            0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
            0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08, 0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
            0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
            0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
            0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
            0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
            0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
            0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
            0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
            0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
            0xf9, 0xfa
    };
    private static final int[] AC_CHROMA_BITS = {0, 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77};
    private static final int[] AC_CHROMA_VALS = {
            0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
            0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91, 0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
            0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34, 0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
            0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
            0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
            0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
            0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
            0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
            0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
            0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
            0xf9, 0xfa
    };

    private static final double[][] COS = new double[8][8];
    private static final int[][] DC_LUMA = buildCodes(DC_LUMA_BITS, DC_LUMA_VALS);
    private static final int[][] AC_LUMA = buildCodes(AC_LUMA_BITS, AC_LUMA_VALS);
    private static final int[][] DC_CHROMA = buildCodes(DC_CHROMA_BITS, DC_CHROMA_VALS);
    private static final int[][] AC_CHROMA = buildCodes(AC_CHROMA_BITS, AC_CHROMA_VALS);

    static {
        for (int u = 0; u < 8; u++) {
            for (int x = 0; x < 8; x++) {
                COS[u][x] = Math.cos((2 * x + 1) * u * Math.PI / 16.0D);
            }
        }
    }

    private JpegEncoder() {
    }

    public static byte[] encode(ByteBuffer rgba, int width, int height, int quality) throws IOException {
        int q = Math.max(1, Math.min(100, quality));
        int scale = q < 50 ? 5000 / q : 200 - 2 * q;
        int[] luma = scaled(LUMA_Q, scale);
        int[] chroma = scaled(CHROMA_Q, scale);

        int paddedW = (width + 7) & ~7;
        int paddedH = (height + 7) & ~7;
        byte[] yPlane = new byte[paddedW * paddedH];
        byte[] cbPlane = new byte[paddedW * paddedH];
        byte[] crPlane = new byte[paddedW * paddedH];
        rgba.rewind();
        // glReadPixels 的行序是自下而上（OpenGL 原点在左下），JPEG 的行序是自上而下。
        // 1.0.2_18：这里补上翻转，否则 MJPEG AVI 导出的整段视频是倒的。
        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int p = (srcY * width + x) * 4;
                int r = rgba.get(p) & 0xFF;
                int g = rgba.get(p + 1) & 0xFF;
                int b = rgba.get(p + 2) & 0xFF;
                int Y = clamp((int) (0.299D * r + 0.587D * g + 0.114D * b));
                int Cb = clamp((int) (-0.168736D * r - 0.331264D * g + 0.5D * b + 128));
                int Cr = clamp((int) (0.5D * r - 0.418688D * g - 0.081312D * b + 128));
                int idx = y * paddedW + x;
                yPlane[idx] = (byte) Y;
                cbPlane[idx] = (byte) Cb;
                crPlane[idx] = (byte) Cr;
            }
        }
        // Edge extension: valid JPEG input for non-multiple-of-8 dimensions.
        for (int y = 0; y < paddedH; y++) {
            for (int x = width; x < paddedW; x++) {
                int idx = y * paddedW + x;
                int src = y * paddedW + Math.max(0, width - 1);
                yPlane[idx] = yPlane[src];
                cbPlane[idx] = cbPlane[src];
                crPlane[idx] = crPlane[src];
            }
        }
        for (int y = height; y < paddedH; y++) {
            for (int x = 0; x < paddedW; x++) {
                int idx = y * paddedW + x;
                int src = Math.max(0, height - 1) * paddedW + x;
                yPlane[idx] = yPlane[src];
                cbPlane[idx] = cbPlane[src];
                crPlane[idx] = crPlane[src];
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(width * height / 2);
        writeMarker(out, 0xD8);
        writeMarker(out, 0xE0);
        writeWord(out, 16);
        out.write(new byte[]{'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0});
        writeMarker(out, 0xDB);
        writeWord(out, 132);
        out.write(0);
        for (int i = 0; i < 64; i++) out.write(luma[ZIGZAG[i]]);
        out.write(1);
        for (int i = 0; i < 64; i++) out.write(chroma[ZIGZAG[i]]);

        writeMarker(out, 0xC0);
        writeWord(out, 17);
        out.write(8);
        writeWord(out, height);
        writeWord(out, width);
        out.write(3);
        out.write(1); out.write(0x11); out.write(0);
        out.write(2); out.write(0x11); out.write(1);
        out.write(3); out.write(0x11); out.write(1);

        writeMarker(out, 0xC4);
        writeWord(out, 2 + (1 + 16 + DC_LUMA_VALS.length) + (1 + 16 + AC_LUMA_VALS.length)
                + (1 + 16 + DC_CHROMA_VALS.length) + (1 + 16 + AC_CHROMA_VALS.length));
        writeHuffman(out, 0x00, DC_LUMA_BITS, DC_LUMA_VALS);
        writeHuffman(out, 0x10, AC_LUMA_BITS, AC_LUMA_VALS);
        writeHuffman(out, 0x01, DC_CHROMA_BITS, DC_CHROMA_VALS);
        writeHuffman(out, 0x11, AC_CHROMA_BITS, AC_CHROMA_VALS);

        writeMarker(out, 0xDA);
        writeWord(out, 12);
        out.write(3);
        out.write(1); out.write(0x00);
        out.write(2); out.write(0x11);
        out.write(3); out.write(0x11);
        out.write(0); out.write(63); out.write(0);

        BitWriter bits = new BitWriter(out);
        int[] previous = new int[3];
        int[] block = new int[64];
        int[] temp = new int[64];
        for (int by = 0; by < paddedH; by += 8) {
            for (int bx = 0; bx < paddedW; bx += 8) {
                encodeBlock(yPlane, paddedW, bx, by, luma, block, temp, bits, DC_LUMA, AC_LUMA, previous, 0);
                encodeBlock(cbPlane, paddedW, bx, by, chroma, block, temp, bits, DC_CHROMA, AC_CHROMA, previous, 1);
                encodeBlock(crPlane, paddedW, bx, by, chroma, block, temp, bits, DC_CHROMA, AC_CHROMA, previous, 2);
            }
        }
        bits.flush();
        writeMarker(out, 0xD9);
        return out.toByteArray();
    }

    private static void encodeBlock(byte[] plane, int stride, int bx, int by, int[] quant,
                                    int[] block, int[] temp, BitWriter bits,
                                    int[][] dc, int[][] ac, int[] previous, int component) throws IOException {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                // 1.0.2_18：plane 是 byte[]，直接减 128 会先把 ≥128 的样本符号扩展成负数
                // （255 -> -1 -> -129），所有亮部与 Cb/Cr 高的像素都会被算错，
                // 表现就是导出视频整体发绿。必须先取无符号值。
                block[y * 8 + x] = (plane[(by + y) * stride + bx + x] & 0xFF) - 128;
            }
        }
        for (int v = 0; v < 8; v++) {
            for (int u = 0; u < 8; u++) {
                double sum = 0.0D;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        sum += block[y * 8 + x] * COS[u][x] * COS[v][y];
                    }
                }
                double c = (u == 0 ? 1.0D / Math.sqrt(2.0D) : 1.0D)
                        * (v == 0 ? 1.0D / Math.sqrt(2.0D) : 1.0D);
                temp[v * 8 + u] = (int) Math.round(0.25D * c * sum / quant[v * 8 + u]);
            }
        }
        int diff = temp[0] - previous[component];
        previous[component] = temp[0];
        int category = bitsFor(Math.abs(diff));
        writeHuffmanBits(bits, dc, category);
        if (category > 0) bits.write(amplitude(diff, category), category);
        int run = 0;
        for (int i = 1; i < 64; i++) {
            int value = temp[ZIGZAG[i]];
            if (value == 0) {
                run++;
            } else {
                while (run > 15) {
                    writeHuffmanBits(bits, ac, 0xF0);
                    run -= 16;
                }
                int size = bitsFor(Math.abs(value));
                writeHuffmanBits(bits, ac, (run << 4) | size);
                bits.write(amplitude(value, size), size);
                run = 0;
            }
        }
        if (run > 0) writeHuffmanBits(bits, ac, 0x00);
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(255, value);
    }

    private static int bitsFor(int value) {
        int bits = 0;
        while (value != 0) {
            bits++;
            value >>= 1;
        }
        return bits;
    }

    private static int amplitude(int value, int size) {
        return value >= 0 ? value : value + (1 << size) - 1;
    }

    private static int[] scaled(int[] base, int scale) {
        int[] out = new int[64];
        for (int i = 0; i < 64; i++) {
            out[i] = Math.max(1, Math.min(255, (base[i] * scale + 50) / 100));
        }
        return out;
    }

    private static int[][] buildCodes(int[] bits, int[] values) {
        int[][] result = new int[256][2];
        int code = 0;
        int k = 0;
        for (int len = 1; len <= 16; len++) {
            for (int i = 0; i < bits[len]; i++) {
                result[values[k]][0] = code;
                result[values[k]][1] = len;
                code++;
                k++;
            }
            code <<= 1;
        }
        return result;
    }

    private static void writeHuffmanBits(BitWriter out, int[][] table, int symbol) throws IOException {
        int[] entry = table[symbol];
        out.write(entry[0], entry[1]);
    }

    private static void writeHuffman(ByteArrayOutputStream out, int id, int[] bits, int[] values) throws IOException {
        out.write(id);
        for (int i = 1; i <= 16; i++) out.write(bits[i]);
        for (int value : values) out.write(value);
    }

    private static void writeMarker(ByteArrayOutputStream out, int marker) throws IOException {
        out.write(0xFF);
        out.write(marker);
    }

    private static void writeWord(ByteArrayOutputStream out, int value) throws IOException {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static final class BitWriter {
        private final ByteArrayOutputStream out;
        private int buffer;
        private int count;

        BitWriter(ByteArrayOutputStream out) {
            this.out = out;
        }

        void write(int value, int length) throws IOException {
            for (int i = length - 1; i >= 0; i--) {
                buffer = (buffer << 1) | ((value >> i) & 1);
                count++;
                if (count == 8) emit(buffer);
            }
        }

        void flush() throws IOException {
            if (count > 0) {
                int value = buffer << (8 - count);
                value |= (1 << (8 - count)) - 1;
                emit(value);
            }
        }

        private void emit(int value) throws IOException {
            out.write(value & 0xFF);
            if ((value & 0xFF) == 0xFF) out.write(0);
            buffer = 0;
            count = 0;
        }
    }
}
