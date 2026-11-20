package com.teamlifebind.clientui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/** Local presentation preferences; no server menu or gameplay state is stored here. */
final class AuiMenuSettings {
    private static boolean loaded, motion = true;
    private AuiMenuSettings() {}
    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/teamlifebind-ui.properties");
    }
    static boolean motion() {
        if (!loaded) {
            loaded = true;
            if (Files.isRegularFile(file())) try (var reader = Files.newBufferedReader(file())) {
                var values = new Properties(); values.load(reader);
                motion = !"false".equalsIgnoreCase(values.getProperty("motion", "true"));
            } catch (IOException | IllegalArgumentException failure) {
                System.getLogger("TeamLifeBind/UI").log(System.Logger.Level.WARNING, "Cannot read menu preferences", failure);
            }
        }
        return motion;
    }
    static boolean toggleMotion() {
        motion = !motion();
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), "# TeamLifeBind client menu presentation\nmotion=" + motion + "\n");
        } catch (IOException failure) {
            System.getLogger("TeamLifeBind/UI").log(System.Logger.Level.WARNING, "Cannot save menu preferences", failure);
        }
        return motion;
    }
}
