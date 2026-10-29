package team.kek.earlywindow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Утверждения ui/RiftTest.kt лаунчера, относящиеся к загрузке, и свои. */
class RiftSimTest {
    private static final int COLS = 420;

    @Test
    void fireCatchesUpInsteadOfJumping() {
        float target = 2f / 7;
        float shown = 1f / 7;
        float first = RiftSim.easeLit(shown, target, 1f / 60);
        assertTrue(first > shown && first < target, "за кадр — только часть пути");
        for (int i = 0; i < 60 * 3; i++) shown = RiftSim.easeLit(shown, target, 1f / 60);
        assertEquals(target, shown, "через пару секунд встаёт точно на цель");
    }

    @Test
    void fireNeverOutrunsProgress() {
        assertEquals(0, RiftSim.litColumns(0f, 400));
        assertEquals(399, RiftSim.litColumns(0.999f, 400), "весь разлом — только на 1");
        assertEquals(400, RiftSim.litColumns(1f, 400));
        assertEquals(0, RiftSim.litColumns(-1f, 400));
    }

    @Test
    void frontIsWhiteHotTailCoolsToEmber() {
        assertEquals(RiftSim.WHITE_HOT, RiftSim.heatColor(0));
        assertEquals(RiftSim.SPARK, RiftSim.heatColor(6));
        assertEquals(RiftSim.EMBER, RiftSim.heatColor(40));
        assertEquals(RiftSim.EMBER, RiftSim.heatColor(300));
    }

    @Test
    void slitAheadOfFrontIsPreheated() {
        int front = RiftSim.litColumns(0.3f, COLS);
        for (float t : new float[] { 0f, 0.7f, 3.1f }) {
            assertTrue(RiftSim.preheat(front, front, true, t, COLS) > 0.3f, "у фронта жарко");
            assertEquals(0f, RiftSim.preheat(front + RiftSim.PREHEAT_REACH, front, true, t, COLS));
            assertEquals(0f, RiftSim.preheat(front - 1, front, true, t, COLS), "позади фронта — огонь, не накал");
        }
    }

    @Test
    void stageWithoutShareGlowsOnlyAhead() {
        int front = RiftSim.litColumns(0.5f, COLS);
        int end = front + RiftSim.waveSpan(COLS);
        for (float t : new float[] { 0f, 1.1f, 2.2f }) {
            final float tt = t;
            assertTrue(IntStream.range(front, end).allMatch(c -> RiftSim.preheat(c, front, false, tt, COLS) > 0f), "весь отрезок тлеет");
            assertEquals(0f, RiftSim.preheat(end, front, false, t, COLS), "дальше накала нет");
        }
    }

    @Test
    void frontNeverMovesBackward() {
        var sim = new RiftSim(new Random(1));
        sim.advance(0.1, 0.6f, true, COLS);
        float shown = sim.lit();
        sim.advance(0.1, 0.4f, true, COLS);
        assertEquals(0.6f, sim.target(), 1e-6f);
        assertTrue(sim.lit() >= shown, "видимый огонь не отступает");
    }

    @Test
    void sparksFlyFromFrontAndDie() {
        var sim = new RiftSim(new Random(2));
        for (int i = 0; i < 60; i++) sim.advance(0.016, 0.5f, true, COLS);
        assertFalse(sim.sparks().isEmpty(), "с фронта летят искры");
        for (int i = 0; i < 200; i++) sim.advance(0.016, 1f, true, COLS);
        assertTrue(sim.sparks().isEmpty(), "разлом залит целиком — искры кончились, живут меньше секунды");
    }

    @Test
    void stepIsCappedAfterPause() {
        var sim = new RiftSim(new Random(3));
        sim.advance(60, 0.5f, true, COLS);
        assertEquals(0.05f, sim.time(), 1e-6f);
    }
}
