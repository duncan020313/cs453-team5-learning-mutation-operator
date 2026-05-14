package astramut.learn;

import java.util.List;

/**
 * Node from a GumTree-produced parse tree, mirrored 1:1 from the tree
 * JSON emitted by {@code gumtree parse} / {@code TreeIoUtils.toJson}:
 *
 * <pre>{ "type": "...", "label": "...", "pos": "10", "length": "8",
 *   "children": [ ... ] }</pre>
 *
 * GumTree identifies a node uniquely by {@code "Type: label [pos,endPos]"}
 * (or {@code "Type [pos,endPos]"} when there is no label) — see
 * {@link #identifier()}. The matches and edit script reference nodes by
 * exactly this string, so it must round-trip stably.
 */
public record GumTreeNode(String type, String label, int pos, int length, List<GumTreeNode> children) {

    public GumTreeNode {
        children = List.copyOf(children == null ? List.of() : children);
        label = label == null ? "" : label;
    }

    public static GumTreeNode leaf(String type, String label, int pos, int length) {
        return new GumTreeNode(type, label, pos, length, List.of());
    }

    /**
     * Re-creates GumTree's {@code Tree.toString()} format used as the node
     * identifier in the matches and edit-script JSON.
     */
    public String identifier() {
        int endPos = pos + length;
        if (label.isEmpty()) {
            return type + " [" + pos + "," + endPos + "]";
        }
        return type + ": " + label + " [" + pos + "," + endPos + "]";
    }
}
