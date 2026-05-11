package astramut.diff;

import astramut.learn.AstNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GumTreeAstDiffAdapterTest {

    private final GumTreeDiffEngine engine  = new GumTreeDiffEngine();
    private final GumTreeAstDiffAdapter adapter = new GumTreeAstDiffAdapter();

    @Test
    void convert_produces_non_null_result() throws IOException {
        GumTreeDiff gumTreeDiff = engine.diff("return 0;", "return 1;");
        astramut.learn.AstDiff learnDiff = adapter.convert(gumTreeDiff);
        assertNotNull(learnDiff);
        assertNotNull(learnDiff.before());
        assertNotNull(learnDiff.after());
        assertNotNull(learnDiff.beforeToAfter());
    }

    @Test
    void before_and_after_trees_are_non_empty() throws IOException {
        GumTreeDiff gumTreeDiff = engine.diff("foo.equals(bar);", "foo.equalsIgnoreCase(bar);");
        astramut.learn.AstDiff learnDiff = adapter.convert(gumTreeDiff);

        assertFalse(learnDiff.before().label().isBlank(), "before root label must not be blank");
        assertFalse(learnDiff.after().label().isBlank(),  "after root label must not be blank");
    }

    @Test
    void mapping_is_non_empty_when_trees_share_structure() throws IOException {
        GumTreeDiff gumTreeDiff = engine.diff("foo.equals(bar);", "foo.equalsIgnoreCase(bar);");
        astramut.learn.AstDiff learnDiff = adapter.convert(gumTreeDiff);

        assertFalse(learnDiff.beforeToAfter().isEmpty(), "mapping must not be empty");
    }

    @Test
    void identical_code_has_full_mapping_no_changes() throws IOException {
        String code = "return x + 1;";
        GumTreeDiff gumTreeDiff = engine.diff(code, code);
        astramut.learn.AstDiff learnDiff = adapter.convert(gumTreeDiff);

        assertEquals(
                countNodes(learnDiff.before()),
                countNodes(learnDiff.after()),
                "before and after node counts must be equal for identical code"
        );
    }

    @Test
    void node_ids_use_prefix_b_and_a() throws IOException {
        GumTreeDiff gumTreeDiff = engine.diff("return 0;", "return 1;");
        astramut.learn.AstDiff learnDiff = adapter.convert(gumTreeDiff);

        assertTrue(learnDiff.before().id().startsWith("b:"),
                "before node id must start with 'b:', acutal: " + learnDiff.before().id());

        assertTrue(learnDiff.after().id().startsWith("a:"),
                "after node id must start with 'a:', acutal: " + learnDiff.after().id());
    }

    @Test
    void mapping_keys_start_with_b_values_start_with_a() throws IOException {
        GumTreeDiff gumTreeDiff = engine.diff("return 0;", "return 1;");
        astramut.learn.AstDiff learnDiff = adapter.convert(gumTreeDiff);

        learnDiff.beforeToAfter().forEach((k, v) -> {
            assertTrue(k.startsWith("b:"), "mapping key must start with 'b:' : " + k);
            assertTrue(v.startsWith("a:"), "mapping value must start with 'a:' : " + v);
        });
    }

    private int countNodes(AstNode node) {
        int count = 1;
        for (AstNode.ChildSlot slot : node.children()) {
            count += countNodes(slot.child());
        }
        return count;
    }
}
