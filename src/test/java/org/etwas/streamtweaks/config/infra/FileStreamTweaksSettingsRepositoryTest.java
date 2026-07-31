package org.etwas.streamtweaks.config.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.etwas.streamtweaks.config.StreamTweaksSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStreamTweaksSettingsRepositoryTest {

    @TempDir
    Path configDir;

    @Test
    void loadReturnsDefaultWhenFileDoesNotExist() {
        Path settingsPath = configDir.resolve("stream-tweaks").resolve("stream-tweaks-config.json");
        FileStreamTweaksSettingsRepository repository = new FileStreamTweaksSettingsRepository(settingsPath);

        assertEquals(StreamTweaksSettings.DEFAULT, repository.load());
        assertFalse(Files.exists(settingsPath), "load()はファイルを新規作成しないこと");
    }

    @Test
    void saveAndLoadRoundTripsTheSettings() {
        Path settingsPath = configDir.resolve("stream-tweaks").resolve("stream-tweaks-config.json");
        FileStreamTweaksSettingsRepository repository = new FileStreamTweaksSettingsRepository(settingsPath);

        repository.save(new StreamTweaksSettings(false));

        assertTrue(Files.exists(settingsPath));
        assertEquals(new StreamTweaksSettings(false), repository.load());
    }

    @Test
    void loadReturnsDefaultWhenFileIsCorrupted() throws IOException {
        Path settingsPath = configDir.resolve("stream-tweaks").resolve("stream-tweaks-config.json");
        Files.createDirectories(settingsPath.getParent());
        Files.writeString(settingsPath, "{ not valid json ");
        FileStreamTweaksSettingsRepository repository = new FileStreamTweaksSettingsRepository(settingsPath);

        assertEquals(StreamTweaksSettings.DEFAULT, repository.load());
    }

    @Test
    void loadReturnsDefaultWhenFileContainsNullJson() throws IOException {
        Path settingsPath = configDir.resolve("stream-tweaks").resolve("stream-tweaks-config.json");
        Files.createDirectories(settingsPath.getParent());
        Files.writeString(settingsPath, "null");
        FileStreamTweaksSettingsRepository repository = new FileStreamTweaksSettingsRepository(settingsPath);

        assertEquals(StreamTweaksSettings.DEFAULT, repository.load());
    }

    @Test
    void saveOverwritesPreviouslySavedSettings() {
        Path settingsPath = configDir.resolve("stream-tweaks").resolve("stream-tweaks-config.json");
        FileStreamTweaksSettingsRepository repository = new FileStreamTweaksSettingsRepository(settingsPath);

        repository.save(new StreamTweaksSettings(false));
        repository.save(new StreamTweaksSettings(true));

        assertEquals(new StreamTweaksSettings(true), repository.load());
    }
}
