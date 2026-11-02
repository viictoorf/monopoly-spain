package monopoly.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import monopoly.game.RuleException;
import monopoly.game.Session;

/** Reads and writes the saved game: one small text file in the user's home folder. */
final class SaveStore {
    private final Path file;

    SaveStore(Path file) {
        this.file = file;
    }

    static SaveStore inHome() {
        return new SaveStore(Path.of(System.getProperty("user.home"), ".monopoly-espana", "partida.txt"));
    }

    /** The saved session, or null if there is none or the file is damaged. */
    Session load() {
        try {
            return Session.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuleException e) {
            return null;
        }
    }

    /** Writes through a temporary file so a crash mid-write never leaves a half-saved game. */
    void save(Session session) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, session.serialize(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("No se pudo guardar la partida: " + e.getMessage());
        }
    }
}
