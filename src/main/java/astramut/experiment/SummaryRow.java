package astramut.experiment;

import astramut.experiment.ExperimentTypes.MutatorVariant;
import astramut.experiment.ExperimentTypes.SummaryFormatException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

record SummaryRow(
    String project,
    int bugId,
    String version,
    MutatorVariant mutatorVariant,
    String status,
    int targetClassCount,
    int targetMethodCount,
    int targetTestCount,
    int generated,
    int killed,
    int survived,
    int noCoverage,
    String message,
    long durationSeconds) {
  static String header() {
    return "project,bugId,version,mutatorVariant,status,targetClasses,targetMethods,targetTests,generated,killed,survived,"
        + "noCoverage,mutationScore,durationSeconds,message";
  }

  static SummaryRow success(
      String project,
      int bugId,
      String version,
      MutatorVariant mutatorVariant,
      Map<String, Set<String>> targetMethods,
      List<String> targetTests,
      MutationTotals totals,
      long seconds) {
    int methodCount = targetMethods.values().stream().mapToInt(Set::size).sum();
    return new SummaryRow(
        project,
        bugId,
        version,
        mutatorVariant,
        "SUCCESS",
        targetMethods.size(),
        methodCount,
        targetTests.size(),
        totals.generated(),
        totals.killed(),
        totals.survived(),
        totals.noCoverage(),
        "methodFilterPrecision=NAME_ONLY",
        seconds);
  }

  static SummaryRow skipped(
      String project,
      int bugId,
      String version,
      MutatorVariant mutatorVariant,
      String status,
      long seconds) {
    return new SummaryRow(
        project, bugId, version, mutatorVariant, status, 0, 0, 0, 0, 0, 0, 0, "", seconds);
  }

  static SummaryRow failed(
      String project,
      int bugId,
      String version,
      MutatorVariant mutatorVariant,
      String message,
      long seconds) {
    return new SummaryRow(
        project,
        bugId,
        version,
        mutatorVariant,
        "FAILED",
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        Objects.toString(message, ""),
        seconds);
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
        escape(mutatorVariant.name()),
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
        escape(message));
  }

  static Optional<SummaryRow> parse(String line) throws SummaryFormatException {
    if (line.startsWith("project,") || line.isBlank()) {
      return Optional.empty();
    }

    List<String> fields = parseCsvLine(line);
    try {
      if (fields.size() >= 15) {
        return Optional.of(
            new SummaryRow(
                fields.get(0),
                Integer.parseInt(fields.get(1)),
                fields.get(2),
                MutatorVariant.valueOf(fields.get(3)),
                fields.get(4),
                Integer.parseInt(fields.get(5)),
                Integer.parseInt(fields.get(6)),
                Integer.parseInt(fields.get(7)),
                Integer.parseInt(fields.get(8)),
                Integer.parseInt(fields.get(9)),
                Integer.parseInt(fields.get(10)),
                Integer.parseInt(fields.get(11)),
                fields.get(14),
                Long.parseLong(fields.get(13))));
      }
      if (fields.size() < 14) {
        return Optional.empty();
      }
      return Optional.of(
          new SummaryRow(
              fields.get(0),
              Integer.parseInt(fields.get(1)),
              fields.get(2),
              MutatorVariant.DEFAULTS,
              fields.get(3),
              Integer.parseInt(fields.get(4)),
              Integer.parseInt(fields.get(5)),
              Integer.parseInt(fields.get(6)),
              Integer.parseInt(fields.get(7)),
              Integer.parseInt(fields.get(8)),
              Integer.parseInt(fields.get(9)),
              Integer.parseInt(fields.get(10)),
              fields.get(13),
              Long.parseLong(fields.get(12))));
    } catch (IllegalArgumentException e) {
      throw new SummaryFormatException("Invalid summary row: " + line, e);
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
