package team.kek.earlywindow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SceneTimelineTest {
    @Test
    void opensFromFlashOfStepIn() {
        // первые 0,4 с гаснет вспышка — продолжение «шага в портал» лаунчера
        assertEquals(1f, SceneTimeline.at(0, 0, -1).flash(), 1e-3f);
        assertEquals(0f, SceneTimeline.at(0.4, 0, -1).flash(), 1e-3f);
        assertEquals(1f, SceneTimeline.at(0, 0, -1).open(), 0f);
        assertEquals(1f, SceneTimeline.at(0, 0, -1).appear(), 0f);
    }

    @Test
    void collapseClosesPortalToPoint() {
        var u = SceneTimeline.at(10, 1f, -1);
        assertEquals(0f, u.open(), 0f);
        assertEquals(0f, u.appear(), 0f);
    }

    @Test
    void flashOverrideWins() {
        assertEquals(0.7f, SceneTimeline.at(10, 1f, 0.7f).flash(), 0f);
    }

    @Test
    void flashStaysVisibleAfterCollapse() {
        // appear гасит весь кадр шейдера — вспышку после схлопывания нельзя гасить вместе с порталом
        assertEquals(0.7f, SceneTimeline.at(10, 1f, 0.7f).appear(), 0f);
    }

    @Test
    void timeIsSecondsSinceWindowOpened() {
        assertEquals(12.5f, SceneTimeline.at(12.5, 0, -1).time(), 1e-6f);
    }
}
