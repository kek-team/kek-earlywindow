/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Изменено KEK.Team: буфер в размер окна (а не 854×480 с растяжением) и пересоздаётся при смене размера,
 * вывод в окно — один к одному.
 */

package team.kek.earlywindow.gl;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.IntBuffer;

/**
 * Кадровый буфер, в который рисуется кадр. Строка 0 — верх окна: при выводе в окно кадр переворачивается.
 */
public class EarlyFramebuffer {
    private final int framebuffer;
    private final int texture;
    private int width;
    private int height;

    public EarlyFramebuffer(int width, int height) {
        this.framebuffer = glGenFramebuffers();
        this.texture = glGenTextures();
        allocate(width, height);
    }

    private void allocate(int width, int height) {
        this.width = width;
        this.height = height;
        var previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
        glBindTexture(GL_TEXTURE_2D, this.texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (IntBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.texture, 0);
        glBindTexture(GL_TEXTURE_2D, previous);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /** Подгоняет буфер под окно; содержимое при этом теряется. */
    public void resize(int width, int height) {
        if (width != this.width || height != this.height) {
            allocate(width, height);
        }
    }

    public void activate() {
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
    }

    public void deactivate() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /** Выводит кадр в окно, переворачивая по вертикали. */
    public void draw() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.framebuffer);
        glBlitFramebuffer(0, height, width, 0, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public int texture() {
        return this.texture;
    }

    public void close() {
        glDeleteTextures(this.texture);
        glDeleteFramebuffers(this.framebuffer);
    }
}
