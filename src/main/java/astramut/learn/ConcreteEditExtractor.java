package astramut.learn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts (b_subtree, a_subtree) pairs from a {@link GumTreeDiff} at every
 * granularity at which the change is visible — the multi-granularity
 * "extract too many rather than too few" step from Getafix §3.3.
 *
 * <p>Driven by the GumTree edit script: each action marks one or two nodes
 * as <em>directly touched</em>, the touched flag is propagated up the src
 * tree, and a concrete edit is emitted at every mapped src ancestor whose
 * subtree differs from its dst counterpart. The clustering step downstream
 * decides which granularity carries a generalizable pattern.
 */
public final class ConcreteEditExtractor {

    public List<EditPattern> extract(GumTreeDiff diff) {
        Map<String, GumTreeNode> srcIndex = new HashMap<>();
        Map<String, GumTreeNode> dstIndex = new HashMap<>();
        Map<String, GumTreeNode> srcParent = new HashMap<>();
        indexTree(diff.srcTree(), null, srcIndex, srcParent);
        indexTree(diff.dstTree(), null, dstIndex, new HashMap<>());

        Map<String, String> srcToDst = new HashMap<>();
        Map<String, String> dstToSrc = new HashMap<>();
        for (GumTreeMatch m : diff.matches()) {
            srcToDst.put(m.src(), m.dest());
            dstToSrc.put(m.dest(), m.src());
        }

        Set<String> touched = new HashSet<>();
        for (GumTreeAction a : diff.actions()) {
            markTouched(a, touched, srcIndex, srcParent, dstToSrc);
        }

        List<EditPattern> out = new ArrayList<>();
        walk(diff.srcTree(), touched, srcToDst, dstIndex, out);
        return out;
    }

    private static void indexTree(GumTreeNode node, GumTreeNode parent,
                                  Map<String, GumTreeNode> index,
                                  Map<String, GumTreeNode> parents) {
        index.put(node.identifier(), node);
        if (parent != null) parents.put(node.identifier(), parent);
        for (GumTreeNode c : node.children()) indexTree(c, node, index, parents);
    }

    private static void markTouched(GumTreeAction a, Set<String> touched,
                                    Map<String, GumTreeNode> srcIndex,
                                    Map<String, GumTreeNode> srcParent,
                                    Map<String, String> dstToSrc) {
        if (a instanceof GumTreeAction.UpdateNode u) {
            markSrc(u.tree(), touched, srcIndex);
        } else if (a instanceof GumTreeAction.DeleteNode d) {
            markSrc(d.tree(), touched, srcIndex);
            markSrcParent(d.tree(), touched, srcParent);
        } else if (a instanceof GumTreeAction.DeleteTree d) {
            markSrc(d.tree(), touched, srcIndex);
            markSrcParent(d.tree(), touched, srcParent);
        } else if (a instanceof GumTreeAction.MoveTree m) {
            markSrc(m.tree(), touched, srcIndex);
            markSrcParent(m.tree(), touched, srcParent);
            markDstAsSrc(m.parent(), touched, dstToSrc);
        } else if (a instanceof GumTreeAction.InsertNode i) {
            markDstAsSrc(i.parent(), touched, dstToSrc);
        } else if (a instanceof GumTreeAction.InsertTree i) {
            markDstAsSrc(i.parent(), touched, dstToSrc);
        }
    }

    private static void markSrc(String id, Set<String> touched,
                                Map<String, GumTreeNode> srcIndex) {
        if (srcIndex.containsKey(id)) touched.add(id);
    }

    private static void markSrcParent(String childId, Set<String> touched,
                                      Map<String, GumTreeNode> srcParent) {
        GumTreeNode p = srcParent.get(childId);
        if (p != null) touched.add(p.identifier());
    }

    private static void markDstAsSrc(String dstId, Set<String> touched,
                                     Map<String, String> dstToSrc) {
        String srcId = dstToSrc.get(dstId);
        if (srcId != null) touched.add(srcId);
    }

    private static boolean walk(GumTreeNode src, Set<String> touched,
                                Map<String, String> srcToDst,
                                Map<String, GumTreeNode> dstIndex,
                                List<EditPattern> out) {
        boolean modified = touched.contains(src.identifier());
        for (GumTreeNode child : src.children()) {
            if (walk(child, touched, srcToDst, dstIndex, out)) modified = true;
        }
        if (!modified) return false;
        String dstId = srcToDst.get(src.identifier());
        if (dstId == null) return true;
        GumTreeNode dst = dstIndex.get(dstId);
        if (dst == null || structurallyEqual(src, dst)) return true;
        out.add(new EditPattern(toPattern(src), toPattern(dst)));
        return true;
    }

    private static boolean structurallyEqual(GumTreeNode a, GumTreeNode b) {
        if (!a.type().equals(b.type())) return false;
        if (!a.label().equals(b.label())) return false;
        if (a.children().size() != b.children().size()) return false;
        for (int i = 0; i < a.children().size(); i++) {
            if (!structurallyEqual(a.children().get(i), b.children().get(i))) return false;
        }
        return true;
    }

    public static TreePattern toPattern(GumTreeNode node) {
        List<TreePattern> kids = new ArrayList<>(node.children().size());
        for (GumTreeNode c : node.children()) kids.add(toPattern(c));
        return new TreeNode(node.type(), node.label(), kids);
    }
}
