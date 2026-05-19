package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import astramut.experiment.ExperimentTypes.CommandFailedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetMethodResolverTest {
  @TempDir Path tempDir;

  @Test
  void changedNewLinesReadsUnifiedDiffHunks()
      throws java.io.IOException, InterruptedException, CommandFailedException {
    Path oldFile = tempDir.resolve("Old.java");
    Path newFile = tempDir.resolve("New.java");
    Files.writeString(oldFile, "a\nb\nc\n", StandardCharsets.UTF_8);
    Files.writeString(newFile, "a\nB\nc\nd\n", StandardCharsets.UTF_8);
    Path repoRoot = Paths.get("").toAbsolutePath().normalize();
    TargetMethodResolver resolver = new TargetMethodResolver(repoRoot, new ProcessRunner(repoRoot));

    Set<Integer> changedLines = resolver.changedNewLines(oldFile, newFile);

    assertEquals(Set.of(2, 4), changedLines);
  }
}
