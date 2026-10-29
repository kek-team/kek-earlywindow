/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow;

import static org.lwjgl.glfw.GLFW.*;

import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.macosx.ObjCRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.kek.earlywindow.gl.STBHelper;

/**
 * Иконка окна игры — искра Искры. Minecraft сразу после передачи окна ставит свою, поэтому её ставят ещё раз на первом
 * {@code periodicTick} после передачи и в {@code loadingOverlay}. На macOS у окон иконок нет — картинка идёт в Dock
 * через {@code [NSApp setApplicationIconImage:]}. Только на главном потоке; ошибки не стоят вылета игры.
 */
public final class WindowIcon {
    private static final Logger LOGGER = LoggerFactory.getLogger("KEK EARLYWINDOW");
    private static final String ICON = "team/kek/earlywindow/game-icon.png";
    private static final int ICON_BUFFER_BYTES = 20000;

    private WindowIcon() {}

    public static void apply(long window) {
        try {
            if (glfwGetPlatform() == GLFW_PLATFORM_COCOA) {
                dock();
            } else {
                windowIcon(window);
            }
        } catch (Throwable t) {
            LOGGER.debug("Иконка окна не поставлена", t);
        }
    }

    private static void windowIcon(long window) {
        int[] w = new int[1];
        int[] h = new int[1];
        int[] channels = new int[1];
        var pixels = STBHelper.loadImageFromClasspath(ICON, ICON_BUFFER_BYTES, w, h, channels);
        try (var images = GLFWImage.malloc(1)) {
            images.get(0).width(w[0]).height(h[0]).pixels(pixels);
            glfwSetWindowIcon(window, images);
        } finally {
            STBImage.stbi_image_free(pixels);
        }
    }

    /** Как ванильный {@code MacosUtil.loadIcon}: PNG → {@code NSData} → {@code NSImage} → значок приложения. */
    private static void dock() {
        var png = STBHelper.readFromClasspath(ICON, ICON_BUFFER_BYTES);
        long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
        long data = JNI.invokePPP(cls("NSData"), sel("alloc"), send);
        data = JNI.invokePPPPP(data, sel("initWithBytes:length:"), MemoryUtil.memAddress(png), png.remaining(), send);
        long image = JNI.invokePPP(cls("NSImage"), sel("alloc"), send);
        image = JNI.invokePPPP(image, sel("initWithData:"), data, send);
        JNI.invokePPV(data, sel("release"), send);
        if (image != 0) {
            long app = JNI.invokePPP(cls("NSApplication"), sel("sharedApplication"), send);
            JNI.invokePPPV(app, sel("setApplicationIconImage:"), image, send);
            JNI.invokePPV(image, sel("release"), send);
        }
    }

    private static long cls(String name) {
        return ObjCRuntime.objc_getClass(name);
    }

    private static long sel(String name) {
        return ObjCRuntime.sel_getUid(name);
    }
}
