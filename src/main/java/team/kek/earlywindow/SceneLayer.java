/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow;

import static org.lwjgl.opengl.GL32C.GL_RENDERER;
import static org.lwjgl.opengl.GL32C.glGetString;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.kek.earlywindow.gl.QuadShader;
import team.kek.earlywindow.gl.SceneShader;
import team.kek.earlywindow.model.SceneParticles;
import team.kek.earlywindow.model.SceneTimeline;

/**
 * Фон кадра: сцена лаунчера, а если шейдер не собрался, упал, выключен ({@code -Dkek.earlywindow.animated=false} —
 * галочка «Анимированный фон») или рендер программный — статичный градиент, как у лаунчера в тех же случаях.
 * Шейдер создаётся лениво на первом кадре: только тогда контекст GL точно у текущего потока.
 */
final class SceneLayer {
    static final String ANIMATED_PROPERTY = "kek.earlywindow.animated";
    private static final Logger LOGGER = LoggerFactory.getLogger("KEK EARLYWINDOW");
    /** Подстроки {@code GL_RENDERER} программных рендеров. */
    private static final List<String> SOFTWARE = List.of("llvmpipe", "softpipe", "gdi generic", "microsoft basic render", "swiftshader");
    private static final int GRADIENT_TOP = 0x150F1E;
    private static final int GRADIENT_BOTTOM = 0x0B0614;

    private final float[] particles = new float[SceneParticles.COUNT * SceneParticles.STRIDE];
    private SceneShader shader;
    private boolean broken;

    /** Рисует фон; {@code collapse} и {@code flashOverride} — как у {@link SceneTimeline#at}. */
    void paint(FrameContext ctx, float collapse, float flashOverride) {
        if (shader == null && !broken) {
            start();
        }
        if (!broken) {
            try {
                var u = SceneTimeline.at(ctx.nanos() / 1e9, collapse, flashOverride);
                SceneParticles.fill(particles, u.time(), u.open(), 0f, u.flash());
                shader.draw(ctx.width(), ctx.height(), u, particles);
                return;
            } catch (Throwable t) {
                LOGGER.error("Сцена упала, дальше — градиент", t);
                broken = true;
            }
        }
        gradient(ctx, 1f - Math.clamp(collapse, 0f, 1f));
    }

    private void start() {
        if ("false".equals(System.getProperty(ANIMATED_PROPERTY))) {
            LOGGER.info("Анимированный фон выключен — градиент");
            broken = true;
            return;
        }
        var renderer = String.valueOf(glGetString(GL_RENDERER)).toLowerCase(Locale.ROOT);
        if (SOFTWARE.stream().anyMatch(renderer::contains)) {
            LOGGER.info("Программный рендер ({}) — градиент", renderer);
            broken = true;
            return;
        }
        try {
            shader = new SceneShader();
        } catch (Throwable t) {
            LOGGER.error("Сцена не собралась, дальше — градиент", t);
            broken = true;
        }
    }

    private static void gradient(FrameContext ctx, float alpha) {
        var q = ctx.quads();
        q.begin(ctx.width(), ctx.height());
        var bb = q.fills();
        int top = QuadShader.colour(GRADIENT_TOP, alpha);
        int bottom = QuadShader.colour(GRADIENT_BOTTOM, alpha);
        float w = ctx.width();
        float h = ctx.height();
        bb.pos(0, 0).tex(0, 0).colour(top).endVertex();
        bb.pos(w, 0).tex(0, 0).colour(top).endVertex();
        bb.pos(0, h).tex(0, 0).colour(bottom).endVertex();
        bb.pos(w, h).tex(0, 0).colour(bottom).endVertex();
        q.draw();
        q.end();
    }
}
