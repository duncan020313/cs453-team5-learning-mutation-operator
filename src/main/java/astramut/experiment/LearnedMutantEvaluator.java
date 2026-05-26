package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandResult;
import astramut.experiment.ExperimentTypes.MethodRange;
import astramut.mutation.Mutant;
import astramut.mutation.MutationOperator;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LearnedMutantEvaluator {
  private final Duration mutantTimeout;

  LearnedMutantEvaluator(Duration mutantTimeout) {
    this.mutantTimeout = mutantTimeout;
  }

  LearnedMutationTotals evaluate(
      String originalSource,
      String sourceName,
      List<MethodRange> targetMethods,
      List<MutationOperator> operators,
      Hooks hooks)
      throws IOException, InterruptedException {
    LearnedMutationTotals.Builder totals = LearnedMutationTotals.builder();
    Set<String> seenMutatedSources = new LinkedHashSet<>();

    for (MutationOperator operator : operators) {
      List<Mutant> mutants =
          operator
              .generateMutants(
                  originalSource,
                  sourceName,
                  Integer.MAX_VALUE,
                  mutant -> isInTargetMethod(mutant, targetMethods))
              .stream()
              .toList();
      for (Mutant mutant : mutants) {
        if (mutant.mutatedSource().equals(originalSource)
            || !seenMutatedSources.add(mutant.mutatedSource())) {
          totals.incrementDuplicateMutants();
          continue;
        }

        totals.incrementSourceMutants();
        hooks.writeMutant(mutant.mutatedSource());
        try {
          CommandResult compile = hooks.compile();
          if (compile.exitCode() != 0 || compile.timedOut()) {
            totals.incrementCompileFailed();
            continue;
          }

          totals.incrementGenerated();
          CommandResult tests = hooks.runRelevantTests();
          if (tests.timedOut()) {
            totals.incrementKilled();
            totals.incrementTimeoutKilled();
          } else if (tests.exitCode() == 0) {
            totals.incrementSurvived();
          } else {
            totals.incrementKilled();
            totals.incrementTestFailed();
          }
        } finally {
          hooks.restoreOriginal();
        }
      }
    }

    return totals.build();
  }

  Duration mutantTimeout() {
    return mutantTimeout;
  }

  private boolean isInTargetMethod(Mutant mutant, List<MethodRange> targetMethods) {
    if (mutant.lineNumber() < 1) {
      return true;
    }
    return targetMethods.stream()
        .anyMatch(
            method ->
                mutant.lineNumber() >= method.startLine()
                    && mutant.lineNumber() <= method.endLine());
  }

  interface Hooks {
    void writeMutant(String mutatedSource) throws IOException;

    void restoreOriginal() throws IOException;

    CommandResult compile() throws IOException, InterruptedException;

    CommandResult runRelevantTests() throws IOException, InterruptedException;
  }
}
