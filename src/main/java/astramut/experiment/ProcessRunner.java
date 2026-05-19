package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class ProcessRunner {
  private final Path repoRoot;

  ProcessRunner(Path repoRoot) {
    this.repoRoot = repoRoot;
  }

  CommandResult run(Path workDir, List<String> command) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(workDir.toFile());
    builder.redirectErrorStream(true);
    configureEnvironment(builder.environment());

    Process process = builder.start();
    StringBuilder output = new StringBuilder();
    try (BufferedReader stdout =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = stdout.readLine()) != null) {
        output.append(line).append(System.lineSeparator());
      }
    }

    try {
      return new CommandResult(process.waitFor(), output.toString());
    } catch (InterruptedException e) {
      process.destroyForcibly();
      throw e;
    }
  }

  private void configureEnvironment(Map<String, String> env) {
    env.put("JAVA_HOME", repoRoot.resolve("tools/jdk-11").toString());
    env.put("DEFECTS4J_HOME", repoRoot.resolve("tools/defects4j").toString());
    env.put("TZ", "America/Los_Angeles");
    env.put(
        "PATH",
        repoRoot.resolve("tools/jdk-11/bin")
            + System.getProperty("path.separator")
            + repoRoot.resolve("tools/perl5/bin")
            + System.getProperty("path.separator")
            + repoRoot.resolve("tools/defects4j/framework/bin")
            + System.getProperty("path.separator")
            + env.getOrDefault("PATH", ""));

    String perl5 = repoRoot.resolve("tools/perl5/lib/perl5").toString();
    env.put("PERL5LIB", perl5 + (env.containsKey("PERL5LIB") ? ":" + env.get("PERL5LIB") : ""));
  }
}
