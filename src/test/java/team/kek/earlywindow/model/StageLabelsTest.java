package team.kek.earlywindow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Имена полос и подписи — из FML 4.0.45 (ImmediateWindowHandler, ModLoader, загрузчик) и NeoForge 21.1 (CommonModLoader). */
class StageLabelsTest {
    private static void check(String expected, String fml) {
        assertEquals(expected, StageLabels.ru(fml), fml);
    }

    @Test
    void translatesKnownStages() {
        check("Подготовка", "EARLY");
        check("Загрузка модов", "Mod Construction");
        check("Загрузка…", "Totally unknown");
    }

    @Test
    void earlyLabels() {
        check("Подготовка", "Bootstrapping Minecraft");
        check("Подготовка", "Loading bootstrap resources");
        check("Поиск модов", "Discovering mod files");
        check("Поиск модов", "Scanning mod candidates");
        check("Поиск модов", "Found 112 mod candidates");
        check("Подготовка", "Loading language providers");
        check("Подготовка", "Loaded language provider javafml 4.0");
        check("Запуск Minecraft", "Launching minecraft");
        check("Запуск Minecraft", "Initializing Game Graphics");
    }

    @Test
    void modLoaderStages() {
        check("Загрузка модов", "Mod Construction: Deferred Queue");
        check("Реестры", "Registry initialization");
        check("Настройки модов", "Config loading");
        check("Регистрация блоков и предметов", "Registration events");
        check("Подготовка сети", "Network registry lock");
        check("Настройка модов", "Common setup");
        check("Настройка модов", "Sided setup: Deferred Queue");
        check("Связь модов", "Enqueue IMC");
        check("Связь модов", "Process IMC");
        check("Завершение загрузки", "Complete loading of 112 mods");
    }

    @Test
    void emptyOrNullIsGeneric() {
        check("Загрузка…", "");
        check("Загрузка…", null);
    }
}
