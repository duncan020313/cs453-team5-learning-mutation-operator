package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandFailedException;
import astramut.experiment.ExperimentTypes.CommandResult;
import astramut.experiment.ExperimentTypes.ExperimentSetupException;
import astramut.experiment.ExperimentTypes.ExportedMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

final class Defects4jClient {
  private final Path repoRoot;
  private final Path defects4j;
  private final Path java;
  private final ProcessRunner processRunner;

  Defects4jClient(Path repoRoot, ProcessRunner processRunner) {
    this.repoRoot = repoRoot;
    this.defects4j = repoRoot.resolve("tools/defects4j/framework/bin/defects4j");
    this.java = repoRoot.resolve("tools/jdk-11/bin/java");
    this.processRunner = processRunner;
  }

  void validateEnvironment() throws IOException, InterruptedException, ExperimentSetupException {
    Path env = repoRoot.resolve("tools/defects4j-env.sh");
    if (!Files.isRegularFile(env)) {
      throw new ExperimentSetupException(
          "tools/defects4j-env.sh is missing. Run ./setup-defects4j, then source"
              + " tools/defects4j-env.sh.");
    }
    if (!Files.isExecutable(defects4j)) {
      throw new ExperimentSetupException("defects4j executable not found at " + defects4j);
    }
    if (!Files.isExecutable(java)) {
      throw new ExperimentSetupException("Java 11 executable not found at " + java);
    }
    CommandResult result =
        processRunner.run(repoRoot, List.of(defects4j.toString(), "info", "-p", "Lang"));
    if (result.exitCode() != 0) {
      throw new ExperimentSetupException(
          "Defects4J is not ready. Run ./setup-defects4j and source tools/defects4j-env.sh.\n"
              + result.output());
    }
  }

  List<String> pids() throws IOException, InterruptedException, CommandFailedException {
    CommandResult result = processRunner.run(repoRoot, List.of(defects4j.toString(), "pids"));
    CommandFailedException.throwIfFailed("defects4j pids", result);
    return FileUtils.nonBlankLines(result.output());
  }

  List<Integer> bids(String project)
      throws IOException, InterruptedException, CommandFailedException {
    CommandResult result =
        processRunner.run(repoRoot, List.of(defects4j.toString(), "bids", "-p", project));
    CommandFailedException.throwIfFailed("defects4j bids -p " + project, result);
    return FileUtils.nonBlankLines(result.output()).stream().map(Integer::parseInt).toList();
  }

  void checkout(String project, String version, Path workDir)
      throws IOException, InterruptedException, CommandFailedException {
    CommandResult result =
        processRunner.run(
            repoRoot,
            List.of(
                defects4j.toString(),
                "checkout",
                "-p",
                project,
                "-v",
                version,
                "-w",
                workDir.toString()));
    CommandFailedException.throwIfFailed("checkout " + project + "-" + version, result);
  }

  void compile(String project, String version, Path workDir)
      throws IOException, InterruptedException, CommandFailedException {
    CommandResult result = processRunner.run(workDir, List.of(defects4j.toString(), "compile"));
    CommandFailedException.throwIfFailed("defects4j compile " + project + "-" + version, result);
  }

  CommandResult compileForMutation(Path workDir) throws IOException, InterruptedException {
    return processRunner.run(workDir, List.of(defects4j.toString(), "compile"));
  }

  CommandResult runRelevantTests(Path workDir, List<String> relevantTests, Duration timeout)
      throws IOException, InterruptedException {
    Path suite = workDir.resolve(".astramut-relevant-tests");
    Files.write(suite, relevantTests);
    return processRunner.run(
        workDir,
        List.of(defects4j.toString(), "test", "-s", suite.toAbsolutePath().normalize().toString()),
        timeout);
  }

  ExportedMetadata exportMetadata(Path workDir)
      throws IOException, InterruptedException, CommandFailedException {
    String srcClasses = export(workDir, "dir.src.classes").get(0);
    String binClasses = export(workDir, "dir.bin.classes").get(0);
    String binTests = export(workDir, "dir.bin.tests").get(0);
    String cpTest = String.join(System.getProperty("path.separator"), export(workDir, "cp.test"));
    List<String> modifiedClasses = export(workDir, "classes.modified");
    List<String> relevantTests = export(workDir, "tests.relevant");
    return new ExportedMetadata(
        srcClasses, binClasses, binTests, cpTest, modifiedClasses, relevantTests);
  }

  private List<String> export(Path workDir, String property)
      throws IOException, InterruptedException, CommandFailedException {
    CommandResult result =
        processRunner.run(workDir, List.of(defects4j.toString(), "export", "-p", property));
    CommandFailedException.throwIfFailed("defects4j export -p " + property, result);
    return FileUtils.defects4jExportLines(result.output());
  }
}
