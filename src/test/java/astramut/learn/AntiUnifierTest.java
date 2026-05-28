package astramut.learn;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AntiUnifierTest {

    @Test
    void identicalLeavesProduceNoHole() {
        TreePattern a = TreeNode.leaf("Identifier", "x");
        TreePattern b = TreeNode.leaf("Identifier", "x");
        TreePattern result = AntiUnifier.au(a, b, new AntiUnifier.HoleAllocator());
        assertEquals(0, result.holeCount());
        assertEquals(a, result);
    }

    @Test
    void differingLeavesProduceHole() {
        TreePattern a = TreeNode.leaf("Identifier", "x");
        TreePattern b = TreeNode.leaf("Identifier", "y");
        TreePattern result = AntiUnifier.au(a, b, new AntiUnifier.HoleAllocator());
        assertInstanceOf(Hole.class, result);
    }

    @Test
    void sameStructureWithDifferingLeafProducesPatternWithOneHole() {
        EditPattern e1 = flipNullCheck("x");
        EditPattern e2 = flipNullCheck("y");
        EditPattern au = AntiUnifier.antiUnify(e1, e2);
        assertEquals(2, au.holeCount(), "one logical hole, counted on both sides");
    }

    @Test
    void sharedSubtreeShareHoleAcrossBeforeAndAfter() {
        EditPattern e1 = flipNullCheck("x");
        EditPattern e2 = flipNullCheck("y");
        EditPattern au = AntiUnifier.antiUnify(e1, e2);

        TreePattern beforeLeft = ((TreeNode) au.before()).children().get(0);
        TreePattern afterLeft = ((TreeNode) au.after()).children().get(0);
        assertInstanceOf(Hole.class, beforeLeft);
        assertInstanceOf(Hole.class, afterLeft);
        assertEquals(((Hole) beforeLeft).id(), ((Hole) afterLeft).id(),
                "the variable should resolve to the same hole on both sides");
    }

    @Test
    void holeIdsRemainDisjointAcrossNestedAntiUnification() {
        // §1-§7 regression: when AU consumes patterns that already contain holes
        // (i.e., later passes over cluster representatives), pre-existing ?h0 and
        // newly-allocated ?h0 must not collide. The receiver hole and method-name
        // hole live in different roles → distinct ids required.
        EditPattern a = methodCallWithHole("foo");
        EditPattern b = methodCallWithHole("baz");
        EditPattern au = AntiUnifier.antiUnify(a, b);

        Set<String> ids = new HashSet<>();
        collectHoleIds(au.before(), ids);
        assertEquals(2, ids.size(),
                "receiver / method-name slots must use different hole ids; got " + ids);
    }

    /** Build MethodInvocation(?h0, method) — receiver is already a hole (simulating a previous AU pass). */
    private static EditPattern methodCallWithHole(String methodName) {
        TreePattern before = new TreeNode("MethodInvocation", "", List.of(
                new Hole("?h0"),
                TreeNode.leaf("Identifier", methodName)));
        TreePattern after = new TreeNode("MethodInvocation", "", List.of(
                new Hole("?h0"),
                TreeNode.leaf("Identifier", methodName + "X")));
        return new EditPattern(before, after);
    }

    private static void collectHoleIds(TreePattern p, Set<String> ids) {
        if (p instanceof Hole h) { ids.add(h.id()); return; }
        TreeNode n = (TreeNode) p;
        for (TreePattern c : n.children()) collectHoleIds(c, ids);
    }

    private static EditPattern flipNullCheck(String var) {
        TreePattern before = new TreeNode("InfixExpression", "==", List.of(
                TreeNode.leaf("Identifier", var),
                TreeNode.leaf("Literal", "null")));
        TreePattern after = new TreeNode("InfixExpression", "!=", List.of(
                TreeNode.leaf("Identifier", var),
                TreeNode.leaf("Literal", "null")));
        return new EditPattern(before, after);
    }
}
