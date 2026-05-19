package astramut.experiment;

import astramut.experiment.ExperimentTypes.BugResult;
import astramut.experiment.ExperimentTypes.BugTarget;
import astramut.experiment.ExperimentTypes.CommandFailedException;
import astramut.experiment.ExperimentTypes.ExperimentSetupException;
import astramut.experiment.ExperimentTypes.MutatorVariant;
import astramut.experiment.ExperimentTypes.SummaryFormatException;
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

final class PitestScoreExperiment {
  private final PitestScoreOptions options;
  private final Defects4jClient defects4jClient;
  private final PitestInvoker pitestInvoker;
  private final BugExperimentRunner bugExperimentRunner;
  private final SummaryCsvStore summaryStore;

  PitestScoreExperiment(PitestScoreOptions options) {
    this.options = options;
    Path repoRoot = Paths.get("").toAbsolutePath().normalize();
    ProcessRunner processRunner = new ProcessRunner(repoRoot);
    TargetMethodResolver targetMethodResolver = new TargetMethodResolver(repoRoot, processRunner);
    PitestXmlReport pitestXmlReport = new PitestXmlReport();

    this.defects4jClient = new Defects4jClient(repoRoot, processRunner);
    this.pitestInvoker = new PitestInvoker(options, repoRoot, processRunner);
    this.bugExperimentRunner =
        new BugExperimentRunner(
            options, defects4jClient, targetMethodResolver, pitestInvoker, pitestXmlReport);
    this.summaryStore = new SummaryCsvStore(options.outDir());
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
    pitestInvoker.prepareClasspathFile();

    Map<String, List<Integer>> targets = resolveTargets();
    if (options.resume()) {
      summaryStore.migrateSummary();
    }
    Set<String> completed = summaryStore.readCompleted(options.resume());
    boolean appendSummary = summaryStore.shouldAppend(options.resume());
    List<BugTarget> pendingTargets = pendingTargets(targets, completed);

    try (BufferedWriter writer = summaryStore.openWriter(appendSummary)) {
      summaryStore.ensureHeader(writer, appendSummary);
      runPendingBugs(pendingTargets, writer);
    }

    summaryStore.writeAverage();
    return 0;
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

  private List<BugTarget> pendingTargets(
      Map<String, List<Integer>> targets, Set<String> completed) {
    List<BugTarget> pending = new ArrayList<>();
    for (Map.Entry<String, List<Integer>> entry : targets.entrySet()) {
      String project = entry.getKey();
      for (int bugId : entry.getValue()) {
        List<MutatorVariant> pendingVariants =
            MutatorVariant.ALL_VARIANTS.stream()
                .filter(
                    variant ->
                        !completed.contains(SummaryCsvStore.completedKey(project, bugId, variant)))
                .toList();
        if (pendingVariants.isEmpty()) {
          System.out.println("[skip] " + project + "-" + bugId + " already completed");
          continue;
        }
        pending.add(new BugTarget(project, bugId, pendingVariants));
      }
    }
    return pending;
  }

  private void runPendingBugs(List<BugTarget> targets, BufferedWriter writer)
      throws IOException, InterruptedException {
    if (targets.isEmpty()) {
      return;
    }
    if (options.bugThreads() == 1) {
      for (BugTarget target : targets) {
        summaryStore.writeRows(writer, bugExperimentRunner.run(target));
      }
      return;
    }

    int workerCount = Math.min(options.bugThreads(), targets.size());
    System.out.println(
        "[info] running "
            + targets.size()
            + " pending bugs with "
            + workerCount
            + " bug worker threads and "
            + options.threads()
            + " PIT worker threads each");
    ExecutorService executor = Executors.newFixedThreadPool(workerCount);
    CompletionService<BugResult> completion = new ExecutorCompletionService<>(executor);
    try {
      for (BugTarget target : targets) {
        completion.submit(
            () -> {
              System.out.println("[run] " + target.project() + "-" + target.bugId());
              return new BugResult(target, bugExperimentRunner.run(target));
            });
      }
      executor.shutdown();
      for (int i = 0; i < targets.size(); i++) {
        Future<BugResult> future = completion.take();
        BugResult result = getCompletedBug(future);
        summaryStore.writeRows(writer, result.rows());
        System.out.println("[done] " + result.target().project() + "-" + result.target().bugId());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private BugResult getCompletedBug(Future<BugResult> future) throws InterruptedException {
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
}
