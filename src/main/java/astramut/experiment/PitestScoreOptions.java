package astramut.experiment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record PitestScoreOptions(
    List<String> projects,
    List<Integer> bugs,
    Path workDir,
    Path outDir,
    int threads,
    int bugThreads,
    String timeoutFactor,
    String pitestVersion,
    boolean resume,
    boolean keepWorkdirs) {
  PitestScoreOptions {
    projects = List.copyOf(projects);
    bugs = List.copyOf(bugs);
  }

  static PitestScoreOptions parse(String[] args) {
    List<String> projects = List.of();
    List<Integer> bugs = List.of();
    Path workDir = Paths.get("data/defects4j-pitest/work");
    Path outDir = Paths.get("data/defects4j-pitest/results");
    int threads = 1;
    int bugThreads = 1;
    String timeoutFactor = "1.25";
    String pitestVersion = "1.22.1";
    boolean resume = true;
    boolean keepWorkdirs = false;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "--projects" -> projects = splitCsv(requireValue(args, ++i, arg));
        case "--bugs" -> bugs = parseBugIds(requireValue(args, ++i, arg));
        case "--work-dir" -> workDir = Paths.get(requireValue(args, ++i, arg));
        case "--out-dir" -> outDir = Paths.get(requireValue(args, ++i, arg));
        case "--threads" -> threads = Integer.parseInt(requireValue(args, ++i, arg));
        case "--bug-threads" -> bugThreads = Integer.parseInt(requireValue(args, ++i, arg));
        case "--timeout-factor" -> timeoutFactor = requireValue(args, ++i, arg);
        case "--pitest-version" -> pitestVersion = requireValue(args, ++i, arg);
        case "--resume" -> resume = true;
        case "--no-resume" -> resume = false;
        case "--keep-workdirs" -> keepWorkdirs = true;
        default -> throw new IllegalArgumentException("Unknown option: " + arg);
      }
    }
    if (threads < 1) {
      throw new IllegalArgumentException("--threads must be >= 1");
    }
    if (bugThreads < 1) {
      throw new IllegalArgumentException("--bug-threads must be >= 1");
    }
    return new PitestScoreOptions(
        projects,
        bugs,
        workDir,
        outDir,
        threads,
        bugThreads,
        timeoutFactor,
        pitestVersion,
        resume,
        keepWorkdirs);
  }

  private static String requireValue(String[] args, int index, String option) {
    if (index >= args.length) {
      throw new IllegalArgumentException("Missing value for " + option);
    }
    return args[index];
  }

  private static List<String> splitCsv(String value) {
    return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static List<Integer> parseBugIds(String value) {
    Set<Integer> ids = new LinkedHashSet<>();
    for (String token : splitCsv(value)) {
      if (token.contains("..")) {
        String[] bounds = token.split("\\.\\.", -1);
        if (bounds.length != 2) {
          throw new IllegalArgumentException("Invalid bug range: " + token);
        }
        int start = Integer.parseInt(bounds[0]);
        int end = Integer.parseInt(bounds[1]);
        if (start > end) {
          throw new IllegalArgumentException("Invalid descending bug range: " + token);
        }
        for (int id = start; id <= end; id++) {
          ids.add(id);
        }
      } else {
        ids.add(Integer.parseInt(token));
      }
    }
    return new ArrayList<>(ids);
  }
}
