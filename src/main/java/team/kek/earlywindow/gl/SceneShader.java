/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.gl;

import static org.lwjgl.opengl.GL32C.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import team.kek.earlywindow.model.SceneParticles;
import team.kek.earlywindow.model.SceneUniforms;

/**
 * Сцена лаунчера ({@code scene.frag}) на весь кадровый буфер. Цвет шейдера премультиплицирован — рисуется
 * смешиванием {@code ONE, ONE_MINUS_SRC_ALPHA}.
 */
public final class SceneShader {
    private static final String VERTEX = """
            #version 150 core
            void main() {
                // полноэкранный треугольник без вершинного буфера
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private final int program;
    private final int vao;
    private final int time;
    private final int resolution;
    private final int open;
    private final int appear;
    private final int flash;
    private final int particles;

    /** Компилирует шейдер; ошибка компиляции или сборки — исключение с журналом драйвера. */
    public SceneShader() {
        int vs = compile(GL_VERTEX_SHADER, VERTEX);
        int fs = compile(GL_FRAGMENT_SHADER, resource("team/kek/earlywindow/scene.frag"));
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        glDetachShader(program, vs);
        glDetachShader(program, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            var log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Сцена не собралась:\n" + log);
        }
        vao = glGenVertexArrays();
        time = glGetUniformLocation(program, "time");
        resolution = glGetUniformLocation(program, "resolution");
        open = glGetUniformLocation(program, "open");
        appear = glGetUniformLocation(program, "appear");
        flash = glGetUniformLocation(program, "flash");
        particles = glGetUniformLocation(program, "particles");
        // постоянные: курсора, пульса, приглушения, заряда, выреза и скругления на раннем экране нет
        glUseProgram(program);
        for (var name : new String[] { "pulse", "dim", "charge", "flashLift", "cutout", "cornerRadius" }) {
            glUniform1f(glGetUniformLocation(program, name), 0f);
        }
        glUniform2f(glGetUniformLocation(program, "mouse"), 0f, 0f);
        glUseProgram(0);
    }

    /** Рисует сцену в привязанный кадровый буфер {@code width × height}; частицы — {@link SceneParticles}. */
    public void draw(int width, int height, SceneUniforms u, float[] particleData) {
        glUseProgram(program);
        glUniform1f(time, u.time());
        glUniform2f(resolution, width, height);
        glUniform1f(open, u.open());
        glUniform1f(appear, u.appear());
        glUniform1f(flash, u.flash());
        glUniform4fv(particles, particleData);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(0);
    }

    public void close() {
        glDeleteProgram(program);
        glDeleteVertexArrays(vao);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            var log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Шейдер сцены не скомпилировался:\n" + log);
        }
        return shader;
    }

    private static String resource(String name) {
        try (var in = Objects.requireNonNull(SceneShader.class.getClassLoader().getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
