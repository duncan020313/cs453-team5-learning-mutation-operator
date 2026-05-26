package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearnedScoreOptionsTest {
  @Test
  void runnerRejectsInvalidLearnedScoreOptionsBeforeEnvironmentValidation() {
    assertEquals(
        2, new ExperimentRunner().run(new String[] {"learned-score", "--min-support", "-1"}));
  }

  @Test
  void rejectsRemovedPerOperatorLimitOption() {
    assertThrows(
        IllegalArgumentException.class,
        () -> LearnedScoreOptions.parse(new String[] {"--max-mutants-per-operator", "3"}));
  }

  @Test
  void rejectsRemovedPerClassLimitOption() {
    assertThrows(
        IllegalArgumentException.class,
        () -> LearnedScoreOptions.parse(new String[] {"--max-mutants-per-class", "50"}));
  }

  @Test
  void parsesDefaults() {
    LearnedScoreOptions options = LearnedScoreOptions.parse(new String[] {});

    assertTrue(options.projects().isEmpty());
    assertTrue(options.bugs().isEmpty());
    assertEquals(Paths.get("data/defects4j-learned/work"), options.workDir());
    assertEquals(Paths.get("data/defects4j-learned/results"), options.outDir());
    assertEquals(1, options.bugThreads());
    assertTrue(options.resume());
    assertFalse(options.keepWorkdirs());
    assertEquals(Paths.get("learned_260520.tar.gz"), options.modelArchive());
    assertEquals("learned/patterns-full.json", options.modelEntry());
    assertEquals(
        List.of(LearnedOperatorSet.LEARNED_TOP_1000, LearnedOperatorSet.LEARNED_TOP_100),
        options.operatorSets());
    assertEquals(2, options.minSupport());
    assertEquals(0.0, options.minSpecificity());
    assertEquals(0.0, options.minCohortRatio());
    assertEquals(Duration.ofSeconds(300), options.mutantTimeout());
  }

  @Test
  void parsesSinglePresetAndFilters() {
    LearnedScoreOptions options =
        LearnedScoreOptions.parse(
            new String[] {
              "--projects",
              "Lang,Math",
              "--bugs",
              "1,3..4",
              "--work-dir",
              "work",
              "--out-dir",
              "out",
              "--bug-threads",
              "2",
              "--no-resume",
              "--keep-workdirs",
              "--model-archive",
              "model.tar.gz",
              "--model-entry",
              "dir/model.json",
              "--preset",
              "top100",
              "--bug-type",
              "CHANGE_OPERATOR",
              "--min-support",
              "7",
              "--min-specificity",
              "0.25",
              "--min-cohort-ratio",
              "0.10",
              "--mutant-timeout-seconds",
              "12"
            });

    assertEquals(List.of("Lang", "Math"), options.projects());
    assertEquals(List.of(1, 3, 4), options.bugs());
    assertEquals(Paths.get("work"), options.workDir());
    assertEquals(Paths.get("out"), options.outDir());
    assertEquals(2, options.bugThreads());
    assertFalse(options.resume());
    assertTrue(options.keepWorkdirs());
    assertEquals(Paths.get("model.tar.gz"), options.modelArchive());
    assertEquals("dir/model.json", options.modelEntry());
    assertEquals(List.of(LearnedOperatorSet.LEARNED_TOP_100), options.operatorSets());
    assertEquals("CHANGE_OPERATOR", options.bugType().orElseThrow());
    assertEquals(7, options.minSupport());
    assertEquals(0.25, options.minSpecificity());
    assertEquals(0.10, options.minCohortRatio());
    assertEquals(Duration.ofSeconds(12), options.mutantTimeout());
  }

  @Test
  void rejectsInvalidPreset() {
    assertThrows(
        IllegalArgumentException.class,
        () -> LearnedScoreOptions.parse(new String[] {"--preset", "top100,bad"}));
  }

  @Test
  void rejectsInvalidNumericOptions() {
    assertThrows(
        IllegalArgumentException.class,
        () -> LearnedScoreOptions.parse(new String[] {"--bug-threads", "0"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> LearnedScoreOptions.parse(new String[] {"--min-specificity", "-0.1"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> LearnedScoreOptions.parse(new String[] {"--mutant-timeout-seconds", "0"}));
  }
}
