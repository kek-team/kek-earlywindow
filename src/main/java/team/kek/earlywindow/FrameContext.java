/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow;

import team.kek.earlywindow.gl.QuadShader;
import team.kek.earlywindow.gl.SimpleFont;

/**
 * Кадр, который рисует {@link PortalWindow#setFrame}: размер кадрового буфера в пикселях, время с открытия окна,
 * чем рисовать. Кадровый буфер уже привязан и очищен.
 */
public record FrameContext(int width, int height, long nanos, QuadShader quads, SimpleFont font) {}
