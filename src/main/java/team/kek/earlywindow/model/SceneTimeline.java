/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.model;

/**
 * Сцена раннего окна по времени: окно открывается сразу после вспышки «шага в портал» лаунчера — портал раскрыт,
 * вспышка догорает {@link #FLASH_FADE_S}. В конце загрузки портал схлопывается ({@code collapse} 0 → 1), и
 * вспышку ведёт оверлей Minecraft ({@code flashOverride}).
 */
public final class SceneTimeline {
    static final double FLASH_FADE_S = 0.4;

    private SceneTimeline() {}

    /**
     * @param seconds       время с открытия окна
     * @param collapse      0 — портал раскрыт, 1 — схлопнулся в точку
     * @param flashOverride вспышка 0…1 от оверлея; отрицательное — своя, догорающая после открытия окна
     */
    public static SceneUniforms at(double seconds, float collapse, float flashOverride) {
        float flash = flashOverride >= 0f ? flashOverride : 1f - smoothstep(0f, (float) FLASH_FADE_S, (float) seconds);
        float open = 1f - clamp01(collapse);
        // appear гасит весь кадр шейдера, поэтому вспышку после схлопывания он не гасит
        float appear = Math.max(open, flashOverride >= 0f ? flashOverride : 0f);
        return new SceneUniforms((float) seconds, open, appear, flash);
    }

    static float clamp01(float v) {
        return Math.clamp(v, 0f, 1f);
    }

    static float smoothstep(float e0, float e1, float v) {
        float t = clamp01((v - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }
}
