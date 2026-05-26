package astramut.experiment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

record LearnedScoreOptions(
    List<String> projects,
    List<Integer> bugs,
    Path workDir,
    Path outDir,
    int bugThreads,
    boolean resume,
    boolean keepWorkdirs,
    Path modelArchive,
    String modelEntry,
    List<LearnedOperatorSet> operatorSets,
    Optional<String> bugType,
    int minSupport,
    double minSpecificity,
    double minCohortRatio,
    Duration mutantTimeout) {
  LearnedScoreOptions {
    projects = List.copyOf(projects);
    bugs = List.copyOf(bugs);
    operatorSets = List.copyOf(operatorSets);
  }

  static LearnedScoreOptions parse(String[] args) {
    List<String> projects = List.of();
    List<Integer> bugs = List.of();
    Path workDir = Paths.get("data/defects4j-learned/work");
    Path outDir = Paths.get("data/defects4j-learned/results");
    int bugThreads = 1;
    boolean resume = true;
    boolean keepWorkdirs = false;
    Path modelArchive = Paths.get("learned_260520.tar.gz");
    String modelEntry = "learned/patterns-full.json";
    List<LearnedOperatorSet> operatorSets = LearnedOperatorSet.DEFAULT_SETS;
    Optional<String> bugType = Optional.empty();
    int minSupport = 2;
    double minSpecificity = 0.0;
    double minCohortRatio = 0.0;
    Duration mutantTimeout = Duration.ofSeconds(300);

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "--projects" -> projects = splitCsv(requireValue(args, ++i, arg));
        case "--bugs" -> bugs = parseBugIds(requireValue(args, ++i, arg));
        case "--work-dir" -> workDir = Paths.get(requireValue(args, ++i, arg));
        case "--out-dir" -> outDir = Paths.get(requireValue(args, ++i, arg));
        case "--bug-threads" -> bugThreads = Integer.parseInt(requireValue(args, ++i, arg));
        case "--resume" -> resume = true;
        case "--no-resume" -> resume = false;
        case "--keep-workdirs" -> keepWorkdirs = true;
        case "--model-archive" -> modelArchive = Paths.get(requireValue(args, ++i, arg));
        case "--model-entry" -> modelEntry = requireValue(args, ++i, arg);
        case "--preset" -> operatorSets = parseOperatorSets(requireValue(args, ++i, arg));
        case "--bug-type" -> bugType = Optional.of(requireValue(args, ++i, arg));
        case "--min-support" -> minSupport = Integer.parseInt(requireValue(args, ++i, arg));
        case "--min-specificity" ->
            minSpecificity = Double.parseDouble(requireValue(args, ++i, arg));
        case "--min-cohort-ratio" ->
            minCohortRatio = Double.parseDouble(requireValue(args, ++i, arg));
        case "--mutant-timeout-seconds" ->
            mutantTimeout = Duration.ofSeconds(Long.parseLong(requireValue(args, ++i, arg)));
        default -> throw new IllegalArgumentException("Unknown option: " + arg);
      }
    }

    if (bugThreads < 1) {
      throw new IllegalArgumentException("--bug-threads must be >= 1");
    }
    if (operatorSets.isEmpty()) {
      throw new IllegalArgumentException("--preset must select at least one preset");
    }
    if (minSupport < 0) {
      throw new IllegalArgumentException("--min-support must be >= 0");
    }
    if (minSpecificity < 0.0) {
      throw new IllegalArgumentException("--min-specificity must be >= 0.0");
    }
    if (minCohortRatio < 0.0) {
      throw new IllegalArgumentException("--min-cohort-ratio must be >= 0.0");
    }
    if (mutantTimeout.isZero() || mutantTimeout.isNegative()) {
      throw new IllegalArgumentException("--mutant-timeout-seconds must be >= 1");
    }

    return new LearnedScoreOptions(
        projects,
        bugs,
        workDir,
        outDir,
        bugThreads,
        resume,
        keepWorkdirs,
        modelArchive,
        modelEntry,
        operatorSets,
        bugType,
        minSupport,
        minSpecificity,
        minCohortRatio,
        mutantTimeout);
  }

  private static void requirePositive(String option, int value) {
    if (value < 1) {
      throw new IllegalArgumentException(option + " must be >= 1");
    }
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

  private static List<LearnedOperatorSet> parseOperatorSets(String value) {
    Set<LearnedOperatorSet> sets = new LinkedHashSet<>();
    for (String token : splitCsv(value)) {
      sets.add(LearnedOperatorSet.parse(token));
    }
    return new ArrayList<>(sets);
  }
}
