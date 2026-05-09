package astramut.learn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AntiUnifierTest {

    @Test
    void identicalLeavesProduceNoHole() {
        TreePattern a = TreeNode.leaf("Id", "x");
        TreePattern b = TreeNode.leaf("Id", "x");
        TreePattern result = AntiUnifier.au(a, b, new AntiUnifier.HoleAllocator());
        assertEquals(0, result.holeCount());
        assertEquals(a, result);
    }

    @Test
    void differingLeavesProduceHole() {
        TreePattern a = TreeNode.leaf("Id", "x");
        TreePattern b = TreeNode.leaf("Id", "y");
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

        TreePattern beforeLeft = ((TreeNode) au.before()).children().get(0).child();
        TreePattern afterLeft = ((TreeNode) au.after()).children().get(0).child();
        assertInstanceOf(Hole.class, beforeLeft);
        assertInstanceOf(Hole.class, afterLeft);
        assertEquals(((Hole) beforeLeft).id(), ((Hole) afterLeft).id(),
                "the variable should resolve to the same hole on both sides");
    }

    private static EditPattern flipNullCheck(String var) {
        TreePattern before = new TreeNode("BinaryExpr", "==", List.of(
                new TreeNode.ChildSlot("left", TreeNode.leaf("Id", var)),
                new TreeNode.ChildSlot("right", TreeNode.leaf("Literal", "null"))));
        TreePattern after = new TreeNode("BinaryExpr", "!=", List.of(
                new TreeNode.ChildSlot("left", TreeNode.leaf("Id", var)),
                new TreeNode.ChildSlot("right", TreeNode.leaf("Literal", "null"))));
        return new EditPattern(before, after);
    }
}
