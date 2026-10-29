package team.kek.earlywindow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static team.kek.earlywindow.model.LoadingProgress.ofFml;
import static team.kek.earlywindow.model.LoadingProgress.ofReload;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.kek.earlywindow.model.LoadingProgress.Bar;

class LoadingProgressTest {
    @Test
    void beforeConstructionIsEarly() {
        var p = ofFml(List.of(new Bar("EARLY", 0, 0, "Loading mods")));
        assertEquals(0f, p.fraction(), 1e-3f);
        assertFalse(p.known());
        assertEquals("Подготовка", p.text());
    }

    @Test
    void earlyMilestonesFillFirstTenth() {
        var discovering = ofFml(List.of(new Bar("EARLY", 0, 0, "Discovering mod files")));
        var graphics = ofFml(List.of(new Bar("EARLY", 0, 0, "Initializing Game Graphics")));
        assertTrue(discovering.fraction() > 0f && discovering.fraction() < graphics.fraction());
        assertEquals(0.10f, graphics.fraction(), 1e-3f);
        assertEquals("Поиск модов", discovering.text());
    }

    @Test
    void constructionIsProportional() {
        var p = ofFml(List.of(new Bar("EARLY", 0, 0, "Initializing Game Graphics"), new Bar("Mod Construction", 356, 178, "Mod Construction")));
        assertEquals(0.30f, p.fraction(), 1e-3f);
        assertTrue(p.known());
        assertEquals("Загрузка модов · 178 / 356", p.text());
    }

    @Test
    void unknownStageHoldsWithoutShare() {
        var p = ofFml(List.of(new Bar("Mod Construction", 356, 356, ""), new Bar("Something new", 0, 0, "Doing things")));
        assertEquals(0.50f, p.fraction(), 1e-3f);
        assertFalse(p.known());
        assertEquals("Загрузка…", p.text());
    }

    @Test
    void stagesAfterConstructionHoldHalf() {
        var p = ofFml(List.of(new Bar("EARLY", 0, 0, "Initializing Game Graphics"), new Bar("Registration events", 0, 0, "Registration events")));
        assertEquals(0.50f, p.fraction(), 1e-3f);
        assertFalse(p.known());
        assertEquals("Регистрация блоков и предметов", p.text());
    }

    @Test
    void noBarsIsEarly() {
        assertEquals(0f, ofFml(List.of()).fraction(), 0f);
    }

    @Test
    void reloadMapsToSecondHalf() {
        assertEquals(0.75f, ofReload(0.5f).fraction(), 1e-3f);
        assertEquals(1f, ofReload(1f).fraction(), 1e-3f);
        assertTrue(ofReload(0.5f).known());
        assertEquals("Загрузка ресурсов", ofReload(0.5f).text());
    }
}
