/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.model;

import java.util.List;
import java.util.Map;

/**
 * Имя полосы прогресса FML или её подпись → текст для игрока. Незнакомое — «Загрузка…», а не английский текст.
 * Имена — из FML 4.0.45 и NeoForge 21.1; новые версии могут добавить свои, тогда игрок увидит «Загрузка…».
 */
public final class StageLabels {
    public static final String GENERIC = "Загрузка…";
    private static final String DEFERRED = ": Deferred Queue";

    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("EARLY", "Подготовка"),
            Map.entry("Bootstrapping Minecraft", "Подготовка"),
            Map.entry("Loading bootstrap resources", "Подготовка"),
            Map.entry("Loading language providers", "Подготовка"),
            Map.entry("Discovering mod files", "Поиск модов"),
            Map.entry("Scanning mod candidates", "Поиск модов"),
            Map.entry("Launching minecraft", "Запуск Minecraft"),
            Map.entry("Initializing Game Graphics", "Запуск Minecraft"),
            Map.entry("Mod Construction", "Загрузка модов"),
            Map.entry("Registry initialization", "Реестры"),
            Map.entry("Config loading", "Настройки модов"),
            Map.entry("Registration events", "Регистрация блоков и предметов"),
            Map.entry("Network registry lock", "Подготовка сети"),
            Map.entry("Common setup", "Настройка модов"),
            Map.entry("Sided setup", "Настройка модов"),
            Map.entry("Enqueue IMC", "Связь модов"),
            Map.entry("Process IMC", "Связь модов"));

    /** Подписи с числами и именами внутри: сверяются по началу. */
    private static final List<Map.Entry<String, String>> PREFIX = List.of(
            Map.entry("Found ", "Поиск модов"),
            Map.entry("Loaded language provider ", "Подготовка"),
            Map.entry("Complete loading of ", "Завершение загрузки"));

    private StageLabels() {}

    public static String ru(String fml) {
        if (fml == null) return GENERIC;
        var name = fml.endsWith(DEFERRED) ? fml.substring(0, fml.length() - DEFERRED.length()) : fml;
        var exact = EXACT.get(name);
        if (exact != null) return exact;
        for (var p : PREFIX) {
            if (name.startsWith(p.getKey())) return p.getValue();
        }
        return GENERIC;
    }

    static boolean knows(String fml) {
        return !GENERIC.equals(ru(fml));
    }
}
