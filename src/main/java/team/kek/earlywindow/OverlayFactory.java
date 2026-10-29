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
        var portal = layer.modules().stream()
                .filter(m -> m.getPackages().contains(LINK_PACKAGE))
                .findFirst()
                .map(m -> {
                    self.addReads(m);
                    return Class.forName(m, PORTAL_CLASS);
                })
                .flatMap(c -> Arrays.stream(c.getMethods())
                        .filter(m -> m.getName().equals("newInstance") && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 4)
                        .findFirst());
        var vanilla = layer.findModule("minecraft")
                .map(m -> {
                    self.addReads(m);
                    return Class.forName(m, VANILLA_CLASS);
                })
                .flatMap(c -> Arrays.stream(c.getConstructors()).filter(k -> k.getParameterCount() == 4).findFirst());
        var factory = new OverlayFactory(portal, vanilla.map(k -> (Constructor<?>) k));
        if (factory.kind() != Kind.PORTAL) {
            LOGGER.warn("[KEK EARLYWINDOW] оверлей KEK Link не найден — ванильный экран");
        }
        return factory;
    }

    @SuppressWarnings("unchecked")
    <T> Supplier<T> create(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade, ToIntFunction<float[]> scene) {
        try {
            return switch (kind()) {
                case PORTAL -> (Supplier<T>) portal.orElseThrow().invoke(null, mc, ri, ex, scene);
                case VANILLA -> {
                    var constructor = vanilla.orElseThrow();
                    yield () -> {
                        try {
                            return (T) constructor.newInstance(mc.get(), ri.get(), ex, fade);
                        } catch (InvocationTargetException e) {
                            throw new IllegalStateException("Не удалось создать экран загрузки Minecraft", e.getCause());
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException("Не удалось создать экран загрузки Minecraft", e);
                        }
                    };
                }
                case NONE -> throw new IllegalStateException("Не найден экран загрузки Minecraft: нет ни PortalOverlay KEK Link, ни LoadingOverlay");
            };
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Не удалось создать экран загрузки KEK Link", e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Не удалось создать экран загрузки KEK Link", e);
        }
    }
}
