package astramut.learn;

import java.util.List;

/**
 * Concrete (non-hole) tree pattern node. Mirrors a {@link GumTreeNode}'s
 * (type, label, children) — positions are dropped because patterns are
 * abstract templates with no source location. Children are an ordered list,
 * matching GumTree's child ordering (no slot/location string).
 */
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
