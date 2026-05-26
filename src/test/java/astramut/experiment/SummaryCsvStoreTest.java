package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import astramut.experiment.ExperimentTypes.MutatorVariant;
import astramut.experiment.ExperimentTypes.SummaryFormatException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SummaryCsvStoreTest {
  @TempDir Path tempDir;

  @Test
  void summaryRowRoundTripsCsvEscaping() throws SummaryFormatException {
    SummaryRow row =
        SummaryRow.failed("Lang", 1, "1f", MutatorVariant.ALL, "bad, \"quoted\"\nmessage", 7);

    SummaryRow parsed = SummaryRow.parse(row.toCsv()).orElseThrow();

    assertEquals(row.project(), parsed.project());
    assertEquals(row.bugId(), parsed.bugId());
    assertEquals(row.mutatorVariant(), parsed.mutatorVariant());
    assertEquals(row.message().replace("\n", " "), parsed.message());
    assertEquals(row.durationSeconds(), parsed.durationSeconds());
  }

  @Test
  void parsesLegacyRowsAsDefaultsVariant() throws SummaryFormatException {
    SummaryRow parsed =
        SummaryRow.parse("Lang,1,1f,SUCCESS,1,1,1,2,1,1,0,0.5000,3,legacy").orElseThrow();

    assertEquals(MutatorVariant.DEFAULTS, parsed.mutatorVariant());
    assertEquals("SUCCESS", parsed.status());
    assertEquals("legacy", parsed.message());
    assertEquals(3, parsed.durationSeconds());
  }

  @Test
  void writesAverageByMutatorVariant() throws IOException, SummaryFormatException {
    SummaryCsvStore store = new SummaryCsvStore(tempDir);
    MutationTotals totals = new MutationTotals();
    totals.incrementGenerated();
    totals.incrementGenerated();
    totals.incrementKilled();
    totals.incrementSurvived();
    SummaryRow row =
        SummaryRow.success(
            "Lang",
            1,
            "1f",
            MutatorVariant.DEFAULTS,
            Map.of("org.example.C", Set.of("m")),
            List.of("org.example.CTest"),
            totals,
            4);
    SummaryRow zeroGenerated =
        SummaryRow.success(
            "Lang",
            2,
            "2f",
            MutatorVariant.DEFAULTS,
            Map.of("org.example.C", Set.of("m")),
            List.of("org.example.CTest"),
            new MutationTotals(),
            5);

    try (BufferedWriter writer = store.openWriter(false)) {
      store.ensureHeader(writer, false);
      store.writeRows(writer, List.of(row, zeroGenerated));
    }

    store.writeAverage();

    assertTrue(zeroGenerated.toCsv().contains(",1.0000,"));
    String average = Files.readString(tempDir.resolve("average.txt"), StandardCharsets.UTF_8);
    assertTrue(average.contains("DEFAULTS.successfulBugs=2"));
    assertTrue(average.contains("DEFAULTS.averageMutationScore=0.7500"));
    assertTrue(average.contains("ALL.successfulBugs=0"));
  }

  @Test
  void readsCompletedSuccessAndSkippedRows() throws IOException, SummaryFormatException {
    SummaryCsvStore store = new SummaryCsvStore(tempDir);
    SummaryRow success =
        SummaryRow.success(
            "Lang",
            1,
            "1f",
            MutatorVariant.DEFAULTS,
            Map.of("C", Set.of("m")),
            List.of("T"),
            new MutationTotals(),
            1);
    SummaryRow skipped =
        SummaryRow.skipped("Lang", 2, "2f", MutatorVariant.ALL, "SKIPPED_NO_TARGET_METHOD", 1);

    try (BufferedWriter writer = store.openWriter(false)) {
      store.ensureHeader(writer, false);
      store.writeRows(writer, List.of(success, skipped));
    }

    Set<String> completed = store.readCompleted(true);

    assertTrue(
        completed.contains(SummaryCsvStore.completedKey("Lang", 1, MutatorVariant.DEFAULTS)));
    assertTrue(completed.contains(SummaryCsvStore.completedKey("Lang", 2, MutatorVariant.ALL)));
  }
}
