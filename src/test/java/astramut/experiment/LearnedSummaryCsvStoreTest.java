package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import astramut.experiment.ExperimentTypes.SummaryFormatException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearnedSummaryCsvStoreTest {
  @TempDir Path tempDir;

  @Test
  void rowRoundTripsCsvEscapingAndExtraColumns() throws SummaryFormatException {
    LearnedSummaryRow row =
        LearnedSummaryRow.failed(
            "Lang",
            1,
            "1f",
            LearnedOperatorSet.LEARNED_TOP_100,
            "bad, \"quoted\"\nmessage",
            7,
            "learned_260520.tar.gz");

    LearnedSummaryRow parsed = LearnedSummaryRow.parse(row.toCsv()).orElseThrow();

    assertEquals(row.project(), parsed.project());
    assertEquals(row.bugId(), parsed.bugId());
    assertEquals(row.operatorSet(), parsed.operatorSet());
    assertEquals(row.message().replace("\n", " "), parsed.message());
    assertEquals(row.modelArchive(), parsed.modelArchive());
  }

  @Test
  void readsCompletedSuccessAndSkippedRows() throws IOException, SummaryFormatException {
    LearnedSummaryCsvStore store = new LearnedSummaryCsvStore(tempDir);
    LearnedSummaryRow success =
        LearnedSummaryRow.success(
            "Lang",
            1,
            "1f",
            LearnedOperatorSet.LEARNED_TOP_100,
            1,
            1,
            1,
            new LearnedMutationTotals(2, 1, 1, 0, 2, 0, 0, 1, 0),
            100,
            4,
            "archive.tar.gz");
    LearnedSummaryRow skipped =
        LearnedSummaryRow.skipped(
            "Lang",
            2,
            "2f",
            LearnedOperatorSet.LEARNED_TOP_1000,
            "SKIPPED_NO_TARGET_METHOD",
            1,
            1000,
            "archive.tar.gz");

    try (BufferedWriter writer = store.openWriter(false)) {
      store.ensureHeader(writer, false);
      store.writeRows(writer, List.of(success, skipped));
    }

    assertTrue(
        store
            .readCompleted(true)
            .contains(
                LearnedSummaryCsvStore.completedKey(
                    "Lang", 1, LearnedOperatorSet.LEARNED_TOP_100)));
    assertTrue(
        store
            .readCompleted(true)
            .contains(
                LearnedSummaryCsvStore.completedKey(
                    "Lang", 2, LearnedOperatorSet.LEARNED_TOP_1000)));
  }

  @Test
  void writesAverageByOperatorSet() throws IOException, SummaryFormatException {
    LearnedSummaryCsvStore store = new LearnedSummaryCsvStore(tempDir);
    LearnedSummaryRow row =
        LearnedSummaryRow.success(
            "Lang",
            1,
            "1f",
            LearnedOperatorSet.LEARNED_TOP_100,
            1,
            1,
            1,
            new LearnedMutationTotals(2, 1, 1, 0, 2, 0, 0, 1, 0),
            100,
            4,
            "archive.tar.gz");
    LearnedSummaryRow zeroGenerated =
        LearnedSummaryRow.success(
            "Lang",
            2,
            "2f",
            LearnedOperatorSet.LEARNED_TOP_100,
            1,
            1,
            1,
            new LearnedMutationTotals(0, 0, 0, 0, 0, 0, 0, 0, 0),
            100,
            5,
            "archive.tar.gz");

    try (BufferedWriter writer = store.openWriter(false)) {
      store.ensureHeader(writer, false);
      store.writeRows(writer, List.of(row, zeroGenerated));
    }

    store.writeAverage();

    assertTrue(zeroGenerated.toCsv().contains(",1.0000,"));
    String average = Files.readString(tempDir.resolve("average.txt"), StandardCharsets.UTF_8);
    assertTrue(average.contains("LEARNED_TOP_100.successfulBugs=2"));
    assertTrue(average.contains("LEARNED_TOP_100.averageMutationScore=0.7500"));
    assertTrue(average.contains("LEARNED_TOP_1000.successfulBugs=0"));
  }
}
