package nz.co.warehouse.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class AiLocalTokenTest {
    @TempDir Path directory;
    @Test void readsOnlyInternalTokenAndEnvironmentWins() throws Exception {
        Path file=directory.resolve(".env");
        Files.writeString(file,"GEMINI_API_KEY=private-model-key\nAI_INTERNAL_TOKEN='local-shared-token-longer-than-32-characters'\n");
        assertThat(AiLocalToken.resolve("",file)).isEqualTo("local-shared-token-longer-than-32-characters");
        assertThat(AiLocalToken.resolve("environment-token",file)).isEqualTo("environment-token");
    }
    @Test void missingOrIncompleteOptionalConfigurationDoesNotThrow() throws Exception {
        assertThat(AiLocalToken.resolve("",directory.resolve("missing"))).isEmpty();
        Path file=directory.resolve(".env");
        Files.writeString(file,"GEMINI_API_KEY=private-model-key\n");
        assertThat(AiLocalToken.resolve(null,file)).isEmpty();
        assertThat(AiLocalToken.resolve("",directory,file)).isEmpty();
    }
}
