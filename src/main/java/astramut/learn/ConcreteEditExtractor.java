package astramut.learn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConcreteEditExtractor {

    public List<EditPattern> extract(AstDiff diff) {
        Map<String, AstNode> afterIndex = new HashMap<>();
        indexById(diff.after(), afterIndex);
        List<EditPattern> out = new ArrayList<>();
        walk(diff.before(), diff.beforeToAfter(), afterIndex, out);
        return out;
    }

    private static void indexById(AstNode node, Map<String, AstNode> index) {
        index.put(node.id(), node);
        for (AstNode.ChildSlot s : node.children()) indexById(s.child(), index);
    }

    private boolean walk(AstNode bNode, Map<String, String> mapping,
                         Map<String, AstNode> afterIndex, List<EditPattern> out) {
        String aId = mapping.get(bNode.id());
        AstNode aNode = aId == null ? null : afterIndex.get(aId);

        boolean selfChanged = aNode == null
                || !bNode.label().equals(aNode.label())
                || !bNode.value().equals(aNode.value())
                || bNode.children().size() != aNode.children().size();

        boolean descendantChanged = false;
        for (AstNode.ChildSlot bs : bNode.children()) {
            if (walk(bs.child(), mapping, afterIndex, out)) descendantChanged = true;
        }

        boolean modified = selfChanged || descendantChanged;
        // Emit at every mapped ancestor whose subtree differs — Getafix §3.3
        // ("extract too many rather than too few"). The clustering step decides
        // which granularity is meaningful.
        if (modified && aNode != null && !structurallyEqual(bNode, aNode)) {
            out.add(new EditPattern(toPattern(bNode), toPattern(aNode)));
        }
        return modified;
    }

    private static boolean structurallyEqual(AstNode a, AstNode b) {
        if (!a.label().equals(b.label())) return false;
        if (!a.value().equals(b.value())) return false;
        if (a.children().size() != b.children().size()) return false;
        for (int i = 0; i < a.children().size(); i++) {
            AstNode.ChildSlot sa = a.children().get(i);
            AstNode.ChildSlot sb = b.children().get(i);
            if (!sa.location().equals(sb.location())) return false;
            if (!structurallyEqual(sa.child(), sb.child())) return false;
        }
        return true;
    }

    public static TreePattern toPattern(AstNode node) {
        List<TreeNode.ChildSlot> kids = new ArrayList<>(node.children().size());
        for (AstNode.ChildSlot s : node.children()) {
            kids.add(new TreeNode.ChildSlot(s.location(), toPattern(s.child())));
        }
        return new TreeNode(node.label(), node.value(), kids);
    }
}
