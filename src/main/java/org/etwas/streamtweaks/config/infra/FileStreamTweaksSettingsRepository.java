package org.etwas.streamtweaks.config.infra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.etwas.streamtweaks.config.StreamTweaksSettings;
import org.etwas.streamtweaks.config.StreamTweaksSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link StreamTweaksSettings} をJSONファイルとして永続化する実装。
 *
 * <p>認証情報用の {@code FileTwitchCredentialRepository} と同様、一時ファイルへ書き込んでから
 * アトミックにリネームすることで書き込み中のクラッシュに対する耐性を持たせる。ただし
 * 認証情報と異なりトークン等の機微情報を含まないため、パーミッション制御は行わない。
 *
 * <p>ファイルが存在しない、または壊れている（JSON構文エラー等）場合は
 * {@link StreamTweaksSettings#DEFAULT} にフォールバックする。
 */
public final class FileStreamTweaksSettingsRepository implements StreamTweaksSettingsRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileStreamTweaksSettingsRepository.class);

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;

    public FileStreamTweaksSettingsRepository(Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public StreamTweaksSettings load() {
        try {
            if (!Files.exists(filePath)) {
                return StreamTweaksSettings.DEFAULT;
            }
            StreamTweaksSettings settings = gson.fromJson(Files.readString(filePath), StreamTweaksSettings.class);
            return settings != null ? settings : StreamTweaksSettings.DEFAULT;
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load Stream Tweaks settings from {}; falling back to defaults", filePath, e);
            return StreamTweaksSettings.DEFAULT;
        }
    }

    @Override
    public void save(StreamTweaksSettings settings) {
        try {
            Files.createDirectories(filePath.getParent());
            var tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(
                    tmp, gson.toJson(settings), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save Stream Tweaks settings to {}", filePath, e);
        }
    }
}
