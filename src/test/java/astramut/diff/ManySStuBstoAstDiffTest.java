package astramut.diff;

import astramut.dataset.BugFix;
import astramut.dataset.ManySStuBsLoader;
import astramut.learn.AstDiff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManySStuBsToAstDiffIntegrationTest {

    private final ManySStuBsLoader loader = new ManySStuBsLoader();
    private final GumTreeDiffEngine engine = new GumTreeDiffEngine();
    private final GumTreeAstDiffAdapter adapter = new GumTreeAstDiffAdapter();

    private static final String FIXTURE_JSON = """
            [
              {
                "bugType": "CHANGE_NUMERIC_LITERAL",
                "sourceBeforeFix": "return 0;",
                "sourceAfterFix":  "return 1;"
              },
              {
                "bugType": "WRONG_FUNCTION_NAME",
                "sourceBeforeFix": "foo.equals(bar);",
                "sourceAfterFix":  "foo.equalsIgnoreCase(bar);"
              }
            ]
            """;

    @Test
    void dataset_entry_converts_to_astDiff_end_to_end(@TempDir Path tmp) throws IOException {
        Path json = writeFixture(tmp, FIXTURE_JSON);
        List<BugFix> examples = loader.loadWithSourceCode(json);
        assertEquals(2, examples.size());

        BugFix example = examples.get(0);
        GumTreeDiff gumTreeDiff = engine.diff(
                example.sourceBeforeFix(),
                example.sourceAfterFix()
        );

        assertTrue(gumTreeDiff.hasChanges(),
                "before/after source code should produce GumTree edit actions");

        AstDiff astDiff = adapter.convert(gumTreeDiff);
        assertNotNull(astDiff);
        assertNotNull(astDiff.before());
        assertNotNull(astDiff.after());
        assertNotNull(astDiff.beforeToAfter());
        assertTrue(astDiff.before().id().startsWith("b:"));
        assertTrue(astDiff.after().id().startsWith("a:"));
        assertFalse(astDiff.beforeToAfter().isEmpty(),
                "converted AstDiff should preserve GumTree mappings");
    }

    @Test
    void all_entries_in_dataset_can_be_converted(@TempDir Path tmp) throws IOException {
        Path json = writeFixture(tmp, FIXTURE_JSON);
        List<BugFix> examples = loader.loadWithSourceCode(json);

        for (BugFix fix : examples) {
            GumTreeDiff gumTreeDiff = engine.diff(fix.sourceBeforeFix(), fix.sourceAfterFix());
            AstDiff astDiff = adapter.convert(gumTreeDiff);

            assertNotNull(astDiff.before(), "before must not be null for bugType: " + fix.bugType());
            assertNotNull(astDiff.after(), "after must not be null for bugType: " + fix.bugType());
        }
    }

    @Test
    void wrong_function_name_produces_non_empty_mapping(@TempDir Path tmp) throws IOException {
        Path json = writeFixture(tmp, FIXTURE_JSON);
        List<BugFix> examples = loader.loadByBugType(json, "WRONG_FUNCTION_NAME");
        assertEquals(1, examples.size());

        BugFix fix = examples.get(0);
        GumTreeDiff gumTreeDiff = engine.diff(fix.sourceBeforeFix(), fix.sourceAfterFix());
        AstDiff astDiff = adapter.convert(gumTreeDiff);

        assertFalse(astDiff.beforeToAfter().isEmpty(),
                "WRONG_FUNCTION_NAME diff should have non-empty mappings");
    }

    private static Path writeFixture(Path dir, String content) throws IOException {
        Path file = dir.resolve("sstubs.json");
        Files.writeString(file, content);
        return file;
    }
}
