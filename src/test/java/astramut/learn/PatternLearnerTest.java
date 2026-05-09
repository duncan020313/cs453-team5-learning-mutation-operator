package astramut.learn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static astramut.learn.MockData.leaf;
import static astramut.learn.MockData.literalToZero;
import static astramut.learn.MockData.nullCheckFlip;
import static org.junit.jupiter.api.Assertions.*;

class PatternLearnerTest {

    @Test
    void learnsNullCheckFlipPatternFromMultipleExamples() {
        List<AstDiff> diffs = List.of(
                nullCheckFlip("d1", "x", "==", "!=", leaf("d1:return", "ReturnStmt", "")),
                nullCheckFlip("d2", "y", "==", "!=", leaf("d2:throw", "ThrowStmt", "")),
                nullCheckFlip("d3", "z", "==", "!=", leaf("d3:break", "BreakStmt", ""))
        );

        LearnedModel model = new PatternLearner().learn(diffs);
        printModel("null-check flip", model);

        assertFalse(model.patterns().isEmpty(), "no patterns learned");
        LearnedPattern top = model.patterns().get(0);
        assertEquals(3, top.support(), "all three diffs should fall into the top cluster");

        TreeNode beforeBin = locateBinaryExpr(top.pattern().before());
        TreeNode afterBin = locateBinaryExpr(top.pattern().after());
        assertNotNull(beforeBin, "BinaryExpr missing on LHS");
        assertNotNull(afterBin, "BinaryExpr missing on RHS");
        assertEquals("==", beforeBin.value(), "LHS operator");
        assertEquals("!=", afterBin.value(), "RHS operator");

        TreePattern beforeLeft = beforeBin.children().get(0).child();
        TreePattern afterLeft = afterBin.children().get(0).child();
        assertInstanceOf(Hole.class, beforeLeft);
        assertInstanceOf(Hole.class, afterLeft);
        assertEquals(((Hole) beforeLeft).id(), ((Hole) afterLeft).id(),
                "the variable should be the same hole on both sides");
    }

    @Test
    void mixedDatasetLearnsTwoSeparatePatternFamilies() {
        List<AstDiff> diffs = List.of(
                nullCheckFlip("d1", "x", "==", "!=", leaf("d1:return", "ReturnStmt", "")),
                nullCheckFlip("d2", "y", "==", "!=", leaf("d2:throw", "ThrowStmt", "")),
                literalToZero("d3", "a", "b", "+"),
                literalToZero("d4", "c", "d", "-")
        );

        LearnedModel model = new PatternLearner().learn(diffs);
        printModel("mixed dataset", model);

        long flipFamily = model.patterns().stream()
                .filter(p -> hasOperatorChange(p, "==", "!="))
                .count();
        long literalFamily = model.patterns().stream()
                .filter(p -> hasLiteralChange(p, "1", "0"))
                .count();
        assertTrue(flipFamily > 0, "should learn the null-check flip family");
        assertTrue(literalFamily > 0, "should learn the literal-to-zero family");
    }

    @Test
    void singletonExamplesAreFilteredOutByMinSupport() {
        List<AstDiff> diffs = List.of(
                nullCheckFlip("only", "x", "==", "!=", leaf("only:return", "ReturnStmt", ""))
        );
        LearnedModel model = new PatternLearner().learn(diffs);
        assertTrue(model.patterns().isEmpty(),
                "minSupport=2 should drop a single-instance pattern");
    }

    private static boolean hasOperatorChange(LearnedPattern p, String beforeOp, String afterOp) {
        TreeNode b = locateBinaryExpr(p.pattern().before());
        TreeNode a = locateBinaryExpr(p.pattern().after());
        return b != null && a != null && beforeOp.equals(b.value()) && afterOp.equals(a.value());
    }

    private static boolean hasLiteralChange(LearnedPattern p, String beforeVal, String afterVal) {
        TreeNode b = locateLiteral(p.pattern().before(), beforeVal);
        TreeNode a = locateLiteral(p.pattern().after(), afterVal);
        return b != null && a != null;
    }

    private static TreeNode locateBinaryExpr(TreePattern p) {
        if (p instanceof TreeNode n) {
            if ("BinaryExpr".equals(n.label())) return n;
            for (TreeNode.ChildSlot s : n.children()) {
                TreeNode found = locateBinaryExpr(s.child());
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TreeNode locateLiteral(TreePattern p, String value) {
        if (p instanceof TreeNode n) {
            if ("Literal".equals(n.label()) && value.equals(n.value())) return n;
            for (TreeNode.ChildSlot s : n.children()) {
                TreeNode found = locateLiteral(s.child(), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void printModel(String label, LearnedModel model) {
        System.out.println("─── " + label + " ───");
        for (int i = 0; i < model.patterns().size(); i++) {
            LearnedPattern p = model.patterns().get(i);
            System.out.printf("[%d] support=%d  spec=%.3f  score=%.3f%n  %s%n",
                    i, p.support(), p.specificity(), p.score(),
                    PatternFormatter.format(p.pattern()));
        }
        System.out.println();
    }
}
