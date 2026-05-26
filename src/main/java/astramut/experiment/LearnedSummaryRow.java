package astramut.experiment;

import astramut.experiment.ExperimentTypes.SummaryFormatException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

record LearnedSummaryRow(
    String project,
    int bugId,
    String version,
    LearnedOperatorSet operatorSet,
    String status,
    int targetClassCount,
    int targetMethodCount,
    int targetTestCount,
    int generated,
    int killed,
    int survived,
    int noCoverage,
    String message,
    long durationSeconds,
    int sourceMutants,
    int duplicateMutants,
    int compileFailed,
    int testFailed,
    int timeoutKilled,
    int selectedOperators,
    String modelArchive) {
  static String header() {
    return "project,bugId,version,mutatorVariant,status,targetClasses,targetMethods,targetTests,generated,killed,survived,"
        + "noCoverage,mutationScore,durationSeconds,message,sourceMutants,duplicateMutants,compileFailed,testFailed,"
        + "timeoutKilled,selectedOperators,modelArchive";
  }

  static LearnedSummaryRow success(
      String project,
      int bugId,
      String version,
      LearnedOperatorSet operatorSet,
      int targetClassCount,
      int targetMethodCount,
      int targetTestCount,
      LearnedMutationTotals totals,
      int selectedOperators,
      long seconds,
      String modelArchive) {
    return new LearnedSummaryRow(
        project,
        bugId,
        version,
        operatorSet,
        "SUCCESS",
        targetClassCount,
        targetMethodCount,
        targetTestCount,
        totals.generated(),
        totals.killed(),
        totals.survived(),
        totals.noCoverage(),
        "",
        seconds,
        totals.sourceMutants(),
        totals.duplicateMutants(),
        totals.compileFailed(),
        totals.testFailed(),
        totals.timeoutKilled(),
        selectedOperators,
        modelArchive);
  }

  static LearnedSummaryRow skipped(
      String project,
      int bugId,
      String version,
      LearnedOperatorSet operatorSet,
      String status,
      long seconds,
      int selectedOperators,
      String modelArchive) {
    return new LearnedSummaryRow(
        project,
        bugId,
        version,
        operatorSet,
        status,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        "",
        seconds,
        0,
        0,
        0,
        0,
        0,
        selectedOperators,
        modelArchive);
  }

  static LearnedSummaryRow failed(
      String project,
      int bugId,
      String version,
      LearnedOperatorSet operatorSet,
      String message,
      long seconds,
      String modelArchive) {
    return failed(project, bugId, version, operatorSet, message, seconds, 0, modelArchive);
  }

  static LearnedSummaryRow failed(
      String project,
      int bugId,
      String version,
      LearnedOperatorSet operatorSet,
      String message,
      long seconds,
      int selectedOperators,
      String modelArchive) {
    return new LearnedSummaryRow(
        project,
        bugId,
        version,
        operatorSet,
        "FAILED",
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        Objects.toString(message, ""),
        seconds,
        0,
        0,
        0,
        0,
        0,
        selectedOperators,
        modelArchive);
  }

  double mutationScore() {
    return generated == 0 ? 1.0 : killed / (double) generated;
  }

  String toCsv() {
    return String.join(
        ",",
        escape(project),
        Integer.toString(bugId),
        escape(version),
        escape(operatorSet.name()),
        escape(status),
        Integer.toString(targetClassCount),
        Integer.toString(targetMethodCount),
        Integer.toString(targetTestCount),
        Integer.toString(generated),
        Integer.toString(killed),
        Integer.toString(survived),
        Integer.toString(noCoverage),
        String.format(Locale.ROOT, "%.4f", mutationScore()),
        Long.toString(durationSeconds),
        escape(message),
        Integer.toString(sourceMutants),
        Integer.toString(duplicateMutants),
        Integer.toString(compileFailed),
        Integer.toString(testFailed),
        Integer.toString(timeoutKilled),
        Integer.toString(selectedOperators),
        escape(modelArchive));
  }

  static Optional<LearnedSummaryRow> parse(String line) throws SummaryFormatException {
    if (line.startsWith("project,") || line.isBlank()) {
      return Optional.empty();
    }

    List<String> fields = parseCsvLine(line);
    if (fields.size() < 22) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new LearnedSummaryRow(
              fields.get(0),
              Integer.parseInt(fields.get(1)),
              fields.get(2),
              LearnedOperatorSet.valueOf(fields.get(3)),
              fields.get(4),
              Integer.parseInt(fields.get(5)),
              Integer.parseInt(fields.get(6)),
              Integer.parseInt(fields.get(7)),
              Integer.parseInt(fields.get(8)),
              Integer.parseInt(fields.get(9)),
              Integer.parseInt(fields.get(10)),
              Integer.parseInt(fields.get(11)),
              fields.get(14),
              Long.parseLong(fields.get(13)),
              Integer.parseInt(fields.get(15)),
              Integer.parseInt(fields.get(16)),
              Integer.parseInt(fields.get(17)),
              Integer.parseInt(fields.get(18)),
              Integer.parseInt(fields.get(19)),
              Integer.parseInt(fields.get(20)),
              fields.get(21)));
    } catch (IllegalArgumentException e) {
      throw new SummaryFormatException("Invalid learned summary row: " + line, e);
    }
  }

  private static String escape(String value) {
    String sanitized = value.replace("\r", " ").replace("\n", " ");
    if (sanitized.contains(",") || sanitized.contains("\"")) {
      return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }
    return sanitized;
  }

  private static List<String> parseCsvLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          current.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        fields.add(current.toString());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString());
    return fields;
  }
}
