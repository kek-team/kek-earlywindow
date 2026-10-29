/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Изменено KEK.Team: несколько диапазонов символов (кириллица, «·», «…») вместо одного ASCII, поиск глифа по карте,
 * кегль — пиксели на em с выборкой NEAREST и целым увеличением при выводе (пиксельный шрифт остаётся чётким).
 */

package team.kek.earlywindow.gl;

import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.HashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;

public class SimpleFont {
    /** Запекаемые диапазоны символов, включительно. */
    static final int[][] GLYPHS = { { 0x20, 0x7E }, { 0xB7, 0xB7 }, { 0x400, 0x45F }, { 0x2026, 0x2026 } };
    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 256;

    private final int texture;
    private final int lineHeight;
    private final Map<Character, Glyph> glyphs = new HashMap<>();

    private record Glyph(int advance, int x0, int y0, int x1, int y1, float s0, float t0, float s1, float t1) {}

    public static boolean covers(char c) {
        for (int[] range : GLYPHS) {
            if (c >= range[0] && c <= range[1]) return true;
        }
        return false;
    }

    /**
     * Запекает шрифт из ресурса {@code resource} кеглем {@code emPixels} пикселей на em.
     */
    public SimpleFont(String resource, int emPixels) {
        var buf = STBHelper.readFromClasspath(resource, 200000);
        var info = STBTTFontinfo.create();
        if (!stbtt_InitFont(info, buf)) {
            throw new IllegalStateException("Bad font");
        }
        float size = -emPixels; // отрицательный размер у stb — пиксели на em, а не высота строки
        var ascent = new float[1];
        var descent = new float[1];
        var lineGap = new float[1];
        stbtt_GetScaledFontVMetrics(buf, 0, size, ascent, descent, lineGap);
        this.lineHeight = Math.round(ascent[0] - descent[0] + lineGap[0]);

        int count = 0;
        for (int[] range : GLYPHS) count += range[1] - range[0] + 1;
        var previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        this.texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        try (var packed = STBTTPackedchar.malloc(count);
                var ranges = STBTTPackRange.malloc(GLYPHS.length);
                var pc = STBTTPackContext.malloc()) {
            int offset = 0;
            for (int[] range : GLYPHS) {
                int n = range[1] - range[0] + 1;
                ranges.get().set(size, range[0], null, n, STBTTPackedchar.create(packed.address() + (long) offset * STBTTPackedchar.SIZEOF, n), (byte) 1, (byte) 1);
                offset += n;
            }
            ranges.flip();
            var bitmap = BufferUtils.createByteBuffer(TEX_WIDTH * TEX_HEIGHT);
            stbtt_PackBegin(pc, bitmap, TEX_WIDTH, TEX_HEIGHT, 0, 1, NULL);
            stbtt_PackSetOversampling(pc, 1, 1);
            stbtt_PackSetSkipMissingCodepoints(pc, true);
            stbtt_PackFontRanges(pc, buf, 0, ranges);
            stbtt_PackEnd(pc);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, TEX_WIDTH, TEX_HEIGHT, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

            try (var q = STBTTAlignedQuad.malloc()) {
                float[] x = new float[1];
                float[] y = new float[1];
                int index = 0;
                for (int[] range : GLYPHS) {
                    for (int c = range[0]; c <= range[1]; c++, index++) {
                        x[0] = 0f;
                        y[0] = Math.round(ascent[0]);
                        stbtt_GetPackedQuad(packed, TEX_WIDTH, TEX_HEIGHT, index, x, y, q, true);
                        glyphs.put((char) c, new Glyph(Math.round(x[0]), Math.round(q.x0()), Math.round(q.y0()), Math.round(q.x1()), Math.round(q.y1()),
                                q.s0(), q.t0(), q.s1(), q.t1()));
                    }
                }
            }
        } finally {
            glBindTexture(GL_TEXTURE_2D, previous);
        }
    }

    public int texture() {
        return texture;
    }

    /** Высота строки без увеличения. */
    public int lineHeight() {
        return lineHeight;
    }

    /** Ширина строки без увеличения; незнакомые символы пропускаются. */
    public int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            var g = glyphs.get(text.charAt(i));
            if (g != null) w += g.advance();
        }
        return w;
    }

    /**
     * Добавляет в пакет {@link QuadShader#text} вершины строки: {@code (x, y)} — левый верхний угол строки,
     * {@code scale} — целое увеличение, {@code colour} — из {@link QuadShader#colour}.
     */
    public void text(SimpleBufferBuilder bb, String text, float x, float y, int scale, int colour) {
        float pen = x;
        for (int i = 0; i < text.length(); i++) {
            var g = glyphs.get(text.charAt(i));
            if (g == null) continue;
            if (g.x1() > g.x0()) {
                QuadHelper.loadQuad(bb, pen + g.x0() * scale, pen + g.x1() * scale, y + g.y0() * scale, y + g.y1() * scale,
                        g.s0(), g.s1(), g.t0(), g.t1(), colour);
            }
            pen += g.advance() * scale;
        }
    }

    public void close() {
        glDeleteTextures(texture);
    }
}
