package astramut.learn;

import org.junit.jupiter.api.Test;

import java.util.List;

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
