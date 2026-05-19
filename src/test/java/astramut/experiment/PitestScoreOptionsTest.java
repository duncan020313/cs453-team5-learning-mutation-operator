package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class PitestScoreOptionsTest {
  @Test
  void runnerReturnsZeroForHelp() {
    assertEquals(0, new ExperimentRunner().run(new String[] {"--help"}));
  }

  @Test
  void runnerRejectsUnknownExperimentCommand() {
    assertEquals(2, new ExperimentRunner().run(new String[] {"unknown"}));
  }

  @Test
  void runnerRejectsInvalidPitestScoreOptionsBeforeEnvironmentValidation() {
    assertEquals(2, new ExperimentRunner().run(new String[] {"pitest-score", "--threads", "0"}));
  }

  @Test
  void parsesDefaults() {
    PitestScoreOptions options = PitestScoreOptions.parse(new String[] {});

    assertTrue(options.projects().isEmpty());
    assertTrue(options.bugs().isEmpty());
    assertEquals(Paths.get("data/defects4j-pitest/work"), options.workDir());
    assertEquals(Paths.get("data/defects4j-pitest/results"), options.outDir());
    assertEquals(1, options.threads());
    assertEquals(1, options.bugThreads());
    assertEquals("1.25", options.timeoutFactor());
    assertEquals("1.22.1", options.pitestVersion());
    assertTrue(options.resume());
    assertFalse(options.keepWorkdirs());
  }

  @Test
  void parsesProjectsBugRangesAndFlags() {
    PitestScoreOptions options =
        PitestScoreOptions.parse(
            new String[] {
              "--projects",
              "Lang, Math",
              "--bugs",
              "1,3..5",
              "--work-dir",
              "work",
              "--out-dir",
              "out",
              "--threads",
              "2",
              "--bug-threads",
              "3",
              "--timeout-factor",
              "2.0",
              "--pitest-version",
              "1.20.0",
              "--no-resume",
              "--keep-workdirs"
            });

    assertEquals(List.of("Lang", "Math"), options.projects());
    assertEquals(List.of(1, 3, 4, 5), options.bugs());
    assertEquals(Paths.get("work"), options.workDir());
    assertEquals(Paths.get("out"), options.outDir());
    assertEquals(2, options.threads());
    assertEquals(3, options.bugThreads());
    assertEquals("2.0", options.timeoutFactor());
    assertEquals("1.20.0", options.pitestVersion());
    assertFalse(options.resume());
    assertTrue(options.keepWorkdirs());
  }

  @Test
  void rejectsInvalidThreadCount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PitestScoreOptions.parse(new String[] {"--threads", "0"}));
  }

  @Test
  void rejectsMissingOptionValue() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PitestScoreOptions.parse(new String[] {"--projects"}));
  }
}
