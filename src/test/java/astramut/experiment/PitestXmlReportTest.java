package astramut.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

class PitestXmlReportTest {
  @TempDir Path tempDir;

  @Test
  void parsesTotalsAndWritesFilteredCombinedXml()
      throws IOException, ParserConfigurationException, SAXException, TransformerException {
    Path classReport = tempDir.resolve("org.example.C");
    Files.createDirectories(classReport);
    Files.writeString(
        classReport.resolve("mutations.xml"),
        """
        <mutations>
          <mutation detected="true" status="KILLED">
            <mutatedClass>org.example.C</mutatedClass>
            <mutatedMethod>target</mutatedMethod>
          </mutation>
          <mutation detected="false" status="NO_COVERAGE">
            <mutatedClass>org.example.C</mutatedClass>
            <mutatedMethod>target</mutatedMethod>
          </mutation>
          <mutation detected="false" status="SURVIVED">
            <mutatedClass>org.example.C</mutatedClass>
            <mutatedMethod>other</mutatedMethod>
          </mutation>
        </mutations>
        """,
        StandardCharsets.UTF_8);
    PitestXmlReport report = new PitestXmlReport();

    MutationTotals totals = report.parseMutationTotals(classReport, Set.of("target"));
    report.copyCombinedMutationXml(tempDir, Map.of("org.example.C", Set.of("target")));

    assertEquals(2, totals.generated());
    assertEquals(1, totals.killed());
    assertEquals(0, totals.survived());
    assertEquals(1, totals.noCoverage());
    String combined = Files.readString(tempDir.resolve("mutations.xml"), StandardCharsets.UTF_8);
    assertTrue(combined.contains("<mutatedMethod>target</mutatedMethod>"));
    assertFalse(combined.contains("<mutatedMethod>other</mutatedMethod>"));
  }
}
