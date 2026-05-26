package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import astramut.experiment.ExperimentTypes.CommandResult;
import astramut.experiment.ExperimentTypes.MethodRange;
import astramut.learn.EditPattern;
import astramut.learn.Hole;
import astramut.mutation.Mutant;
import astramut.mutation.MutationOperator;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class LearnedMutantEvaluatorTest {
  @Test
  void excludesCompileFailuresAndCountsTimeoutsAsKilled() throws IOException, InterruptedException {
    String original = "class C { void f() { int x = 1; } }";
    List<Mutant> mutants =
        List.of(
            mutant("compile", 1),
            mutant("timeout", 1),
            mutant("killed", 1),
            mutant("survived", 1),
            mutant("survived", 1),
            mutant("outside", 99));
    SequenceHooks hooks = new SequenceHooks();
    LearnedMutantEvaluator evaluator = new LearnedMutantEvaluator(Duration.ofSeconds(1));

    LearnedMutationTotals totals =
        evaluator.evaluate(
            original,
            "C.java",
            List.of(new MethodRange("f", 1, 10)),
            List.of(new StubOperator(mutants)),
            hooks);

    assertEquals(4, totals.sourceMutants());
    assertEquals(1, totals.duplicateMutants());
    assertEquals(1, totals.compileFailed());
    assertEquals(3, totals.generated());
    assertEquals(2, totals.killed());
    assertEquals(1, totals.survived());
    assertEquals(1, totals.testFailed());
    assertEquals(1, totals.timeoutKilled());
    assertEquals(4, hooks.restoreCount);
  }

  @Test
  void evaluatesAllTargetMutantsWithoutCountLimit()
      throws IOException, InterruptedException {
    String original = "class C { void before() {} void target() { int x = 1; } }";
    List<Mutant> mutants =
        List.of(
            mutant("outside-1", 1),
            mutant("outside-2", 2),
            mutant("outside-3", 3),
            mutant("inside-1", 20),
            mutant("inside-2", 21),
            mutant("inside-3", 22));
    SequenceHooks hooks = new SequenceHooks();
    LearnedMutantEvaluator evaluator = new LearnedMutantEvaluator(Duration.ofSeconds(1));
    StubOperator operator = new StubOperator(mutants);

    LearnedMutationTotals totals =
        evaluator.evaluate(
            original,
            "C.java",
            List.of(new MethodRange("target", 20, 30)),
            List.of(operator),
            hooks);

    assertEquals(Integer.MAX_VALUE, operator.lastMaxMutants);
    assertEquals(3, totals.sourceMutants());
    assertEquals(3, totals.generated());
    assertEquals(3, totals.survived());
  }

  private static Mutant mutant(String marker, int lineNumber) {
    return new Mutant(
        "id-" + marker,
        "learned",
        "C.java",
        "class C { void f() { " + marker + "(); } }",
        0,
        lineNumber);
  }

  private static final class StubOperator implements MutationOperator {
    private final List<Mutant> mutants;
    private int lastMaxMutants = -1;

    private StubOperator(List<Mutant> mutants) {
      this.mutants = mutants;
    }

    @Override
    public String name() {
      return "stub";
    }

    @Override
    public EditPattern pattern() {
      return new EditPattern(new Hole("?x"), new Hole("?x"));
    }

    @Override
    public List<Mutant> generateMutants(String sourceCode, String sourceName, int maxMutants) {
      return mutants.stream().limit(maxMutants).toList();
    }

    @Override
    public List<Mutant> generateMutants(
        String sourceCode, String sourceName, int maxMutants, Predicate<Mutant> mutantFilter) {
      lastMaxMutants = maxMutants;
      return mutants.stream().filter(mutantFilter).limit(maxMutants).toList();
    }
  }

  private static final class SequenceHooks implements LearnedMutantEvaluator.Hooks {
    private String currentSource;
    private int restoreCount;
    private final List<String> compiled = new ArrayList<>();

    @Override
    public void writeMutant(String mutatedSource) {
      currentSource = mutatedSource;
    }

    @Override
    public void restoreOriginal() {
      restoreCount++;
      currentSource = null;
    }

    @Override
    public CommandResult compile() {
      compiled.add(currentSource);
      if (currentSource.contains("compile")) {
        return new CommandResult(1, "compile failed");
      }
      return new CommandResult(0, "compiled");
    }

    @Override
    public CommandResult runRelevantTests() {
      if (currentSource.contains("timeout")) {
        return new CommandResult(-1, "timed out", true);
      }
      if (currentSource.contains("killed")) {
        return new CommandResult(1, "test failed");
      }
      return new CommandResult(0, "ok");
    }
  }
}
