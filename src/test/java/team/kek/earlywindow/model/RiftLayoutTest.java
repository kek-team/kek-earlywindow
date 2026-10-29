package team.kek.earlywindow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RiftLayoutTest {
    @Test
    void pixelsAreWholeAtAnySize() {
        for (int[] size : new int[][] { { 854, 480 }, { 1280, 720 }, { 1920, 1080 }, { 2576, 1369 }, { 3840, 2160 }, { 1001, 577 } }) {
            var l = RiftLayout.of(size[0], size[1]);
            assertTrue(l.px() >= 2, "пиксель разлома не меньше 2");
            assertTrue(l.left() >= 0 && l.right() <= size[0], "разлом помещается в ширину");
            assertEquals(l.cols() * l.px(), l.right() - l.left());
            assertTrue(l.y(0) >= 0 && l.y(RiftLayout.ROWS) <= size[1], "разлом помещается в высоту");
        }
    }

    @Test
    void relayoutOnResize() {
        var a = RiftLayout.of(854, 480);
        var b = RiftLayout.of(1920, 1080);
        assertTrue(b.px() >= a.px());
        assertEquals(1920 - b.right(), b.left(), "поля симметричны, щель на всю ширину");
        assertTrue(b.cols() > a.cols());
        assertTrue(b.slitY() > a.slitY(), "щель у нижнего края нового окна");
    }

    @Test
    void slitSitsNearBottom() {
        var l = RiftLayout.of(1920, 1080);
        assertTrue(l.slitY() > 1080 * 3 / 4 && l.slitY() < 1080, "щель в нижней четверти: " + l.slitY());
        assertEquals(l.slitY(), l.y(RiftLayout.SLIT_ROW));
    }
}
