/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.gl;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Состояние GL, которое трогает отрисовка кадра в контексте Minecraft. Minecraft кэширует состояние в
 * {@code GlStateManager}: всё, что изменили сырые вызовы, должно вернуться как было, иначе меню рисуется с артефактами.
 */
public final class GlState {
    private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    private final int drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int texture0;
    private final int[] viewport = new int[4];
    private final float[] clearColour = new float[4];
    private final boolean blend = glIsEnabled(GL_BLEND);
    private final int blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
    private final int blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
    private final int blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
    private final int blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
    private final int blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
    private final int blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
    private final boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
    private final boolean cullFace = glIsEnabled(GL_CULL_FACE);
    private final boolean scissorTest = glIsEnabled(GL_SCISSOR_TEST);

    private GlState() {
        glActiveTexture(GL_TEXTURE0);
        texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(activeTexture);
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColour);
    }

    /** Запоминает состояние и готовит контекст к нашим проходам: без глубины, отсечения граней и ножниц. */
    public static GlState saveAndPrepare() {
        var state = new GlState();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_SCISSOR_TEST);
        glBlendEquation(GL_FUNC_ADD);
        glActiveTexture(GL_TEXTURE0);
        return state;
    }

    public void restore() {
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glUseProgram(program);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture0);
        glActiveTexture(activeTexture);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glClearColor(clearColour[0], clearColour[1], clearColour[2], clearColour[3]);
        set(GL_BLEND, blend);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        set(GL_DEPTH_TEST, depthTest);
        set(GL_CULL_FACE, cullFace);
        set(GL_SCISSOR_TEST, scissorTest);
    }

    private static void set(int cap, boolean enabled) {
        if (enabled) {
            glEnable(cap);
        } else {
            glDisable(cap);
        }
    }
}
