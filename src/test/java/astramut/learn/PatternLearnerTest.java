package astramut.learn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static astramut.learn.MockData.leafBody;
import static astramut.learn.MockData.literalToZero;
import static astramut.learn.MockData.nullCheckFlip;
import static astramut.learn.MockData.richBody;
import static org.junit.jupiter.api.Assertions.*;

class PatternLearnerTest {

    @Test
    void learnsNullCheckFlipPatternFromMultipleExamples() {
        List<GumTreeDiff> diffs = List.of(
                nullCheckFlip("x", "==", "!=", leafBody("ReturnStatement", 50, 60)),
                nullCheckFlip("y", "==", "!=", leafBody("ThrowStatement", 50, 60)),
                nullCheckFlip("z", "==", "!=", leafBody("BreakStatement", 50, 60))
        );

        LearnedModel model = new PatternLearner().learn(diffs);
        printModel("null-check flip", model);

        assertFalse(model.patterns().isEmpty(), "no patterns learned");
        LearnedPattern top = model.patterns().get(0);
        assertEquals(3, top.support(), "all three diffs should fall into the top cluster");

        TreeNode beforeBin = locateInfix(top.pattern().before());
        TreeNode afterBin = locateInfix(top.pattern().after());
        assertNotNull(beforeBin, "InfixExpression missing on LHS");
        assertNotNull(afterBin, "InfixExpression missing on RHS");
        assertEquals("==", beforeBin.label(), "LHS operator");
        assertEquals("!=", afterBin.label(), "RHS operator");

        TreePattern beforeLeft = beforeBin.children().get(0);
        TreePattern afterLeft = afterBin.children().get(0);
        assertInstanceOf(Hole.class, beforeLeft);
        assertInstanceOf(Hole.class, afterLeft);
        assertEquals(((Hole) beforeLeft).id(), ((Hole) afterLeft).id(),
                "the variable should be the same hole on both sides");
    }

