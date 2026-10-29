/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.model;

/**
 * Меняющиеся uniform'ы {@code scene.frag} на один кадр. Остальные постоянны: курсора, пульса, приглушения, заряда,
 * подъёма вспышки, выреза и скругления на раннем экране нет.
 */
public record SceneUniforms(float time, float open, float appear, float flash) {}
