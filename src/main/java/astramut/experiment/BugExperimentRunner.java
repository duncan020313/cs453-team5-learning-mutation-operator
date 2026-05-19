package astramut.experiment;

import astramut.experiment.ExperimentTypes.BugTarget;
import astramut.experiment.ExperimentTypes.CommandFailedException;
import astramut.experiment.ExperimentTypes.CommandResult;
import astramut.experiment.ExperimentTypes.ExperimentSetupException;
import astramut.experiment.ExperimentTypes.ExportedMetadata;
import astramut.experiment.ExperimentTypes.MethodRange;
import astramut.experiment.ExperimentTypes.MutatorVariant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.xml.sax.SAXException;

final class BugExperimentRunner {
  private final PitestScoreOptions options;
  private final Defects4jClient defects4jClient;
  private final TargetMethodResolver targetMethodResolver;
  private final PitestInvoker pitestInvoker;
  private final PitestXmlReport pitestXmlReport;

  BugExperimentRunner(
      PitestScoreOptions options,
      Defects4jClient defects4jClient,
      TargetMethodResolver targetMethodResolver,
      PitestInvoker pitestInvoker,
      PitestXmlReport pitestXmlReport) {
    this.options = options;
    this.defects4jClient = defects4jClient;
    this.targetMethodResolver = targetMethodResolver;
    this.pitestInvoker = pitestInvoker;
    this.pitestXmlReport = pitestXmlReport;
  }

  List<SummaryRow> run(BugTarget target) {
    Instant start = Instant.now();
    String project = target.project();
    int bugId = target.bugId();
    String version = bugId + "f";
    Path bugRoot = options.workDir().resolve(project + "-" + bugId);
    Path fixedWork = bugRoot.resolve("fixed");
    Path buggyWork = bugRoot.resolve("buggy");
    Path bugReportDir =
        options.outDir().resolve("per-bug").resolve(project).resolve(Integer.toString(bugId));

    try {
      Files.createDirectories(bugReportDir);
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
        return target.variants().stream()
            .map(
                variant ->
                    SummaryRow.skipped(
                        project, bugId, version, variant, "SKIPPED_NO_TARGET_METHOD", seconds))
            .toList();
      }
      if (relevantTests.isEmpty()) {
        long seconds = Duration.between(start, Instant.now()).toSeconds();
        return target.variants().stream()
            .map(
                variant ->
                    SummaryRow.skipped(
                        project, bugId, version, variant, "SKIPPED_NO_RELEVANT_TESTS", seconds))
            .toList();
      }

      List<SummaryRow> rows = new ArrayList<>();
      for (MutatorVariant variant : target.variants()) {
        try {
          rows.add(
              runVariant(
                  project,
                  bugId,
                  version,
                  variant,
                  bugReportDir,
                  fixedWork,
                  metadata,
                  targetMethods,
                  relevantTests));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          long seconds = Duration.between(start, Instant.now()).toSeconds();
          rows.add(
              SummaryRow.failed(
                  project, bugId, version, variant, "Experiment interrupted", seconds));
          return rows;
        }
      }
      return rows;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failedRows(
          project, bugId, version, target.variants(), "Experiment interrupted", start);
    } catch (IOException | CommandFailedException e) {
      return failedRows(project, bugId, version, target.variants(), e.getMessage(), start);
    } finally {
      cleanupWorkDir(bugRoot);
    }
  }

  private SummaryRow runVariant(
      String project,
      int bugId,
      String version,
      MutatorVariant variant,
      Path bugReportDir,
      Path fixedWork,
      ExportedMetadata metadata,
      Map<String, Set<String>> targetMethods,
      List<String> relevantTests)
      throws InterruptedException {
    Instant variantStart = Instant.now();
    Path reportDir = bugReportDir.resolve(variant.directoryName());
    Path log = reportDir.resolve("pitest.log");

    try {
      Files.createDirectories(reportDir);
      Files.writeString(
          log,
          "",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
      MutationTotals totals = new MutationTotals();

      for (Map.Entry<String, Set<String>> entry : targetMethods.entrySet()) {
        String className = entry.getKey();
        Set<String> methods = entry.getValue();
        List<MethodRange> allMethods =
            targetMethodResolver.parseMethods(
                targetMethodResolver.sourcePath(fixedWork, metadata.srcClassesDir(), className));
        Set<String> excluded =
            allMethods.stream()
                .map(MethodRange::name)
                .filter(name -> !methods.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));

        Path classReportDir = reportDir.resolve(FileUtils.safeName(className));
        Files.createDirectories(classReportDir);
        CommandResult pit =
            pitestInvoker.runPitest(
                fixedWork, classReportDir, metadata, className, relevantTests, excluded, variant);
        appendPitestOutput(log, className, pit);
        if (pit.exitCode() != 0) {
          throw new CommandFailedException("PIT failed for " + className + ". See " + log, pit);
        }
        totals.add(pitestXmlReport.parseMutationTotals(classReportDir, methods));
      }

      pitestXmlReport.copyCombinedMutationXml(reportDir, targetMethods);
      long seconds = Duration.between(variantStart, Instant.now()).toSeconds();
      return SummaryRow.success(
          project, bugId, version, variant, targetMethods, relevantTests, totals, seconds);
    } catch (InterruptedException e) {
      throw e;
    } catch (IOException
        | CommandFailedException
        | ExperimentSetupException
        | ParserConfigurationException
        | SAXException
        | TransformerException e) {
      logVariantFailure(reportDir, log, e);
      long seconds = Duration.between(variantStart, Instant.now()).toSeconds();
      return SummaryRow.failed(project, bugId, version, variant, e.getMessage(), seconds);
    }
  }

  private void appendPitestOutput(Path log, String className, CommandResult pit)
      throws IOException {
    Files.writeString(
        log,
        "== " + className + " ==\n" + pit.output() + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }

  private void logVariantFailure(Path reportDir, Path log, Exception failure) {
    try {
      Files.createDirectories(reportDir);
      Files.writeString(
          log,
          "[error] " + failure.getMessage() + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException logFailure) {
      // Keep the original PIT failure in the summary row.
    }
  }

  private List<SummaryRow> failedRows(
      String project,
      int bugId,
      String version,
      List<MutatorVariant> variants,
      String message,
      Instant start) {
    long seconds = Duration.between(start, Instant.now()).toSeconds();
    return variants.stream()
        .map(variant -> SummaryRow.failed(project, bugId, version, variant, message, seconds))
        .toList();
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
}
