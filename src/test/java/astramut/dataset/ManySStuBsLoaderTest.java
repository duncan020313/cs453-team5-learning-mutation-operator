package astramut.dataset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManySStuBsLoaderTest {

    private final ManySStuBsLoader loader = new ManySStuBsLoader();

    private static final String FIXTURE_JSON = """
            [
              {
                "bugType": "CHANGE_NUMERIC_LITERAL",
                "sourceBeforeFix": "return 0;",
                "sourceAfterFix":  "return 1;"
              },
              {
                "bugType": "MISSING_THROWS_EXCEPTION",
                "sourceBeforeFix": null,
                "sourceAfterFix":  null
              }
            ]
            """;

    @Test
    void load_returns_all_entries(@TempDir Path tmp) throws IOException {
        Path json = writeFixture(tmp, FIXTURE_JSON);
        List<BugFix> all = loader.load(json);
        assertEquals(2, all.size());
    }

    @Test
    void loadWithSourceCode_filters_missing_snippets(@TempDir Path tmp) throws IOException {
        Path json = writeFixture(tmp, FIXTURE_JSON);
        List<BugFix> withCode = loader.loadWithSourceCode(json);
        assertEquals(1, withCode.size());
        assertEquals("CHANGE_NUMERIC_LITERAL", withCode.get(0).bugType());
    }

    @Test
    void loadByBugType_returns_matching_entries(@TempDir Path tmp) throws IOException {
        Path json = writeFixture(tmp, FIXTURE_JSON);
        List<BugFix> typed = loader.loadByBugType(json, "CHANGE_NUMERIC_LITERAL");
        assertEquals(1, typed.size());
        assertEquals("return 0;", typed.get(0).sourceBeforeFix());
        assertEquals("return 1;", typed.get(0).sourceAfterFix());
    }

    @Test
    void loadByBugType_empty_when_no_match(@TempDir Path tmp) throws IOException {
        Path json = writeFixture(tmp, FIXTURE_JSON);
        List<BugFix> typed = loader.loadByBugType(json, "NONEXISTENT_TYPE");
        assertTrue(typed.isEmpty());
    }

    @Test
    void hasSourceCode_is_false_when_fields_null(@TempDir Path tmp) throws IOException {
        Path json = writeFixture(tmp, FIXTURE_JSON);
        List<BugFix> all = loader.load(json);
        assertFalse(all.get(1).hasSourceCode());
    }

    private static Path writeFixture(Path dir, String content) throws IOException {
        Path file = dir.resolve("sstubs.json");
        Files.writeString(file, content);
        return file;
    }
}
