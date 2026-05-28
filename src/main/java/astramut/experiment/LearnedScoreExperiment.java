package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandFailedException;
import astramut.experiment.ExperimentTypes.ExperimentSetupException;
import astramut.experiment.ExperimentTypes.SummaryFormatException;
import astramut.learn.LearnedModelArchiveExtractor;
import astramut.learn.LearnedPatternEntry;
import astramut.learn.LearnedPatternJsonLoader;
import astramut.mutation.LearnedMutationOperator;
import astramut.mutation.MagicValueSampler;
import astramut.mutation.MutationOperator;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class LearnedScoreExperiment {
  private final LearnedScoreOptions options;
  private final Defects4jClient defects4jClient;
  private final TargetMethodResolver targetMethodResolver;
  private final LearnedSummaryCsvStore summaryStore;

  LearnedScoreExperiment(LearnedScoreOptions options) {
    this.options = options;
    Path repoRoot = Paths.get("").toAbsolutePath().normalize();
    ProcessRunner processRunner = new ProcessRunner(repoRoot);
    this.defects4jClient = new Defects4jClient(repoRoot, processRunner);
    this.targetMethodResolver = new TargetMethodResolver(repoRoot, processRunner);
    this.summaryStore = new LearnedSummaryCsvStore(options.outDir());
  }

  int run()
      throws IOException,
          InterruptedException,
          CommandFailedException,
          ExperimentSetupException,
          SummaryFormatException {
    Files.createDirectories(options.workDir());
    Files.createDirectories(options.outDir());
    Files.createDirectories(options.outDir().resolve("per-bug"));
    defects4jClient.validateEnvironment();

    Map<LearnedOperatorSet, List<MutationOperator>> operatorsBySet = loadOperators();
    LearnedBugExperimentRunner bugRunner =
        new LearnedBugExperimentRunner(
            options, defects4jClient, targetMethodResolver, operatorsBySet);

    Map<String, List<Integer>> targets = resolveTargets();
    Set<String> completed = summaryStore.readCompleted(options.resume());
    boolean appendSummary = summaryStore.shouldAppend(options.resume());
    List<LearnedBugTarget> pendingTargets = pendingTargets(targets, completed);

    try (BufferedWriter writer = summaryStore.openWriter(appendSummary)) {
      summaryStore.ensureHeader(writer, appendSummary);
      runPendingBugs(pendingTargets, writer, bugRunner);
    }

    summaryStore.writeAverage();
    return 0;
  }

  private Map<LearnedOperatorSet, List<MutationOperator>> loadOperators() throws IOException {
    Path modelJson =
        new LearnedModelArchiveExtractor()
            .extract(
                options.modelArchive(),
                options.modelEntry(),
                options.workDir().resolve("model-cache"));
    LearnedPatternJsonLoader loader = new LearnedPatternJsonLoader();
    LearnedPatternJsonLoader.Selection selection =
        new LearnedPatternJsonLoader.Selection(
            options.bugType().orElse(null),
            options.minSupport(),
            options.minSpecificity(),
            options.minCohortRatio());

    // RHS NumberLiteral(__MAGIC__) → corpus-weighted sample at apply time.
    MagicValueSampler magicSampler;
    try {
      magicSampler = MagicValueSampler.loadFromCatalogue(modelJson, 42L);
      System.out.println("[info] loaded magic-value distribution: "
          + magicSampler.size() + " values, total mass=" + magicSampler.total());
    } catch (IOException e) {
      System.err.println("[warn] could not load magic-value distribution: " + e.getMessage());
      magicSampler = MagicValueSampler.empty();
    }

    Map<LearnedOperatorSet, List<MutationOperator>> result = new LinkedHashMap<>();
    for (LearnedOperatorSet set : options.operatorSets()) {
      List<LearnedPatternEntry> entries = loader.selectTop(modelJson, selection, set.limit());
      List<MutationOperator> operators = new ArrayList<>();
      for (int i = 0; i < entries.size(); i++) {
        operators.add(new LearnedMutationOperator(entries.get(i).pattern(), i, magicSampler));
      }
      result.put(set, List.copyOf(operators));
      System.out.println("[info] " + set.name() + " selected operators: " + operators.size());
    }
    return result;
  }

  private Map<String, List<Integer>> resolveTargets()
      throws IOException, InterruptedException, CommandFailedException {
    List<String> projects = options.projects();
    if (projects.isEmpty()) {
      projects = defects4jClient.pids();
    }

    Map<String, List<Integer>> targets = new LinkedHashMap<>();
    for (String project : projects) {
      List<Integer> bugs = options.bugs();
      if (bugs.isEmpty()) {
        bugs = defects4jClient.bids(project);
      }
      targets.put(project, bugs);
    }
    return targets;
  }

  private List<LearnedBugTarget> pendingTargets(
      Map<String, List<Integer>> targets, Set<String> completed) {
    List<LearnedBugTarget> pending = new ArrayList<>();
    for (Map.Entry<String, List<Integer>> entry : targets.entrySet()) {
      String project = entry.getKey();
      for (int bugId : entry.getValue()) {
        List<LearnedOperatorSet> pendingSets =
            options.operatorSets().stream()
                .filter(
                    set ->
                        !completed.contains(
                            LearnedSummaryCsvStore.completedKey(project, bugId, set)))
                .toList();
        if (pendingSets.isEmpty()) {
          System.out.println("[skip] " + project + "-" + bugId + " already completed");
          continue;
        }
        pending.add(new LearnedBugTarget(project, bugId, pendingSets));
      }
    }
    return pending;
  }

  private void runPendingBugs(
      List<LearnedBugTarget> targets, BufferedWriter writer, LearnedBugExperimentRunner bugRunner)
      throws IOException, InterruptedException {
    if (targets.isEmpty()) {
      return;
    }
    if (options.bugThreads() == 1) {
      for (LearnedBugTarget target : targets) {
        summaryStore.writeRows(writer, bugRunner.run(target));
      }
      return;
    }

    int workerCount = Math.min(options.bugThreads(), targets.size());
    System.out.println(
        "[info] running "
            + targets.size()
            + " pending bugs with "
            + workerCount
            + " bug worker threads");
    ExecutorService executor = Executors.newFixedThreadPool(workerCount);
    CompletionService<LearnedBugResult> completion = new ExecutorCompletionService<>(executor);
    try {
      for (LearnedBugTarget target : targets) {
        completion.submit(
            () -> {
              System.out.println("[run] " + target.project() + "-" + target.bugId());
              return new LearnedBugResult(target, bugRunner.run(target));
            });
      }
      executor.shutdown();
      for (int i = 0; i < targets.size(); i++) {
        Future<LearnedBugResult> future = completion.take();
        LearnedBugResult result = getCompletedBug(future);
        summaryStore.writeRows(writer, result.rows());
        System.out.println("[done] " + result.target().project() + "-" + result.target().bugId());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private LearnedBugResult getCompletedBug(Future<LearnedBugResult> future)
      throws InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(cause);
    }
  }

  private record LearnedBugResult(LearnedBugTarget target, List<LearnedSummaryRow> rows) {}
}
