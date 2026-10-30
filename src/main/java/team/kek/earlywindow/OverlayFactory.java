/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Экран загрузки ресурсов Minecraft после передачи окна. Есть мод KEK Link — его {@code PortalOverlay} продолжает
 * нашу сцену через мост {@code ToIntFunction<float[]>} (только типы JDK: провайдер и мод в разных слоях модулей);
 * нет — ванильный {@code LoadingOverlay}. Стандартный {@code NeoForgeLoadingOverlay} не подходит: он требует
 * {@code DisplayWindow}.
 */
final class OverlayFactory {
    enum Kind { PORTAL, VANILLA, NONE }

    static final String LINK_PACKAGE = "team.kek.link";
    static final String PORTAL_CLASS = LINK_PACKAGE + ".PortalOverlay";
    static final String VANILLA_CLASS = "net.minecraft.client.gui.screens.LoadingOverlay";
    private static final Logger LOGGER = LoggerFactory.getLogger("KEK EARLYWINDOW");

    private final Optional<Method> portal;
    private final Optional<Constructor<?>> vanilla;

    OverlayFactory(Optional<Method> portal, Optional<Constructor<?>> vanilla) {
        this.portal = portal;
        this.vanilla = vanilla;
    }

    static Kind choose(Optional<Method> portal, Optional<Constructor<?>> vanilla) {
        if (portal.isPresent()) return Kind.PORTAL;
        return vanilla.isPresent() ? Kind.VANILLA : Kind.NONE;
    }

    Kind kind() {
        return choose(portal, vanilla);
    }

    /**
     * Ищет оверлеи в игровом слое: модуль мода — по пакету {@value #LINK_PACKAGE}, ванильный — в модуле
     * {@code minecraft}. Модулю провайдера ({@code self}) даёт читать оба.
     */
    static OverlayFactory find(ModuleLayer layer, Module self) {
        var factory = new OverlayFactory(guarded(() -> findPortal(layer, self)), guarded(() -> findVanilla(layer, self)));
        if (factory.kind() != Kind.PORTAL) {
            LOGGER.warn("[KEK EARLYWINDOW] оверлей KEK Link не найден — ванильный экран");
        }
        return factory;
    }

    private static Optional<Method> findPortal(ModuleLayer layer, Module self) {
        return layer.modules().stream()
                .filter(m -> m.getPackages().contains(LINK_PACKAGE))
                .findFirst()
                .map(m -> {
                    self.addReads(m);
                    return Class.forName(m, PORTAL_CLASS);
                })
                .flatMap(c -> Arrays.stream(c.getMethods())
                        .filter(m -> m.getName().equals("newInstance") && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 4)
                        .findFirst());
    }

    private static Optional<Constructor<?>> findVanilla(ModuleLayer layer, Module self) {
        return layer.findModule("minecraft")
                .map(m -> {
                    self.addReads(m);
                    return Class.forName(m, VANILLA_CLASS);
                })
                .flatMap(c -> Arrays.stream(c.getConstructors()).filter(k -> k.getParameterCount() == 4).findFirst());
    }

    /** Классы мода и игры могут не загрузиться (LinkageError): игра должна запуститься и без оверлея провайдера. */
    private static <R> Optional<R> guarded(Supplier<Optional<R>> find) {
        try {
            return find.get();
        } catch (Throwable t) {
            LOGGER.warn("[KEK EARLYWINDOW] оверлей не найден: {}", t.toString());
            return Optional.empty();
        }
    }

    /** Мод обновляется с паком отдельно от провайдера: любой сбой его оверлея — ванильный экран, а не вылет. */
    @SuppressWarnings("unchecked")
    <T> Supplier<T> create(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade, ToIntFunction<float[]> scene) {
        return switch (kind()) {
            case PORTAL -> {
                Supplier<T> fallback = vanilla(mc, ri, ex, fade);
                try {
                    Supplier<T> overlay = (Supplier<T>) portal.orElseThrow().invoke(null, mc, ri, ex, scene);
                    yield () -> {
                        try {
                            return overlay.get();
                        } catch (Throwable t) {
                            LOGGER.error("[KEK EARLYWINDOW] оверлей KEK Link не создан — ванильный экран", t);
                            return fallback.get();
                        }
                    };
                } catch (Throwable t) {
                    LOGGER.error("[KEK EARLYWINDOW] оверлей KEK Link не создан — ванильный экран", t);
                    yield fallback;
                }
            }
            case VANILLA -> vanilla(mc, ri, ex, fade);
            case NONE -> throw new IllegalStateException("Не найден экран загрузки Minecraft: нет ни PortalOverlay KEK Link, ни LoadingOverlay");
        };
    }

    @SuppressWarnings("unchecked")
    private <T> Supplier<T> vanilla(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
        return () -> {
            try {
                return (T) vanilla.orElseThrow(() -> new IllegalStateException("нет LoadingOverlay")).newInstance(mc.get(), ri.get(), ex, fade);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Не удалось создать экран загрузки Minecraft", e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Не удалось создать экран загрузки Minecraft", e);
            }
        };
    }
}
