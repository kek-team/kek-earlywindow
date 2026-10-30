package team.kek.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KekEarlyWindowFallbackTest {
    @AfterEach
    void clearProperty() {
        System.clearProperty(KekEarlyWindow.FAIL_PROPERTY);
    }

    @Test
    void fallsBackWhenForced() {
        var own = new Fake(false);
        var fallback = new Fake(false);
        System.setProperty(KekEarlyWindow.FAIL_PROPERTY, "1");
        new KekEarlyWindow(own, () -> fallback).initialize(new String[0]);
        assertTrue(fallback.calls.contains("initialize"));
        assertFalse(own.calls.contains("initialize"));
    }

    @Test
    void fallsBackWhenOwnInitThrows() {
        var own = new Fake(true);
        var fallback = new Fake(false);
        new KekEarlyWindow(own, () -> fallback).initialize(new String[0]);
        assertTrue(fallback.calls.contains("initialize"));
        assertTrue(own.calls.contains("close"));
    }

    @Test
    void fallsBackWhenOwnCannotBeCreated() {
        var fallback = new Fake(false);
        new KekEarlyWindow(() -> {
            throw new NoClassDefFoundError("org/lwjgl/Changed");
        }, () -> fallback).initialize(new String[0]);
        assertTrue(fallback.calls.contains("initialize"));
    }

    @Test
    void usesOwnWindowWhenItStarts() {
        var own = new Fake(false);
        var fallback = new Fake(false);
        var provider = new KekEarlyWindow(own, () -> fallback);
        provider.initialize(new String[0]);
        provider.periodicTick();
        assertEquals(List.of("initialize", "periodicTick"), own.calls);
        assertTrue(fallback.calls.isEmpty());
    }

    @Test
    void delegatesEverythingAfterFallback() {
        var own = new Fake(true);
        var fallback = new Fake(false);
        var provider = new KekEarlyWindow(own, () -> fallback);
        provider.initialize(new String[0]);
        provider.setupMinecraftWindow(() -> 1, () -> 1, () -> "", () -> 0);
        provider.periodicTick();
        provider.loadingOverlay(() -> null, () -> null, t -> {}, false);
        provider.updateModuleReads(ModuleLayer.boot());
        assertEquals(List.of("initialize", "setupMinecraftWindow", "periodicTick", "loadingOverlay", "updateModuleReads"), fallback.calls);
        assertEquals(List.of("initialize", "close"), own.calls);
    }

    private static final class Fake implements ImmediateWindowProvider, AutoCloseable {
        final List<String> calls = new ArrayList<>();
        private final boolean failInit;

        Fake(boolean failInit) {
            this.failInit = failInit;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public Runnable initialize(String[] arguments) {
            calls.add("initialize");
            if (failInit) throw new IllegalStateException("нет GL");
            return () -> {};
        }

        @Override
        public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
            calls.add("updateFramebufferSize");
        }

        @Override
        public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
            calls.add("setupMinecraftWindow");
            return 1;
        }

        @Override
        public boolean positionWindow(Optional<Object> monitor, IntConsumer w, IntConsumer h, IntConsumer x, IntConsumer y) {
            calls.add("positionWindow");
            return true;
        }

        @Override
        public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
            calls.add("loadingOverlay");
            return () -> null;
        }

        @Override
        public void updateModuleReads(ModuleLayer layer) {
            calls.add("updateModuleReads");
        }

        @Override
        public void periodicTick() {
            calls.add("periodicTick");
        }

        @Override
        public String getGLVersion() {
            return "3.2";
        }

        @Override
        public void crash(String message) {
            calls.add("crash");
        }

        @Override
        public void close() {
            calls.add("close");
        }
    }
}
