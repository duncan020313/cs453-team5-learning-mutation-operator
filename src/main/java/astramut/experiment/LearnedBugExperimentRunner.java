package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandFailedException;
import astramut.experiment.ExperimentTypes.CommandResult;
import astramut.experiment.ExperimentTypes.ExportedMetadata;
import astramut.experiment.ExperimentTypes.MethodRange;
import astramut.mutation.MutationOperator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LearnedBugExperimentRunner {
  private final LearnedScoreOptions options;
  private final Defects4jClient defects4jClient;
  private final TargetMethodResolver targetMethodResolver;
  private final Map<LearnedOperatorSet, List<MutationOperator>> operatorsBySet;

  LearnedBugExperimentRunner(
      LearnedScoreOptions options,
      Defects4jClient defects4jClient,
      TargetMethodResolver targetMethodResolver,
      Map<LearnedOperatorSet, List<MutationOperator>> operatorsBySet) {
    this.options = options;
    this.defects4jClient = defects4jClient;
    this.targetMethodResolver = targetMethodResolver;
    this.operatorsBySet = Map.copyOf(operatorsBySet);
  }

  List<LearnedSummaryRow> run(LearnedBugTarget target) {
    Instant start = Instant.now();
    String project = target.project();
    int bugId = target.bugId();
    String version = bugId + "f";
    Path bugRoot = options.workDir().resolve(project + "-" + bugId);
    Path fixedWork = bugRoot.resolve("fixed");
    Path buggyWork = bugRoot.resolve("buggy");

    try {
      FileUtils.recreateDirectory(fixedWork);
      FileUtils.recreateDirectory(buggyWork);

      defects4jClient.checkout(project, version, fixedWork);
      defects4jClient.checkout(project, bugId + "b", buggyWork);
      defects4jClient.compile(project, version, fixedWork);

      ExportedMetadata metadata = defects4jClient.exportMetadata(fixedWork);
      Map<String, Set<String>> targetMethods =
          targetMethodResolver.findTargetMethods(
              metadata.modifiedClasses(), fixedWork, buggyWork, metadata.srcClassesDir());
      targetMethods.entrySet().removeIf(entry -> entry.getValue().isEmpty());
      List<String> relevantTests = metadata.relevantTests();

      if (targetMethods.isEmpty()) {
        long seconds = Duration.between(start, Instant.now()).toSeconds();
        return target.operatorSets().stream()
            .map(
                set ->
                    LearnedSummaryRow.skipped(
                        project,
                        bugId,
                        version,
                        set,
                        "SKIPPED_NO_TARGET_METHOD",
                        seconds,
                        selectedOperators(set),
                        modelArchive()))
            .toList();
      }
      if (relevantTests.isEmpty()) {
        long seconds = Duration.between(start, Instant.now()).toSeconds();
        return target.operatorSets().stream()
            .map(
                set ->
                    LearnedSummaryRow.skipped(
                        project,
                        bugId,
                        version,
                        set,
                        "SKIPPED_NO_RELEVANT_TESTS",
                        seconds,
                        selectedOperators(set),
                        modelArchive()))
            .toList();
      }

      List<LearnedSummaryRow> rows = new ArrayList<>();
      for (LearnedOperatorSet set : target.operatorSets()) {
        try {
          rows.add(
              runSet(
                  project, bugId, version, set, fixedWork, metadata, targetMethods, relevantTests));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          long seconds = Duration.between(start, Instant.now()).toSeconds();
          rows.add(
              LearnedSummaryRow.failed(
                  project,
                  bugId,
                  version,
                  set,
                  "Experiment interrupted",
                  seconds,
                  selectedOperators(set),
                  modelArchive()));
          return rows;
        } catch (IOException e) {
          long seconds = Duration.between(start, Instant.now()).toSeconds();
          rows.add(
              LearnedSummaryRow.failed(
                  project,
                  bugId,
                  version,
                  set,
                  e.getMessage(),
                  seconds,
                  selectedOperators(set),
                  modelArchive()));
        }
      }
      return rows;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failedRows(project, bugId, version, target, "Experiment interrupted", start);
    } catch (IOException | CommandFailedException e) {
      return failedRows(project, bugId, version, target, e.getMessage(), start);
    } finally {
      cleanupWorkDir(bugRoot);
    }
  }

  private LearnedSummaryRow runSet(
      String project,
      int bugId,
      String version,
      LearnedOperatorSet set,
      Path fixedWork,
      ExportedMetadata metadata,
      Map<String, Set<String>> targetMethods,
      List<String> relevantTests)
      throws IOException, InterruptedException {
    Instant start = Instant.now();
    LearnedMutationTotals totals = LearnedMutationTotals.empty();
    LearnedMutantEvaluator evaluator = new LearnedMutantEvaluator(options.mutantTimeout());

    for (Map.Entry<String, Set<String>> entry : targetMethods.entrySet()) {
      String className = entry.getKey();
      Path source = targetMethodResolver.sourcePath(fixedWork, metadata.srcClassesDir(), className);
      String originalSource = Files.readString(source, StandardCharsets.UTF_8);
      List<MethodRange> methodRanges = targetRanges(source, entry.getValue());
      SourceHooks hooks =
          new SourceHooks(
              source, originalSource, fixedWork, relevantTests, defects4jClient, evaluator);
      totals =
          totals.add(
              evaluator.evaluate(
                  originalSource,
                  source.getFileName().toString(),
                  methodRanges,
                  operatorsBySet.getOrDefault(set, List.of()),
                  hooks));
    }

    long seconds = Duration.between(start, Instant.now()).toSeconds();
    int methodCount = targetMethods.values().stream().mapToInt(Set::size).sum();
    return LearnedSummaryRow.success(
        project,
        bugId,
        version,
        set,
        targetMethods.size(),
        methodCount,
        relevantTests.size(),
        totals,
        selectedOperators(set),
        seconds,
        modelArchive());
  }

  private List<MethodRange> targetRanges(Path source, Set<String> targetMethodNames)
      throws IOException {
    List<MethodRange> ranges =
        targetMethodResolver.parseMethods(source).stream()
            .filter(method -> targetMethodNames.contains(method.name()))
            .toList();
    if (!ranges.isEmpty()) {
      return ranges;
    }
    return List.of(new MethodRange("*", 1, Integer.MAX_VALUE));
  }

  private List<LearnedSummaryRow> failedRows(
      String project,
      int bugId,
      String version,
      LearnedBugTarget target,
      String message,
      Instant start) {
    long seconds = Duration.between(start, Instant.now()).toSeconds();
    return target.operatorSets().stream()
        .map(
            set ->
                LearnedSummaryRow.failed(
                    project,
                    bugId,
                    version,
                    set,
                    message,
                    seconds,
                    selectedOperators(set),
                    modelArchive()))
        .toList();
  }

  private int selectedOperators(LearnedOperatorSet set) {
    return operatorsBySet.getOrDefault(set, List.of()).size();
  }

  private String modelArchive() {
    return options.modelArchive().toString();
  }

  private void cleanupWorkDir(Path bugRoot) {
    if (options.keepWorkdirs()) {
      return;
    }
    try {
      FileUtils.deleteRecursively(bugRoot);
    } catch (IOException e) {
      System.err.println(
          "[warn] could not delete work directory " + bugRoot + ": " + e.getMessage());
    }
  }

  private static final class SourceHooks implements LearnedMutantEvaluator.Hooks {
    private final Path source;
    private final String originalSource;
    private final Path workDir;
    private final List<String> relevantTests;
    private final Defects4jClient defects4jClient;
    private final LearnedMutantEvaluator evaluator;

    private SourceHooks(
        Path source,
        String originalSource,
        Path workDir,
        List<String> relevantTests,
        Defects4jClient defects4jClient,
        LearnedMutantEvaluator evaluator) {
      this.source = source;
      this.originalSource = originalSource;
      this.workDir = workDir;
      this.relevantTests = relevantTests;
      this.defects4jClient = defects4jClient;
      this.evaluator = evaluator;
    }

    @Override
    public void writeMutant(String mutatedSource) throws IOException {
      Files.writeString(source, mutatedSource, StandardCharsets.UTF_8);
    }

    @Override
    public void restoreOriginal() throws IOException {
      Files.writeString(source, originalSource, StandardCharsets.UTF_8);
    }

    @Override
    public CommandResult compile() throws IOException, InterruptedException {
      return defects4jClient.compileForMutation(workDir);
    }

    @Override
    public CommandResult runRelevantTests() throws IOException, InterruptedException {
      return defects4jClient.runRelevantTests(workDir, relevantTests, evaluator.mutantTimeout());
    }
  }
}
