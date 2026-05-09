package astramut.learn;

import java.util.List;

public record TreeNode(String label, String value, List<ChildSlot> children) implements TreePattern {
    public TreeNode {
        children = List.copyOf(children == null ? List.of() : children);
        value = value == null ? "" : value;
    }

    public static TreeNode leaf(String label, String value) {
        return new TreeNode(label, value, List.of());
    }

    @Override
    public int holeCount() {
        int total = 0;
        for (ChildSlot s : children) total += s.child().holeCount();
        return total;
    }

    @Override
    public int nodeCount() {
        int total = 1;
        for (ChildSlot s : children) total += s.child().nodeCount();
        return total;
    }

    public record ChildSlot(String location, TreePattern child) {}
}
