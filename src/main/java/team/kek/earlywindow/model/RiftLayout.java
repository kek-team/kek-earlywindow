/*
 * Copyright (c) KEK.Team
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package team.kek.earlywindow.model;

/**
 * Раскладка разлома в целых пикселях кадра, как {@code RiftLayout} лаунчера: «пиксель» разлома {@link #px()},
 * {@link #ROWS} рядов, щель — ряд {@link #SLIT_ROW}, над ней место, куда взлетают искры. Строится заново по размеру
 * кадра, поэтому при смене размера окна ничего не растягивается.
 */
public record RiftLayout(int px, int left, int right, int slitY, int cols) {
    public static final int ROWS = 10;
    public static final int SLIT_ROW = 7;
    /** Доля высоты окна под щелью — нижняя полоса, как на главном экране лаунчера. */
    private static final float BAND = 0.14f;

    public static RiftLayout of(int width, int height) {
        int px = Math.max(2, Math.round(height / 360f));
        int cols = Math.max(1, width / px);
        int left = (width - cols * px) / 2;
        int slitY = height - px * Math.max(ROWS - SLIT_ROW, Math.round(height * BAND / px));
        return new RiftLayout(px, left, left + cols * px, slitY, cols);
    }

    public int x(int col) {
        return left + col * px;
    }

    public int y(int row) {
        return slitY + (row - SLIT_ROW) * px;
    }
}
