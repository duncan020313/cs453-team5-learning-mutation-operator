package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import astramut.experiment.ExperimentTypes.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessRunnerTest {
  @TempDir Path tempDir;

  @Test
  void returnsTimeoutResultAndCapturedOutputWhenProcessExceedsLimit()
      throws IOException, InterruptedException {
    ProcessRunner runner = new ProcessRunner(tempDir);

    CommandResult result =
        runner.run(
            tempDir,
            List.of("sh", "-c", "printf started; sleep 5; printf finished"),
            Duration.ofMillis(100));

    assertTrue(result.timedOut());
    assertEquals(-1, result.exitCode());
    assertTrue(result.output().contains("started"), result.output());
  }

  @Test
  void returnsNormalResultBeforeTimeout() throws IOException, InterruptedException {
    ProcessRunner runner = new ProcessRunner(tempDir);

    CommandResult result =
        runner.run(tempDir, List.of("sh", "-c", "printf ok"), Duration.ofSeconds(5));

    assertEquals(0, result.exitCode());
    assertEquals("ok", result.output());
    assertTrue(!result.timedOut());
  }
}
