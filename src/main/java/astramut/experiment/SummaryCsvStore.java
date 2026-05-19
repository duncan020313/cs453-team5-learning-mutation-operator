package astramut.experiment;

import astramut.experiment.ExperimentTypes.MutatorVariant;
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

final class SummaryCsvStore {
  private final Path outDir;
  private final Path summary;

  SummaryCsvStore(Path outDir) {
    this.outDir = outDir;
    this.summary = outDir.resolve("summary.csv");
  }

  Path summaryPath() {
    return summary;
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
      writer.write(SummaryRow.header());
      writer.newLine();
    }
  }

  void writeRows(BufferedWriter writer, List<SummaryRow> rows) throws IOException {
    for (SummaryRow row : rows) {
      writer.write(row.toCsv());
      writer.newLine();
    }
    writer.flush();
  }

  void migrateSummary() throws IOException, SummaryFormatException {
    if (!Files.exists(summary)) {
      return;
    }
    List<String> lines = Files.readAllLines(summary, StandardCharsets.UTF_8);
    if (!lines.isEmpty() && SummaryRow.header().equals(lines.get(0))) {
      return;
    }

    List<SummaryRow> rows = new ArrayList<>();
    for (String line : lines) {
      SummaryRow.parse(line).ifPresent(rows::add);
    }
    try (BufferedWriter writer =
        Files.newBufferedWriter(
            summary,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
      writer.write(SummaryRow.header());
      writer.newLine();
      for (SummaryRow row : rows) {
        writer.write(row.toCsv());
        writer.newLine();
      }
    }
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
      SummaryRow.parse(line)
          .filter(row -> "SUCCESS".equals(row.status()) || row.status().startsWith("SKIPPED_"))
          .ifPresent(
              row -> completed.add(completedKey(row.project(), row.bugId(), row.mutatorVariant())));
    }
    return completed;
  }

  void writeAverage() throws IOException, SummaryFormatException {
    List<SummaryRow> rows = new ArrayList<>();
    for (String line : Files.readAllLines(summary, StandardCharsets.UTF_8)) {
      if (line.startsWith("project,") || line.isBlank()) {
        continue;
      }
      SummaryRow.parse(line).ifPresent(rows::add);
    }

    List<SummaryRow> successes =
        rows.stream().filter(row -> "SUCCESS".equals(row.status()) && row.generated() > 0).toList();
    Map<MutatorVariant, List<SummaryRow>> byVariant =
        successes.stream()
            .collect(
                Collectors.groupingBy(
                    SummaryRow::mutatorVariant, LinkedHashMap::new, Collectors.toList()));

    StringBuilder content = new StringBuilder();
    for (MutatorVariant variant : MutatorVariant.ALL_VARIANTS) {
      List<SummaryRow> variantRows = byVariant.getOrDefault(variant, List.of());
      double average =
          variantRows.stream().mapToDouble(SummaryRow::mutationScore).average().orElse(0.0);
      content
          .append(variant.name())
          .append(".successfulBugs=")
          .append(variantRows.size())
          .append(System.lineSeparator())
          .append(variant.name())
          .append(".averageMutationScore=")
          .append(String.format(Locale.ROOT, "%.4f", average))
          .append(System.lineSeparator());
    }
    Files.writeString(outDir.resolve("average.txt"), content.toString(), StandardCharsets.UTF_8);
  }

  static String completedKey(String project, int bugId, MutatorVariant variant) {
    return project + "-" + bugId + "-" + variant.name();
  }
}
