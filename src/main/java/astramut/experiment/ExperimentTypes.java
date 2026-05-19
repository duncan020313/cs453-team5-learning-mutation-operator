package astramut.experiment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

final class ExperimentTypes {
  private ExperimentTypes() {}

  record CommandResult(int exitCode, String output) {}

  record ExportedMetadata(
      String srcClassesDir,
      String binClassesDir,
      String binTestsDir,
      String cpTest,
      List<String> modifiedClasses,
      List<String> relevantTests) {}

  record BugTarget(String project, int bugId, List<MutatorVariant> variants) {}

  record BugResult(BugTarget target, List<SummaryRow> rows) {}

  enum MutatorVariant {
    DEFAULTS("defaults", Optional.empty()),
    ALL("all", Optional.of("ALL"));

    static final List<MutatorVariant> ALL_VARIANTS = List.of(DEFAULTS, ALL);

    private final String directoryName;
    private final Optional<String> mutatorsArgument;

    MutatorVariant(String directoryName, Optional<String> mutatorsArgument) {
      this.directoryName = directoryName;
      this.mutatorsArgument = mutatorsArgument;
    }

    String directoryName() {
      return directoryName;
    }

    Optional<String> mutatorsArgument() {
      return mutatorsArgument;
    }
  }

  record MethodRange(String name, int startLine, int endLine) {
    boolean intersects(Collection<Integer> lines) {
      return lines.stream().anyMatch(line -> line >= startLine && line <= endLine);
    }

    MethodRange withEnd(int endLine) {
      return new MethodRange(name, startLine, endLine);
    }
  }

  static final class CommandFailedException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String action;
    private final CommandResult result;

    CommandFailedException(String action, CommandResult result) {
      super(action + " failed:\n" + result.output());
      this.action = action;
      this.result = result;
    }

    String action() {
      return action;
    }

    CommandResult result() {
      return result;
    }

    static void throwIfFailed(String action, CommandResult result) throws CommandFailedException {
      if (result.exitCode() != 0) {
        throw new CommandFailedException(action, result);
      }
    }
  }

  static final class ExperimentSetupException extends Exception {
    private static final long serialVersionUID = 1L;

    ExperimentSetupException(String message) {
      super(message);
    }
  }

  static final class SummaryFormatException extends Exception {
    private static final long serialVersionUID = 1L;

    SummaryFormatException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
