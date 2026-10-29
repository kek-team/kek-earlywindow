/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.model;

import java.util.List;
import java.util.Map;

/**
 * Доля разлома и подпись этапа. Вся загрузка — от 0 до 1: подготовка FML — 0…0,10 по вехам, загрузка модов
 * ({@code Mod Construction}, шагов — по числу модов) — 0,10…0,50, остальное до передачи экрана Minecraft держит
 * 0,50 без доли, загрузка ресурсов Minecraft — 0,50…1. Назад фронт не идёт — это забота рисующего.
 */
public final class LoadingProgress {
    /** Снимок {@code ProgressMeter} FML: логика не зависит от классов FML. */
    public record Bar(String name, int steps, int current, String label) {}

    /** {@code known = false} — у этапа нет доли: фронт стоит, по щели ходит волна накала. */
    public record Progress(float fraction, boolean known, String text) {}

    static final float CONSTRUCTION_START = 0.10f;
    static final float CONSTRUCTION_END = 0.50f;
    private static final String EARLY = "EARLY";
    private static final String CONSTRUCTION = "Mod Construction";

    /** Вехи подготовки FML — подписи полосы {@code EARLY} по порядку. */
    private static final Map<String, Float> EARLY_MILESTONES = Map.of(
            "Loading bootstrap resources", 0.01f,
            "Discovering mod files", 0.03f,
            "Scanning mod candidates", 0.05f,
            "Loading language providers", 0.08f,
            "Launching minecraft", 0.09f,
            "Initializing Game Graphics", 0.10f);

    private LoadingProgress() {}

    public static Progress ofFml(List<Bar> bars) {
        if (bars.isEmpty()) return new Progress(0f, false, StageLabels.ru(EARLY));
        var last = bars.getLast();
        if (EARLY.equals(last.name())) {
            var label = last.label();
            var text = StageLabels.knows(label) ? StageLabels.ru(label) : StageLabels.ru(EARLY);
            return new Progress(earlyFraction(label), false, text);
        }
        if (CONSTRUCTION.equals(last.name()) && last.steps() > 0) {
            float share = Math.clamp(last.current() / (float) last.steps(), 0f, 1f);
            var text = StageLabels.ru(CONSTRUCTION) + " · " + last.current() + " / " + last.steps();
            return new Progress(CONSTRUCTION_START + (CONSTRUCTION_END - CONSTRUCTION_START) * share, true, text);
        }
        return new Progress(CONSTRUCTION_END, false, StageLabels.ru(last.name()));
    }

    public static Progress ofReload(float reloadProgress) {
        float p = Math.clamp(reloadProgress, 0f, 1f);
        return new Progress(CONSTRUCTION_END + (1f - CONSTRUCTION_END) * p, true, "Загрузка ресурсов");
    }

    private static float earlyFraction(String label) {
        if (label == null) return 0f;
        if (label.startsWith("Found ")) return 0.07f;
        if (label.startsWith("Loaded language provider ")) return 0.08f;
        return EARLY_MILESTONES.getOrDefault(label, 0f);
    }
}
