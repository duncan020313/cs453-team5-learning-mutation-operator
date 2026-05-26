package astramut.experiment;

import astramut.experiment.ExperimentTypes.SummaryFormatException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class LearnedSummaryCsvStore {
  private final Path outDir;
  private final Path summary;

  LearnedSummaryCsvStore(Path outDir) {
    this.outDir = outDir;
    this.summary = outDir.resolve("summary.csv");
  }

  boolean shouldAppend(boolean resume) {
    return Files.exists(summary) && resume;
  }

  BufferedWriter openWriter(boolean append) throws IOException {
    if (append) {
      return Files.newBufferedWriter(
          summary, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    return Files.newBufferedWriter(
        summary,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  void ensureHeader(BufferedWriter writer, boolean append) throws IOException {
    if (!append || Files.size(summary) == 0) {
      writer.write(LearnedSummaryRow.header());
      writer.newLine();
    }
  }

  void writeRows(BufferedWriter writer, List<LearnedSummaryRow> rows) throws IOException {
    for (LearnedSummaryRow row : rows) {
      writer.write(row.toCsv());
      writer.newLine();
    }
    writer.flush();
  }

  Set<String> readCompleted(boolean resume) throws IOException, SummaryFormatException {
    if (!resume || !Files.exists(summary)) {
      return Collections.emptySet();
    }

    Set<String> completed = new HashSet<>();
    for (String line : Files.readAllLines(summary, StandardCharsets.UTF_8)) {
      if (line.startsWith("project,") || line.isBlank()) {
        continue;
      }
      LearnedSummaryRow.parse(line)
          .filter(row -> "SUCCESS".equals(row.status()) || row.status().startsWith("SKIPPED_"))
          .ifPresent(
              row -> completed.add(completedKey(row.project(), row.bugId(), row.operatorSet())));
    }
    return completed;
  }

  void writeAverage() throws IOException, SummaryFormatException {
    List<LearnedSummaryRow> rows = new ArrayList<>();
    if (!Files.exists(summary)) {
      return;
    }
    for (String line : Files.readAllLines(summary, StandardCharsets.UTF_8)) {
      if (line.startsWith("project,") || line.isBlank()) {
        continue;
      }
      LearnedSummaryRow.parse(line).ifPresent(rows::add);
    }

    List<LearnedSummaryRow> successes =
        rows.stream().filter(row -> "SUCCESS".equals(row.status())).toList();
    Map<LearnedOperatorSet, List<LearnedSummaryRow>> bySet =
        successes.stream()
            .collect(
                Collectors.groupingBy(
                    LearnedSummaryRow::operatorSet, LinkedHashMap::new, Collectors.toList()));

    StringBuilder content = new StringBuilder();
    for (LearnedOperatorSet set : LearnedOperatorSet.values()) {
      List<LearnedSummaryRow> setRows = bySet.getOrDefault(set, List.of());
      double average =
          setRows.stream().mapToDouble(LearnedSummaryRow::mutationScore).average().orElse(0.0);
      content
          .append(set.name())
          .append(".successfulBugs=")
          .append(setRows.size())
          .append(System.lineSeparator())
          .append(set.name())
          .append(".averageMutationScore=")
          .append(String.format(Locale.ROOT, "%.4f", average))
          .append(System.lineSeparator());
    }
    Files.writeString(outDir.resolve("average.txt"), content.toString(), StandardCharsets.UTF_8);
  }

  static String completedKey(String project, int bugId, LearnedOperatorSet set) {
    return project + "-" + bugId + "-" + set.name();
  }
}
