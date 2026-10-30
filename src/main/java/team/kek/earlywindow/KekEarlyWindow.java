/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ранний экран загрузки Искры. Своё окно — {@link PortalWindow}; если оно не поднялось (или задано
 * {@code -Dkek.earlywindow.fail=1}), все вызовы уходят стандартному {@link DisplayWindow}.
 */
public class KekEarlyWindow implements ImmediateWindowProvider {
    public static final String NAME = "kekearlywindow";
    public static final String FAIL_PROPERTY = "kek.earlywindow.fail";
    private static final Logger LOGGER = LoggerFactory.getLogger("KEK EARLYWINDOW");

    private final Supplier<ImmediateWindowProvider> ownFactory;
    private final Supplier<ImmediateWindowProvider> fallbackFactory;
    private ImmediateWindowProvider active;

    /** Окно создаётся только в {@link #initialize}: FML создаёт все провайдеры из classpath, и сбой здесь сорвал бы запуск. */
    public KekEarlyWindow() {
        this(PortalWindow::new, DisplayWindow::new);
    }

    KekEarlyWindow(ImmediateWindowProvider own, Supplier<ImmediateWindowProvider> fallbackFactory) {
        this(() -> own, fallbackFactory);
    }

    KekEarlyWindow(Supplier<ImmediateWindowProvider> ownFactory, Supplier<ImmediateWindowProvider> fallbackFactory) {
        this.ownFactory = ownFactory;
        this.fallbackFactory = fallbackFactory;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Runnable initialize(String[] arguments) {
        if ("1".equals(System.getProperty(FAIL_PROPERTY))) {
            return fallBack(arguments, "задано " + FAIL_PROPERTY, null);
        }
        LOGGER.info("[KEK EARLYWINDOW] своё окно");
        ImmediateWindowProvider own = null;
        try {
            own = ownFactory.get();
            active = own;
            return own.initialize(arguments);
        } catch (Throwable t) {
            if (own instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Throwable ignored) {
                    // окно и так не работает
                }
            }
            return fallBack(arguments, t.toString(), t);
        }
    }

    private Runnable fallBack(String[] arguments, String reason, Throwable cause) {
        LOGGER.warn("[KEK EARLYWINDOW] откат на стандартный экран: {}", reason, cause);
        active = fallbackFactory.get();
        return active.initialize(arguments);
    }

    @Override
    public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
        active.updateFramebufferSize(width, height);
    }

    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        return active.setupMinecraftWindow(width, height, title, monitor);
    }

    @Override
    public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
        return active.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);
    }

    @Override
    public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
        return active.loadingOverlay(mc, ri, ex, fade);
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
        active.updateModuleReads(layer);
    }

    @Override
    public void periodicTick() {
        active.periodicTick();
    }

    @Override
    public String getGLVersion() {
        return active.getGLVersion();
    }

    @Override
    public void crash(String message) {
        active.crash(message);
    }
}
