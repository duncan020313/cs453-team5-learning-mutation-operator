package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class ProcessRunner {
  private final Path repoRoot;

  ProcessRunner(Path repoRoot) {
    this.repoRoot = repoRoot;
  }

  CommandResult run(Path workDir, List<String> command) throws IOException, InterruptedException {
    return runInternal(workDir, command, null);
  }

  CommandResult run(Path workDir, List<String> command, Duration timeout)
      throws IOException, InterruptedException {
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    return runInternal(workDir, command, timeout);
  }

  private CommandResult runInternal(Path workDir, List<String> command, Duration timeout)
      throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(workDir.toFile());
    builder.redirectErrorStream(true);
    configureEnvironment(builder.environment());

    Process process = builder.start();
    ExecutorService outputReader = Executors.newSingleThreadExecutor();
    Future<String> output = outputReader.submit(() -> readOutput(process));

    try {
      if (timeout == null) {
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, collectOutput(output));
      }
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (finished) {
        return new CommandResult(process.exitValue(), collectOutput(output));
      }
      destroyProcessTree(process);
      process.waitFor();
      return new CommandResult(-1, collectOutput(output), true);
    } catch (InterruptedException e) {
      destroyProcessTree(process);
      throw e;
    } finally {
      outputReader.shutdownNow();
    }
  }

  private String readOutput(Process process) throws IOException {
    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }

  private String collectOutput(Future<String> output) throws IOException, InterruptedException {
    try {
      return output.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException ioException) {
        throw ioException;
      }
      throw new IOException("Failed to read process output", cause);
    }
  }

  private void destroyProcessTree(Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
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
