package astramut.learn;

/**
 * One entry of GumTree's {@code actions} edit script. Mirrors the JSON
 * shape:
 *
 * <pre>{ "action": "update-node", "tree": "...", "label": "..." }
 * { "action": "insert-node", "tree": "...", "parent": "...", "at": 0 }
 * { "action": "delete-node", "tree": "..." }
 * { "action": "move-tree",   "tree": "...", "parent": "...", "at": 1 }
 * { "action": "insert-tree", "tree": "...", "parent": "...", "at": 0 }
 * { "action": "delete-tree", "tree": "..." }</pre>
 *
 * The {@code tree} field references a node identifier — for update / delete /
 * move it lives on the src side, for insert it lives on the dst side.
 */
public sealed interface GumTreeAction {

    String tree();

    record UpdateNode(String tree, String label) implements GumTreeAction {}

    record InsertNode(String tree, String parent, int at) implements GumTreeAction {}

    record DeleteNode(String tree) implements GumTreeAction {}

    record InsertTree(String tree, String parent, int at) implements GumTreeAction {}

    record DeleteTree(String tree) implements GumTreeAction {}

    record MoveTree(String tree, String parent, int at) implements GumTreeAction {}
}
