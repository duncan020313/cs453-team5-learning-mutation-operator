package astramut.experiment;

import astramut.experiment.ExperimentTypes.CommandFailedException;
import astramut.experiment.ExperimentTypes.CommandResult;
import astramut.experiment.ExperimentTypes.MethodRange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class TargetMethodResolver {
  private static final Pattern DIFF_HUNK =
      Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*$");

  private final Path repoRoot;
  private final ProcessRunner processRunner;
  private final JavaSourceMethodParser methodParser;

  TargetMethodResolver(Path repoRoot, ProcessRunner processRunner) {
    this.repoRoot = repoRoot;
    this.processRunner = processRunner;
    this.methodParser = new JavaSourceMethodParser();
  }

  Map<String, Set<String>> findTargetMethods(
      List<String> modifiedClasses, Path fixedWork, Path buggyWork, String srcClassesDir)
      throws IOException, InterruptedException, CommandFailedException {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    for (String className : modifiedClasses) {
      Path fixedSource = sourcePath(fixedWork, srcClassesDir, className);
      Path buggySource = sourcePath(buggyWork, srcClassesDir, className);
      if (!Files.isRegularFile(fixedSource) || !Files.isRegularFile(buggySource)) {
        result.put(className, Set.of());
        continue;
      }

      Set<Integer> changedLines = changedNewLines(buggySource, fixedSource);
      List<MethodRange> methods = methodParser.parseMethods(fixedSource);
      Set<String> names =
          methods.stream()
              .filter(method -> method.intersects(changedLines))
              .map(MethodRange::name)
              .collect(Collectors.toCollection(TreeSet::new));
      if (names.isEmpty() && !changedLines.isEmpty()) {
        names.addAll(methodParser.findMethodsByBackwardScan(fixedSource, changedLines));
      }
      result.put(className, names);
    }
    return result;
  }

  List<MethodRange> parseMethods(Path source) throws IOException {
    return methodParser.parseMethods(source);
  }

  Path sourcePath(Path workDir, String srcClassesDir, String className) {
    return workDir.resolve(srcClassesDir).resolve(className.replace('.', '/') + ".java");
  }

  Set<Integer> changedNewLines(Path oldFile, Path newFile)
      throws IOException, InterruptedException, CommandFailedException {
    CommandResult result =
        processRunner.run(repoRoot, List.of("diff", "-U0", oldFile.toString(), newFile.toString()));
    if (result.exitCode() != 0 && result.exitCode() != 1) {
      throw new CommandFailedException("diff", result);
    }

    Set<Integer> lines = new TreeSet<>();
    int newLine = -1;
    for (String line : result.output().split("\\R")) {
      Matcher matcher = DIFF_HUNK.matcher(line);
      if (matcher.matches()) {
        newLine = Integer.parseInt(matcher.group(1));
        continue;
      }
      if (newLine < 0 || line.startsWith("---") || line.startsWith("+++")) {
        continue;
      }
      if (line.startsWith("+")) {
        lines.add(newLine);
        newLine++;
      } else if (line.startsWith("-")) {
        lines.add(newLine);
      } else {
        newLine++;
      }
    }
    return lines;
  }
}
