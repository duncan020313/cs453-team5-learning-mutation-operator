package astramut.learn;

import java.util.List;

/** Mirrors gumtree's tree JSON node. Identifier string format must match {@code Tree.toString()}. */
public record GumTreeNode(String type, String label, int pos, int length, List<GumTreeNode> children) {

    public GumTreeNode {
        children = List.copyOf(children == null ? List.of() : children);
        label = label == null ? "" : label;
    }

    public static GumTreeNode leaf(String type, String label, int pos, int length) {
        return new GumTreeNode(type, label, pos, length, List.of());
    }

    public String identifier() {
        int endPos = pos + length;
        if (label.isEmpty()) return type + " [" + pos + "," + endPos + "]";
        return type + ": " + label + " [" + pos + "," + endPos + "]";
    }
}
