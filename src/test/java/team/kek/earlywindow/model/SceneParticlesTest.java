package team.kek.earlywindow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Те же ожидания, что у ui/scene/SceneParticlesTest.kt лаунчера. */
class SceneParticlesTest {
    private static float[] frame(float open, float pulse, float flash) {
        var out = new float[SceneParticles.COUNT * SceneParticles.STRIDE];
        SceneParticles.fill(out, 3f, open, pulse, flash);
        return out;
    }

    private static double radius(float[] p, int i) {
        return Math.hypot(p[i * 4], p[i * 4 + 1]);
    }

    @Test
    void seedsMatchKotlinRandomOfLauncher() {
        // kotlin.random.Random(7) и Random(31 * 47 + 7): первые nextFloat() — значения из kotlin-stdlib
        var r = new SceneParticles.KotlinRandom(7);
        assertEquals(0.95755213f, r.nextFloat(), 0f);
        assertEquals(0.0027709007f, r.nextFloat(), 0f);
        assertEquals(0.5479703f, r.nextFloat(), 0f);
        assertEquals(0.37255895f, r.nextFloat(), 0f);
        var last = new SceneParticles.KotlinRandom(31 * 47 + 7);
        assertEquals(0.9424919f, last.nextFloat(), 0f);
        assertEquals(0.7434639f, last.nextFloat(), 0f);
    }

    @Test
    void brightnessInRangeAndColourIsFlag() {
        for (var p : new float[][] { frame(0, 1, 0), frame(0.5f, 0.4f, 0), frame(1, 0, 0) }) {
            for (int i = 0; i < SceneParticles.COUNT; i++) {
                float b = p[i * 4 + 2];
                assertTrue(b >= 0f && b <= 1f, "яркость " + i + ": " + b);
                assertTrue(p[i * 4 + 3] == 0f || p[i * 4 + 3] == 1f);
            }
        }
    }

    @Test
    void closedPortalKeepsParticlesOnSpiral() {
        var p = frame(0, 1, 0);
        for (int i = 0; i < SceneParticles.COUNT; i++) assertTrue(radius(p, i) <= 1.05 + 1e-3, "частица " + i);
    }

    @Test
    void openPortalScattersParticles() {
        var p = frame(1, 0, 0);
        for (int i = 0; i < SceneParticles.COUNT; i++) {
            assertTrue(Math.abs(p[i * 4]) <= 0.95f, "x " + i);
            assertTrue(Math.abs(p[i * 4 + 1]) <= 0.6f, "y " + i);
        }
    }

    @Test
    void flashPushesParticlesOut() {
        var calm = frame(1, 0, 0);
        var flash = frame(1, 0, 1);
        for (int i = 0; i < SceneParticles.COUNT; i++) assertEquals(radius(calm, i) + 0.15, radius(flash, i), 1e-3);
    }
}
