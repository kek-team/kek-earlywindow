package team.kek.earlywindow.gl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimpleFontGlyphsTest {
    @Test
    void coversRussianAlphabet() {
        for (char c : "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя…·".toCharArray()) {
            assertTrue(SimpleFont.covers(c), () -> "нет глифа " + c);
        }
    }

    @Test
    void coversAscii() {
        for (char c = ' '; c <= '~'; c++) {
            final char ch = c;
            assertTrue(SimpleFont.covers(c), () -> "нет глифа " + ch);
        }
    }

    @Test
    void skipsEverythingElse() {
        assertFalse(SimpleFont.covers('\n'));
        assertFalse(SimpleFont.covers('漢'));
    }
}
