package astramut.learn;

import java.util.List;

public record AstNode(String id, String label, String value, List<ChildSlot> children) {
    public AstNode {
        children = List.copyOf(children == null ? List.of() : children);
        value = value == null ? "" : value;
    }

    public static AstNode leaf(String id, String label, String value) {
        return new AstNode(id, label, value, List.of());
    }

    public record ChildSlot(String location, AstNode child) {}
}
