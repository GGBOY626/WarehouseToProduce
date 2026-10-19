package nz.co.warehouse.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads only the shared credential; Gemini credentials never enter Java configuration. */
final class AiLocalToken {
    private AiLocalToken() {}

    static String resolve(String configured, Path... candidates) {
        if (configured != null && !configured.isBlank()) return configured.trim();
        for (Path path : candidates) {
            try {
                if (!Files.isRegularFile(path)) continue;
                for (String line : Files.readAllLines(path)) {
                    String[] pair = line.split("=", 2);
                    if (pair.length != 2 || !pair[0].trim().equals("AI_INTERNAL_TOKEN")) continue;
                    String value = pair[1].trim();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            } catch (IOException | SecurityException ignored) {
                // Optional AI configuration must never prevent ordinary business startup.
            }
        }
        return "";
    }
}
