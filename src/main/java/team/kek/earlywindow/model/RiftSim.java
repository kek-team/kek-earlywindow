/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Перенос {@code ui/Rift.kt} лаунчера для загрузки: огонь до фронта, видимый фронт догоняет настоящий плавно и назад
 * не идёт, с фронта летят искры, впереди щель накалена. Координаты искр — в «пикселях» разлома.
 */
public final class RiftSim {
    public static final int WHITE_HOT = 0xFFF3D6;
    public static final int SPARK = 0xFFC93C;
    public static final int EMBER = 0xFF6A13;
    /** Холодная щель. */
    public static final int CORE = 0x0A070E;
    static final float EASE_S = 0.35f;
    static final int PREHEAT_REACH = 14;
    private static final float GRAVITY = 70f;
    private static final double MAX_STEP_S = 0.05;

    public static final class Ember {
        public float x;
        public float y;
        float vx;
        float vy;
        final float max;
        float life;

        Ember(float x, float y, float vx, float vy, float max) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.max = max;
        }

        /** Доля прожитого, 0…1. */
        public float age() {
            return life / max;
        }
    }

    private final Random random;
    private final List<Ember> sparks = new ArrayList<>();
    private float time;
    private float target;
    private float shown;

    public RiftSim(Random random) {
        this.random = random;
    }

    /**
     * Шаг на {@code dt} секунд (не длиннее 50 мс: после паузы время не прыгает) к доле {@code lit}; {@code known} —
     * у этапа есть доля. {@code cols} — столбцов в нынешней раскладке.
     */
    public void advance(double dt, float lit, boolean known, int cols) {
        float step = (float) Math.min(dt, MAX_STEP_S);
        if (step <= 0f) return;
        time += step;
        target = Math.max(target, Math.clamp(lit, 0f, 1f));
        shown = easeLit(shown, target, step);
        var it = sparks.iterator();
        while (it.hasNext()) {
            var s = it.next();
            s.life += step;
            if (s.life >= s.max) {
                it.remove();
                continue;
            }
            s.vy += GRAVITY * step;
            s.x += s.vx * step;
            s.y += s.vy * step;
        }
        int front = litColumns(shown, cols);
        if (front < cols && random.nextFloat() < step * 50f) {
            sparks.add(new Ember(front, RiftLayout.SLIT_ROW - 1f, rnd(-14f, 30f), rnd(-34f, -8f), rnd(0.25f, 0.7f)));
        }
    }

    /** Видимая доля огня. */
    public float lit() {
        return shown;
    }

    /** Настоящая доля — наибольшая из пришедших. */
    public float target() {
        return target;
    }

    public float time() {
        return time;
    }

    public List<Ember> sparks() {
        return Collections.unmodifiableList(sparks);
    }

    private float rnd(float a, float b) {
        return a + random.nextFloat() * (b - a);
    }

    /** Сколько столбцов из {@code total} залито при доле {@code fraction}: огонь не опережает прогресс. */
    public static int litColumns(float fraction, int total) {
        return Math.clamp((int) Math.floor(Math.clamp(fraction, 0f, 1f) * total), 0, total);
    }

    /** Видимая доля через {@code dt} секунд: вперёд догоняет экспоненциально, вплотную встаёт точно на цель. */
    public static float easeLit(float shown, float target, float dt) {
        if (target <= shown) return target;
        float next = target - (target - shown) * (float) Math.exp(-dt / EASE_S);
        return target - next < 0.0005f ? target : next;
    }

    /** Цвет огня по расстоянию от фронта: фронт добела, за ним искра, дальше остывает до жара. */
    public static int heatColor(int behind) {
        if (behind <= 1) return WHITE_HOT;
        if (behind <= 6) return lerp(WHITE_HOT, SPARK, (behind - 1) / 5f);
        if (behind <= 40) return lerp(SPARK, EMBER, (behind - 6) / 34f);
        return EMBER;
    }

    /** Отрезок впереди фронта, по которому ходит волна накала у этапа без доли. */
    static int waveSpan(int cols) {
        return Math.max(12, cols / 10);
    }

    /**
     * Накал щели в столбце {@code col} при фронте {@code front}, 0…1: с долей — короткий хвост за фронтом,
     * без доли — мягкая волна по отрезку {@link #waveSpan} впереди. Позади фронта — 0.
     */
    public static float preheat(int col, int front, boolean known, float t, int cols) {
        int d = col - front;
        if (d < 0) return 0f;
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * 6f + col * 1.7f);
        if (known) return d >= PREHEAT_REACH ? 0f : 0.5f * (1f - d / (float) PREHEAT_REACH) * flicker;
        int span = waveSpan(cols);
        if (d >= span) return 0f;
        float pos = front + span * (0.5f - 0.5f * (float) Math.cos(t * 1.4f));
        float wave = Math.max(0f, 1f - Math.abs(col - pos) / 12f);
        return (0.12f + 0.45f * wave * wave) * flicker;
    }

    /** Смесь цветов {@code 0xRRGGBB}. */
    public static int lerp(int a, int b, float t) {
        float k = Math.clamp(t, 0f, 1f);
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * k);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return r << 16 | g << 8 | bl;
    }
}
