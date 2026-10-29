/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Изменено KEK.Team: перенесён ElementShader; свой буфер вершин и пакеты «заливки» и «текст» вместо RenderElement,
 * шрифт привязывается к нулевому слоту только на время отрисовки.
 */

package team.kek.earlywindow.gl;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Цветные прямоугольники и текст в кадровом буфере. Координаты — пиксели буфера, (0, 0) — левый верхний угол окна.
 */
public final class QuadShader {
    private static final int FONT = 0;
    private static final int FILL = 2;

    private final SimpleBufferBuilder bb = new SimpleBufferBuilder(1 << 16);
    private int program;
    private int textureUniform;
    private int screenSizeUniform;
    private int renderTypeUniform;

    public void init() {
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, """
                #version 150 core
                uniform sampler2D tex;
                uniform int rendertype;
                in vec2 fTex;
                in vec4 fColour;
                out vec4 fragColor;
                void main() {
                    if (rendertype == 0)
                        fragColor = vec4(1, 1, 1, texture(tex, fTex).r) * fColour;
                    if (rendertype == 2)
                        fragColor = fColour;
                }
                """);
        glShaderSource(vertexShader, """
                #version 150 core
                in vec2 position;
                in vec2 tex;
                in vec4 colour;
                uniform vec2 screenSize;
                out vec2 fTex;
                out vec4 fColour;
                void main() {
                    fTex = tex;
                    fColour = colour;
                    gl_Position = vec4((position / screenSize) * 2 - 1, 0.0, 1.0);
                }
                """);
        glCompileShader(vertexShader);
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("VertexShader linkage failure. \n" + glGetShaderInfoLog(vertexShader));
        }
        glCompileShader(fragmentShader);
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("FragmentShader linkage failure. \n" + glGetShaderInfoLog(fragmentShader));
        }
        var program = glCreateProgram();
        glBindAttribLocation(program, 0, "position");
        glBindAttribLocation(program, 1, "tex");
        glBindAttribLocation(program, 2, "colour");
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("ShaderProgram linkage failure. \n" + glGetProgramInfoLog(program));
        }
        this.program = program;
        glDetachShader(program, vertexShader);
        glDetachShader(program, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        textureUniform = glGetUniformLocation(program, "tex");
        screenSizeUniform = glGetUniformLocation(program, "screenSize");
        renderTypeUniform = glGetUniformLocation(program, "rendertype");
    }

    /** Начало рисования в буфер размером {@code width × height}. */
    public void begin(int width, int height) {
        glUseProgram(program);
        glUniform2f(screenSizeUniform, width, height);
        glUniform1i(textureUniform, 0);
    }

    /** Пакет заливок: вершины — {@link QuadHelper#loadQuad} с цветом, затем {@link #draw()}. */
    public SimpleBufferBuilder fills() {
        glUniform1i(renderTypeUniform, FILL);
        return bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
    }

    /** Пакет текста шрифтом {@code font}: вершины — {@link SimpleFont#text}, затем {@link #draw()}. */
    public SimpleBufferBuilder text(SimpleFont font) {
        glUniform1i(renderTypeUniform, FONT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, font.texture());
        return bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
    }

    public void draw() {
        bb.draw();
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void end() {
        glUseProgram(0);
    }

    /** Цвет вершины (ABGR в памяти) из {@code 0xRRGGBB} и непрозрачности 0…1. */
    public static int colour(int rgb, float alpha) {
        int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255f);
        return a << 24 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    public void close() {
        glDeleteProgram(program);
        bb.close();
    }
}
