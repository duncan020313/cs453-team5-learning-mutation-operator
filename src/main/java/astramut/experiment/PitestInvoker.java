package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandResult;
import astramut.experiment.ExperimentTypes.ExperimentSetupException;
import astramut.experiment.ExperimentTypes.ExportedMetadata;
import astramut.experiment.ExperimentTypes.MutatorVariant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class PitestInvoker {
  private final PitestScoreOptions options;
  private final Path repoRoot;
  private final Path java;
  private final Path pitestClasspathFile;
  private final ProcessRunner processRunner;

  PitestInvoker(PitestScoreOptions options, Path repoRoot, ProcessRunner processRunner) {
    this.options = options;
    this.repoRoot = repoRoot;
    this.java = repoRoot.resolve("tools/jdk-11/bin/java");
    this.pitestClasspathFile = options.outDir().resolve("pitest-classpath.txt");
    this.processRunner = processRunner;
  }

  void prepareClasspathFile() throws IOException, ExperimentSetupException {
    Files.writeString(pitestClasspathFile, findPitestClasspath(), StandardCharsets.UTF_8);
  }

  CommandResult runPitest(
      Path workDir,
      Path reportDir,
      ExportedMetadata metadata,
      String targetClass,
      List<String> targetTests,
      Set<String> excludedMethods,
      MutatorVariant variant)
      throws IOException, InterruptedException, ExperimentSetupException {
    String projectClassPath = buildPitestProjectClasspath(workDir, metadata);
    writePitestClasspathReport(reportDir, projectClassPath);

    List<String> command = new ArrayList<>();
    command.add(java.toString());
    command.add("-cp");
    command.add(Files.readString(pitestClasspathFile, StandardCharsets.UTF_8));
    command.add("org.pitest.mutationtest.commandline.MutationCoverageReport");
    command.add("--reportDir");
    command.add(reportDir.toAbsolutePath().normalize().toString());
    command.add("--targetClasses");
    command.add(targetClass + "*");
    command.add("--targetTests");
    command.add(String.join(",", targetTests));
    command.add("--sourceDirs");
    command.add(workDir.resolve(metadata.srcClassesDir()).toAbsolutePath().normalize().toString());
    command.add("--classPath");
    command.add(projectClassPath);
    command.add("--outputFormats");
    command.add("XML");
    variant
        .mutatorsArgument()
        .ifPresent(
            mutators -> {
              command.add("--mutators");
              command.add(mutators);
            });
    command.add("--timestampedReports");
    command.add("false");
    command.add("--failWhenNoMutations");
    command.add("false");
    command.add("--skipFailingTests");
    command.add("true");
    command.add("--threads");
    command.add(Integer.toString(options.threads()));
    command.add("--timeoutFactor");
    command.add(options.timeoutFactor());
    if (!excludedMethods.isEmpty()) {
      command.add("--excludedMethods");
      command.add(String.join(",", excludedMethods));
    }
    return processRunner.run(workDir, command);
  }

  private String buildPitestProjectClasspath(Path workDir, ExportedMetadata metadata)
      throws ExperimentSetupException {
    Set<String> entries = new LinkedHashSet<>();
    entries.add(workDir.resolve(metadata.binClassesDir()).toAbsolutePath().normalize().toString());
    entries.add(workDir.resolve(metadata.binTestsDir()).toAbsolutePath().normalize().toString());

    Path defects4jJunit =
        repoRoot
            .resolve("tools/defects4j/framework/projects/lib/junit-4.12-hamcrest-1.3.jar")
            .toAbsolutePath()
            .normalize();
    if (!Files.isRegularFile(defects4jJunit)) {
      throw new ExperimentSetupException("Defects4J JUnit 4 jar not found at " + defects4jJunit);
    }
    entries.add(defects4jJunit.toString());

    for (String entry : FileUtils.splitClasspath(metadata.cpTest())) {
      Path path = Paths.get(entry);
      Path normalized =
          (path.isAbsolute() ? path : workDir.resolve(path)).toAbsolutePath().normalize();
      if (isBundledJunitJar(normalized, defects4jJunit)) {
        continue;
      }
      entries.add(normalized.toString());
    }
    return String.join(",", entries);
  }

  private void writePitestClasspathReport(Path reportDir, String projectClassPath)
      throws IOException {
    Files.writeString(
        reportDir.resolve("pitest-classpath.txt"),
        String.join(System.lineSeparator(), projectClassPath.split(",")) + System.lineSeparator(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private boolean isBundledJunitJar(Path path, Path defects4jJunit) {
    if (path.equals(defects4jJunit) || path.getFileName() == null) {
      return false;
    }
    String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return fileName.equals("junit.jar")
        || (fileName.startsWith("junit-") && fileName.endsWith(".jar"));
  }

  private String findPitestClasspath() throws IOException, ExperimentSetupException {
    Path cache =
        Paths.get(System.getProperty("user.home"), ".gradle", "caches", "modules-2", "files-2.1");
    List<Path> jars = new ArrayList<>();
    addNewestJar(cache, jars, "org.pitest", "pitest-command-line", options.pitestVersion());
    addNewestJar(cache, jars, "org.pitest", "pitest", options.pitestVersion());
    addNewestJar(cache, jars, "org.pitest", "pitest-entry", options.pitestVersion());
    addNewestJar(cache, jars, "org.pitest", "pitest-html-report", options.pitestVersion());
    addAnyVersionJar(cache, jars, "org.apache.commons", "commons-text");
    addAnyVersionJar(cache, jars, "org.apache.commons", "commons-lang3");
    if (jars.size() < 4) {
      throw new ExperimentSetupException(
          "PIT jars were not found in Gradle cache. Run a Gradle build once to populate PIT"
              + " dependencies.");
    }
    return jars.stream()
        .map(Path::toString)
        .collect(Collectors.joining(System.getProperty("path.separator")));
  }

  private void addNewestJar(
      Path cache, List<Path> jars, String group, String artifact, String version)
      throws IOException {
    Path base = cache.resolve(group).resolve(artifact).resolve(version);
    if (!Files.isDirectory(base)) {
      return;
    }
    try (var stream = Files.walk(base)) {
      stream
          .filter(path -> path.getFileName().toString().endsWith(".jar"))
          .sorted()
          .findFirst()
          .ifPresent(jars::add);
    }
  }

  private void addAnyVersionJar(Path cache, List<Path> jars, String group, String artifact)
      throws IOException {
    Path base = cache.resolve(group).resolve(artifact);
    if (!Files.isDirectory(base)) {
      return;
    }
    try (var stream = Files.walk(base)) {
      stream
          .filter(path -> path.getFileName().toString().endsWith(".jar"))
          .sorted((left, right) -> right.compareTo(left))
          .findFirst()
          .ifPresent(jars::add);
    }
  }
}
