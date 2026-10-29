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
 * Ранний экран загрузки Искры. Пока своего окна нет — всё делает стандартный {@link DisplayWindow}.
 */
public class KekEarlyWindow implements ImmediateWindowProvider {
    public static final String NAME = "kekearlywindow";
    private static final Logger LOGGER = LoggerFactory.getLogger("KEK EARLYWINDOW");

    private ImmediateWindowProvider fallback;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Runnable initialize(String[] arguments) {
        LOGGER.info("[KEK EARLYWINDOW] провайдер найден");
        fallback = new DisplayWindow();
        return fallback.initialize(arguments);
    }

    @Override
    public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
        fallback.updateFramebufferSize(width, height);
    }

    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        return fallback.setupMinecraftWindow(width, height, title, monitor);
    }

    @Override
    public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
        return fallback.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);
    }

    @Override
    public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
        return fallback.loadingOverlay(mc, ri, ex, fade);
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
        fallback.updateModuleReads(layer);
    }

    @Override
    public void periodicTick() {
        fallback.periodicTick();
    }

    @Override
    public String getGLVersion() {
        return fallback.getGLVersion();
    }

    @Override
    public void crash(String message) {
        fallback.crash(message);
    }
}
