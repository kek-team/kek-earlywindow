package team.kek.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import team.kek.earlywindow.OverlayFactory.Kind;

class OverlayFactoryTest {
    /** Как {@code team.kek.link.PortalOverlay.newInstance}. */
    public static final class FakePortal {
        static ToIntFunction<float[]> scene;

        public static Supplier<Object> newInstance(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, ToIntFunction<float[]> scene) {
            FakePortal.scene = scene;
            return () -> "portal";
        }
    }

    /** Как ванильный {@code LoadingOverlay(Minecraft, ReloadInstance, Consumer, boolean)}. */
    public static final class FakeVanilla {
        final boolean fade;

        public FakeVanilla(Object mc, Object ri, Consumer<Optional<Throwable>> ex, boolean fade) {
            this.fade = fade;
        }
    }

    private static Optional<Method> portal() throws NoSuchMethodException {
        return Optional.of(FakePortal.class.getMethod("newInstance", Supplier.class, Supplier.class, Consumer.class, ToIntFunction.class));
    }

    private static Optional<Constructor<?>> vanilla() {
        return Optional.of(FakeVanilla.class.getConstructors()[0]);
    }

    @Test
    void choosesPortalWhenModIsThere() throws Exception {
        assertEquals(Kind.PORTAL, OverlayFactory.choose(portal(), vanilla()));
        assertEquals(Kind.VANILLA, OverlayFactory.choose(Optional.empty(), vanilla()));
        assertEquals(Kind.NONE, OverlayFactory.choose(Optional.empty(), Optional.empty()));
    }

    @Test
    void portalGetsSceneBridge() throws Exception {
        ToIntFunction<float[]> scene = s -> 7;
        var overlay = new OverlayFactory(portal(), vanilla()).create(() -> "mc", () -> "ri", t -> {}, false, scene);
        assertEquals("portal", overlay.get());
        assertSame(scene, FakePortal.scene);
    }

    @Test
    void vanillaWithoutMod() {
        Supplier<Object> overlay = new OverlayFactory(Optional.empty(), vanilla()).create(() -> "mc", () -> "ri", t -> {}, true, s -> 0);
        assertTrue(((FakeVanilla) overlay.get()).fade);
    }

    @Test
    void noOverlayIsClearError() {
        var e = assertThrows(IllegalStateException.class,
                () -> new OverlayFactory(Optional.empty(), Optional.empty()).create(() -> "mc", () -> "ri", t -> {}, false, s -> 0));
        assertTrue(e.getMessage().contains("экран загрузки"), e.getMessage());
    }
}
