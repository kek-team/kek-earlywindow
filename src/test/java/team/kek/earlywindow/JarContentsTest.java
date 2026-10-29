package team.kek.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class JarContentsTest {
    private static JarFile jar() throws IOException {
        return new JarFile(System.getProperty("kek.test.jar"));
    }

    @Test
    void registersProvider() throws IOException {
        try (var jar = jar()) {
            var entry = jar.getEntry("META-INF/services/net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider");
            var text = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("team.kek.earlywindow.KekEarlyWindow", text.strip());
        }
    }

    @Test
    void hasNoNeoforgedPackages() throws IOException {
        try (var jar = jar()) {
            assertTrue(Collections.list(jar.entries()).stream().noneMatch(e -> e.getName().startsWith("net/neoforged/")));
        }
    }

    @Test
    void declaresEarlydisplayVersion() throws IOException {
        try (var jar = jar()) {
            assertEquals("4.0.45", jar.getManifest().getMainAttributes().getValue("Kek-Earlydisplay-Version"));
        }
    }
}
