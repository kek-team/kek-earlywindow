/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.model;

/**
 * Перенос {@code ui/scene/SceneParticles.kt} лаунчера: частицы считаются на CPU раз за кадр. Массив — {@link #COUNT}
 * записей по {@link #STRIDE} float: x, y (доли меньшей стороны от центра окна), яркость 0…1, цвет (0 — пурпурный,
 * 1 — бирюзовый). Зёрна — тем же генератором, что в лаунчере, поэтому после вспышки частицы стоят там же.
 * Заряда, курсора и подъёма вспышки на раннем экране нет.
 */
public final class SceneParticles {
    public static final int COUNT = 48;
    public static final int STRIDE = 4;

    private static final float[][] SEEDS = new float[COUNT][];

    static {
        for (int i = 0; i < COUNT; i++) {
            var r = new KotlinRandom(31 * i + 7);
            SEEDS[i] = new float[] { r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() };
        }
    }

    private SceneParticles() {}

    /**
     * При закрытом портале частицы втягиваются по спирали в ядро, после раскрытия дрейфуют по окну; вспышка
     * отталкивает их от центра.
     */
    public static void fill(float[] out, float time, float open, float pulse, float flash) {
        float settle = SceneTimeline.smoothstep(0.3f, 1f, open);
        for (int i = 0; i < COUNT; i++) {
            float[] s = SEEDS[i];
            float h1 = s[0];
            float h2 = s[1];
            float h3 = s[2];
            float h4 = s[3];
            float phase = fract(h3 + time * 0.12f * (0.5f + h4));
            float rr = lerp(0.15f + 0.9f * h2, 0.02f, phase);
            float ang = 6.2832f * h1 + phase * 4f + time * 0.3f;
            float x = lerp(rr * (float) Math.cos(ang), (h1 - 0.5f) * 1.8f + 0.04f * (float) Math.sin(time * 0.3f + i), settle);
            float y = lerp(rr * (float) Math.sin(ang), (h2 - 0.5f) * 1.1f + 0.04f * (float) Math.cos(time * 0.23f + i * 1.3f), settle);
            float len = (float) Math.hypot(x, y);
            if (len > 0f) {
                x += x / len * flash * 0.15f;
                y += y / len * flash * 0.15f;
            }
            float twinkle = 0.55f + 0.45f * (float) Math.sin(time * 2f + h3 * 6.2832f);
            float visible = lerp(SceneTimeline.smoothstep(0f, 0.12f, rr) * (0.3f + 0.7f * pulse), 1f, settle);
            out[i * STRIDE] = x;
            out[i * STRIDE + 1] = y;
            out[i * STRIDE + 2] = twinkle * visible;
            out[i * STRIDE + 3] = h4 > 0.85f ? 1f : 0f;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    /** {@code kotlin.random.Random(seed)} — XorWow из kotlin-stdlib, чтобы зёрна совпали с лаунчером. */
    static final class KotlinRandom {
        private int x;
        private int y;
        private int z;
        private int w;
        private int v;
        private int addend;

        KotlinRandom(int seed) {
            int seed2 = seed >> 31;
            x = seed;
            y = seed2;
            z = 0;
            w = 0;
            v = ~seed;
            addend = (seed << 10) ^ (seed2 >>> 4);
            for (int i = 0; i < 64; i++) nextInt();
        }

        int nextInt() {
            int t = x;
            t = t ^ (t >>> 2);
            x = y;
            y = z;
            z = w;
            int v0 = v;
            w = v0;
            t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4);
            v = t;
            addend += 362437;
            return t + addend;
        }

        float nextFloat() {
            return (nextInt() >>> 8) / (float) (1 << 24);
        }
    }
}
