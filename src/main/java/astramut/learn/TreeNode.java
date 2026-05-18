package astramut.learn;

import java.util.List;

public record TreeNode(String type, String label, List<TreePattern> children) implements TreePattern {

    public TreeNode {
        children = List.copyOf(children == null ? List.of() : children);
        label = label == null ? "" : label;
    }

    public static TreeNode leaf(String type, String label) {
        return new TreeNode(type, label, List.of());
    }

    @Override
    public int holeCount() {
        int total = 0;
        for (TreePattern c : children) total += c.holeCount();
        return total;
    }

    @Override
    public int nodeCount() {
        int total = 1;
        for (TreePattern c : children) total += c.nodeCount();
        return total;
    }
}