    @Test
    void mixedDatasetLearnsTwoSeparatePatternFamilies() {
        List<GumTreeDiff> diffs = List.of(
                nullCheckFlip("x", "==", "!=", leafBody("ReturnStatement", 50, 60)),
                nullCheckFlip("y", "==", "!=", leafBody("ThrowStatement", 50, 60)),
                literalToZero("a", "b", "+"),
                literalToZero("c", "d", "-")
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
    void stripUnmodCollapsesRichUnmodBodyIntoSingleHole() {
        // Same MethodCall shape across diffs with differing leaves — without
        // stripUnmod the leaves explode into holes past maxHoles=4 and the
        // IfStatement-level cluster never merges.
        List<GumTreeDiff> diffs = List.of(
                nullCheckFlip("x", "==", "!=", richBody("a", "b", 50, 150)),
                nullCheckFlip("y", "==", "!=", richBody("c", "d", 50, 150))
        );

        LearnedModel model = new PatternLearner().learn(diffs);
        printModel("rich-body null-check flip", model);

        LearnedPattern ifLevel = model.patterns().stream()
                .filter(p -> p.pattern().before() instanceof TreeNode n
                        && "IfStatement".equals(n.type()))
                .findFirst()
                .orElse(null);
        assertNotNull(ifLevel,
                "stripUnmod should let the IfStatement-level cluster fit under maxHoles");
        assertEquals(2, ifLevel.support());
        assertTrue(ifLevel.pattern().holeCount() <= 4,
                "stripUnmod should keep the IfStatement-level hole count ≤ 4, got "
                        + ifLevel.pattern().holeCount());
    }

    @Test
    void mutationUnsafePatternsAreDropped() {
        // literalToZero with mismatched operators (+ vs -) produces a
        // VariableDeclaration-level cluster with LHS-only and RHS-only holes
        // — mutation-broken in both directions, must be filtered.
        List<GumTreeDiff> diffs = List.of(
                literalToZero("a", "b", "+"),
                literalToZero("c", "d", "-")
        );
        LearnedModel model = new PatternLearner().learn(diffs);
        printModel("mutation-safety filter", model);

        for (LearnedPattern p : model.patterns()) {
            assertTrue(p.pattern().isMutationSafe(),
                    "every retained pattern must be mutation-safe, got: "
                            + PatternFormatter.format(p.pattern()));
        }
        boolean varDeclSurvived = model.patterns().stream().anyMatch(p ->
                p.pattern().before() instanceof TreeNode n
                        && "VariableDeclaration".equals(n.type()));
        assertFalse(varDeclSurvived,
                "VariableDeclaration-level pattern is mutation-broken and should be dropped");
    }

    @Test
    void singletonExamplesSurviveWithoutMinSupportFilter() {
        // §10: minSupport filter removed — singleton concrete edits now yield
        // a usable pattern as long as F2/F3 pass.
        List<GumTreeDiff> diffs = List.of(
                nullCheckFlip("x", "==", "!=", leafBody("ReturnStatement", 50, 60))
        );
        LearnedModel model = new PatternLearner().learn(diffs);
        assertFalse(model.patterns().isEmpty(),
                "singleton patterns should survive without minSupport filter");
        LearnedPattern p = model.patterns().get(0);
        assertEquals(1, p.support(), "support reflects the single source diff");
    }

    @Test
    void identifierOnlySwapOnJdtNodesAbsorbedByF2() {
        // §8: with JDT-style SimpleName under MethodInvocation, an identifier-only
        // swap (foo() → bar()) hole-ifies on both sides → before ≡ after → F2 drops.
        List<GumTreeDiff> diffs = List.of(
                methodNameSwap("foo", "bar"),
                methodNameSwap("baz", "qux"));
        LearnedModel model = new PatternLearner().learn(diffs);
        assertTrue(model.patterns().isEmpty(),
                "identifier-only swap should be absorbed by F2 after §8 hole-ification, got "
                        + model.patterns().size() + " patterns");
    }

    @Test
    void typeIdentifierIsPreservedAcrossLearning() {
        // §8: SimpleType label (Object, String, ...) must stay literal so type-change
        // mutations like StringBuffer → StringBuilder survive.
        List<GumTreeDiff> diffs = List.of(
                typeAndVarSwap("StringBuffer", "StringBuilder", "sb"),
                typeAndVarSwap("StringBuffer", "StringBuilder", "buf"));
        LearnedModel model = new PatternLearner().learn(diffs);
        assertFalse(model.patterns().isEmpty(),
                "type-change pattern should survive (SimpleType literal preservation)");
        LearnedPattern top = model.patterns().get(0);
        TreeNode beforeType = locateType(top.pattern().before(), "StringBuffer");
        TreeNode afterType = locateType(top.pattern().after(), "StringBuilder");
        assertNotNull(beforeType, "StringBuffer literal must be preserved on LHS");
        assertNotNull(afterType, "StringBuilder literal must be preserved on RHS");
    }

    /** Build a CHANGE_IDENTIFIER-shaped edit: MethodInvocation { SimpleName(srcName) } → { SimpleName(dstName) }. */
    private static GumTreeDiff methodNameSwap(String srcName, String dstName) {
        GumTreeNode src = GumTreeNode.leaf("SimpleName", srcName, 1, srcName.length());
        GumTreeNode srcMi = new GumTreeNode("MethodInvocation", "", 0, srcName.length() + 2, List.of(src));

        GumTreeNode dst = GumTreeNode.leaf("SimpleName", dstName, 21, dstName.length());
        GumTreeNode dstMi = new GumTreeNode("MethodInvocation", "", 20, dstName.length() + 2, List.of(dst));

        List<GumTreeMatch> matches = List.of(
                new GumTreeMatch(srcMi.identifier(), dstMi.identifier()),
                new GumTreeMatch(src.identifier(), dst.identifier()));
        List<GumTreeAction> actions = List.of(
                new GumTreeAction.UpdateNode(src.identifier(), dstName));
        return new GumTreeDiff(srcMi, dstMi, matches, actions);
    }

    /** Build: VariableDeclarationFragment(SimpleName(var), ClassInstanceCreation(SimpleType(srcType))) → ...(dstType). */
    private static GumTreeDiff typeAndVarSwap(String srcType, String dstType, String varName) {
        GumTreeNode srcVar = GumTreeNode.leaf("SimpleName", varName, 1, varName.length());
        GumTreeNode srcTypeName = GumTreeNode.leaf("SimpleName", srcType, 2, srcType.length());
        GumTreeNode srcSimpleType = new GumTreeNode("SimpleType", "", 2, srcType.length(), List.of(srcTypeName));
        GumTreeNode srcCic = new GumTreeNode("ClassInstanceCreation", "", 3, srcType.length() + 3, List.of(srcSimpleType));
        GumTreeNode srcFrag = new GumTreeNode("VariableDeclarationFragment", "", 0, 20, List.of(srcVar, srcCic));

        GumTreeNode dstVar = GumTreeNode.leaf("SimpleName", varName, 41, varName.length());
        GumTreeNode dstTypeName = GumTreeNode.leaf("SimpleName", dstType, 42, dstType.length());
        GumTreeNode dstSimpleType = new GumTreeNode("SimpleType", "", 42, dstType.length(), List.of(dstTypeName));
        GumTreeNode dstCic = new GumTreeNode("ClassInstanceCreation", "", 43, dstType.length() + 3, List.of(dstSimpleType));
        GumTreeNode dstFrag = new GumTreeNode("VariableDeclarationFragment", "", 40, 20, List.of(dstVar, dstCic));

        List<GumTreeMatch> matches = List.of(
                new GumTreeMatch(srcFrag.identifier(), dstFrag.identifier()),
                new GumTreeMatch(srcVar.identifier(), dstVar.identifier()),
                new GumTreeMatch(srcCic.identifier(), dstCic.identifier()),
                new GumTreeMatch(srcSimpleType.identifier(), dstSimpleType.identifier()),
                new GumTreeMatch(srcTypeName.identifier(), dstTypeName.identifier()));
        List<GumTreeAction> actions = List.of(
                new GumTreeAction.UpdateNode(srcTypeName.identifier(), dstType));
        return new GumTreeDiff(srcFrag, dstFrag, matches, actions);
    }

    private static TreeNode locateType(TreePattern p, String label) {
        if (p instanceof TreeNode n) {
            if ("SimpleName".equals(n.type()) && label.equals(n.label())) return n;
            for (TreePattern c : n.children()) {
                TreeNode found = locateType(c, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasOperatorChange(LearnedPattern p, String beforeOp, String afterOp) {
        TreeNode b = locateInfix(p.pattern().before());
        TreeNode a = locateInfix(p.pattern().after());
        return b != null && a != null && beforeOp.equals(b.label()) && afterOp.equals(a.label());
    }

    private static boolean hasLiteralChange(LearnedPattern p, String beforeVal, String afterVal) {
        TreeNode b = locateLiteral(p.pattern().before(), beforeVal);
        TreeNode a = locateLiteral(p.pattern().after(), afterVal);
        return b != null && a != null;
    }

    private static TreeNode locateInfix(TreePattern p) {
        if (p instanceof TreeNode n) {
            if ("InfixExpression".equals(n.type())) return n;
            for (TreePattern c : n.children()) {
                TreeNode found = locateInfix(c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TreeNode locateLiteral(TreePattern p, String value) {
        if (p instanceof TreeNode n) {
            if ("Literal".equals(n.type()) && value.equals(n.label())) return n;
            for (TreePattern c : n.children()) {
                TreeNode found = locateLiteral(c, value);
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
