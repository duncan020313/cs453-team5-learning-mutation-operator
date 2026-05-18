package astramut.diff;

import astramut.learn.GumTreeDiff;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GumTreeDiffEngineTest {

    private final GumTreeDiffEngine engine = new GumTreeDiffEngine();

    @Test
    void identical_code_produces_no_actions() throws IOException {
        String code = "y = a / x;";
        GumTreeDiff diff = engine.diff(code, code);
        assertTrue(diff.actions().isEmpty(), "Identical snippets should yield no actions");
    }

    @Test
    void numeric_literal_change_detected() throws IOException {
        GumTreeDiff diff = engine.diff("return 0;", "return 1;");
        assertFalse(diff.actions().isEmpty(), "Numeric literal change should produce at least one action");
    }

    @Test
    void if_guard_removal_detected() throws IOException {
        GumTreeDiff diff = engine.diff(
                "if (x != 0) { y = a / x; }",
                "y = a / x;"
        );
        assertFalse(diff.actions().isEmpty(), "Removing if-guard should produce edit actions");
    }

    @Test
    void wrong_method_name_detected() throws IOException {
        GumTreeDiff diff = engine.diff("foo.equals(bar);", "foo.equalsIgnoreCase(bar);");
        assertFalse(diff.actions().isEmpty(), "Method name change should produce edit actions");
    }

    @Test
    void diff_returns_non_null_trees() throws IOException {
        GumTreeDiff diff = engine.diff("return 0;", "return 1;");
        assertNotNull(diff.srcTree());
        assertNotNull(diff.dstTree());
    }

    @Test
    void matches_are_non_empty() throws IOException {
        GumTreeDiff diff = engine.diff("foo.equals(bar);", "foo.equalsIgnoreCase(bar);");
        assertFalse(diff.matches().isEmpty(), "Method name change should have node matches");
    }
}