package astramut.learn;

/** Mirrors gumtree's edit-script entries. {@code tree} is a src id for update/delete/move and a dst id for insert. */
public sealed interface GumTreeAction {

    String tree();

    record UpdateNode(String tree, String label) implements GumTreeAction {}

    record InsertNode(String tree, String parent, int at) implements GumTreeAction {}

    record DeleteNode(String tree) implements GumTreeAction {}

    record InsertTree(String tree, String parent, int at) implements GumTreeAction {}

    record DeleteTree(String tree) implements GumTreeAction {}

    record MoveTree(String tree, String parent, int at) implements GumTreeAction {}
}
