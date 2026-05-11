package astramut.diff;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GumTreeDiffEngineTest {

    private final GumTreeDiffEngine engine = new GumTreeDiffEngine();

    @Test
    void identical_code_produces_no_actions() throws IOException {
        String code = "y = a / x;";
        GumTreeDiff diff = engine.diff(code, code);
        assertFalse(diff.hasChanges(), "Identical snippets should yield no actions");
    }

    @Test
    void numeric_literal_change_detected() throws IOException {
        GumTreeDiff diff = engine.diff("return 0;", "return 1;");
        assertTrue(diff.hasChanges(), "Numeric literal change should produce at least one action");
    }

    @Test
    void if_guard_removal_detected() throws IOException {
        GumTreeDiff diff = engine.diff(
                "if (x != 0) { y = a / x; }",
                "y = a / x;"
        );
        assertTrue(diff.hasChanges(), "Removing if-guard should produce edit actions");
    }

    @Test
    void wrong_method_name_detected() throws IOException {
        GumTreeDiff diff = engine.diff("foo.equals(bar);", "foo.equalsIgnoreCase(bar);");
        assertTrue(diff.hasChanges(), "Method name change should produce edit actions");
    }

    @Test
    void diff_result_preserves_original_code() throws IOException {
        String before = "return x + 1;";
        String after  = "return x - 1;";
        GumTreeDiff diff = engine.diff(before, after);
        assertEquals(before, diff.beforeCode());
        assertEquals(after,  diff.afterCode());
    }
}
