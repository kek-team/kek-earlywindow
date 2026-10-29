/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow;

import java.util.Random;
import team.kek.earlywindow.gl.QuadHelper;
import team.kek.earlywindow.gl.QuadShader;
import team.kek.earlywindow.gl.SimpleBufferBuilder;
import team.kek.earlywindow.model.LoadingProgress.Progress;
import team.kek.earlywindow.model.RiftLayout;
import team.kek.earlywindow.model.RiftSim;

/**
 * Один кадр раннего экрана: сцена лаунчера, нижняя полоса, разлом по прогрессу с искрами и подпись этапа.
 * Один экземпляр на всю загрузку — до передачи окна, после неё и в оверлее Minecraft, чтобы огонь и искры шли
 * непрерывно.
 */
final class PortalFrame {
    private static final int FIELD = 0x150F1E;
    private static final int DUSK = 0x2B2238;
    private static final int TEXT = 0xF3ECE2;
    private static final int TEXT_MUTED = 0xB9AFC6;
    /** Непрозрачность нижней полосы: сцена под ней чуть видна, как под полосой лаунчера. */
    private static final float FIELD_ALPHA = 0.85f;
    /** Разлом и подпись гаснут к этой доле схлопывания портала. */
    private static final float FADE_BY_COLLAPSE = 0.6f;

    private final SceneLayer scene = new SceneLayer();
    private final RiftSim sim = new RiftSim(new Random());
    private long lastNanos = -1;

    /**
     * @param collapse 0…1 схлопывание портала в конце загрузки
     * @param flash    вспышка 0…1 от оверлея, отрицательное — своя
     */
    void paint(FrameContext ctx, Progress progress, float collapse, float flash) {
        scene.paint(ctx, collapse, flash);
        double dt = lastNanos < 0 ? 0 : (ctx.nanos() - lastNanos) / 1e9;
        lastNanos = ctx.nanos();
        var l = RiftLayout.of(ctx.width(), ctx.height());
        sim.advance(dt, progress.fraction(), progress.known(), l.cols());

        float t = Math.clamp(collapse / FADE_BY_COLLAPSE, 0f, 1f);
        float alpha = 1f - t * t * (3f - 2f * t);
        if (alpha <= 0f) return;
        var q = ctx.quads();
        q.begin(ctx.width(), ctx.height());
        var bb = q.fills();
        QuadHelper.loadQuad(bb, 0, ctx.width(), l.y(RiftLayout.SLIT_ROW), ctx.height(), 0, 0, 0, 0, QuadShader.colour(FIELD, FIELD_ALPHA * alpha));
        rift(bb, l, progress.known(), alpha);
        q.draw();
        label(ctx, l, progress, alpha);
        q.end();
    }

    private void rift(SimpleBufferBuilder bb, RiftLayout l, boolean known, float alpha) {
        int lit = RiftSim.litColumns(sim.lit(), l.cols());
        for (int col = 0; col < l.cols(); col++) {
            int heat;
            float glow;
            if (col < lit) {
                int behind = lit - 1 - col;
                heat = RiftSim.heatColor(behind);
                glow = 0.3f * Math.max(0f, 1f - behind / 15f);
            } else {
                float h = RiftSim.preheat(col, lit, known, sim.time(), l.cols());
                heat = RiftSim.lerp(RiftSim.CORE, RiftSim.EMBER, h);
                glow = 0.12f * h;
            }
            cell(bb, l, col, RiftLayout.SLIT_ROW + 1, DUSK, alpha);
            cell(bb, l, col, RiftLayout.SLIT_ROW, heat, alpha);
            cell(bb, l, col, RiftLayout.SLIT_ROW - 1, heat, glow * alpha);
        }
        for (var s : sim.sparks()) {
            float k = s.age();
            int colour = k < 0.2f ? RiftSim.WHITE_HOT : k < 0.55f ? RiftSim.SPARK : RiftSim.EMBER;
            cell(bb, l, Math.round(s.x), Math.round(s.y), colour, (1f - k * k) * alpha);
        }
    }

    private static void cell(SimpleBufferBuilder bb, RiftLayout l, int col, int row, int rgb, float alpha) {
        if (alpha <= 0f || col < 0 || col >= l.cols() || row < 0 || row >= RiftLayout.ROWS) return;
        float x = l.x(col);
        float y = l.y(row);
        QuadHelper.loadQuad(bb, x, x + l.px(), y, y + l.px(), 0, 0, 0, 0, QuadShader.colour(rgb, Math.min(alpha, 1f)));
    }

    /** Подпись этапа слева над разломом, процент — справа. Целое увеличение пиксельного шрифта. */
    private void label(FrameContext ctx, RiftLayout l, Progress progress, float alpha) {
        var font = ctx.font();
        int scale = Math.max(1, Math.round(ctx.height() / 720f));
        int margin = 12 * l.px();
        float y = l.y(0) - font.lineHeight() * scale;
        var percent = Math.round(sim.lit() * 100f) + "%";
        var bb = ctx.quads().text(font);
        font.text(bb, progress.text(), l.left() + margin, y, scale, QuadShader.colour(TEXT, alpha));
        font.text(bb, percent, l.right() - margin - font.width(percent) * scale, y, scale, QuadShader.colour(TEXT_MUTED, alpha));
        ctx.quads().draw();
    }
}
