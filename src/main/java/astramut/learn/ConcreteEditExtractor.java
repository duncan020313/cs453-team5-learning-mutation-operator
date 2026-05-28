package astramut.learn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Action-driven multi-granularity edit extraction — Getafix §3.3 "extract too many rather than too few". */
public final class ConcreteEditExtractor {

    /** Corpus-derived common type names kept literal in type contexts; rare ones hole-ify.
     *  Configured by {@link #setCommonTypeNames}; defaults to empty (= all types preserved). */
    private static volatile Set<String> COMMON_TYPE_NAMES = Set.of();

    /** Configure corpus-common type names before training. Call once from LearnCommand. */
    public static void setCommonTypeNames(Set<String> names) {
        COMMON_TYPE_NAMES = names == null ? Set.of() : Set.copyOf(names);
    }

    /** Treat all type-context identifiers as literal (used by tests and pre-Option-C paths). */
    public static void preserveAllTypeNames() {
        COMMON_TYPE_NAMES = Set.of();
    }

    /** Boundary numeric labels kept literal; other NumberLiteral labels collapse to MAGIC_NUMBER_TOKEN. */
    private static final Set<String> CANONICAL_NUMERIC_LABELS = Set.of(
            "0", "1", "-1",
            "0.0", "1.0", "-1.0",
            "0f", "1f", "-1f", "0F", "1F", "-1F",
            "0l", "1l", "-1l", "0L", "1L", "-1L",
            "0d", "1d", "-1d", "0D", "1D", "-1D");
    private static final String MAGIC_NUMBER_TOKEN = "__MAGIC__";

    /** Identifier parent contexts whose value carries mutation semantics (types, annotation names, imports) → literal.
     * Every other SimpleName/QualifiedName parent (variable, method, declaration, label) defaults to hole. */
    private static final Set<String> LITERAL_PARENT_TYPES = Set.of(
            "SimpleType", "QualifiedType", "NameQualifiedType", "ParameterizedType",
            "MarkerAnnotation", "NormalAnnotation", "SingleMemberAnnotation",
            "ImportDeclaration", "PackageDeclaration");

    public List<EditPattern> extract(GumTreeDiff diff) {
        Map<String, GumTreeNode> srcIndex = new HashMap<>();
        Map<String, GumTreeNode> dstIndex = new HashMap<>();
        Map<String, GumTreeNode> srcParent = new HashMap<>();
        Map<String, GumTreeNode> dstParent = new HashMap<>();
        indexTree(diff.srcTree(), null, srcIndex, srcParent);
        indexTree(diff.dstTree(), null, dstIndex, dstParent);

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
        // Root-type mismatch (e.g., `expr → !expr` wraps in PrefixExpression): walk(src) would
        // see structurallyEqual=true on the moved subtree and skip extraction. Emit explicitly.
        if (!diff.srcTree().type().equals(diff.dstTree().type())) {
            out.add(buildEditPattern(diff.srcTree(), diff.dstTree(), touched,
                    srcToDst, dstToSrc, dstIndex, srcParent, dstParent));
        }
        walk(diff.srcTree(), touched, srcToDst, dstToSrc, dstIndex,
                srcParent, dstParent, out);
        return out;
    }

    /** Builds a single EditPattern from matched (src, dst) subtrees with §8 hole-ification. */
    private static EditPattern buildEditPattern(GumTreeNode src, GumTreeNode dst,
                                                Set<String> touched,
                                                Map<String, String> srcToDst,
                                                Map<String, String> dstToSrc,
                                                Map<String, GumTreeNode> dstIndex,
                                                Map<String, GumTreeNode> srcParent,
                                                Map<String, GumTreeNode> dstParent) {
        Set<TreePattern> unmodSrc = EditPattern.identitySet();
        Set<TreePattern> unmodDst = EditPattern.identitySet();
        AtomicInteger seq = new AtomicInteger();
        Map<String, String> nodeIdToHole = new HashMap<>();
        preallocateMatchedHoles(src, srcToDst, dstIndex, srcParent, dstParent,
                nodeIdToHole, seq);
        TreePattern srcPattern = toPatternMarkingUnmod(src, touched, true, srcToDst,
                srcParent, unmodSrc, nodeIdToHole, seq);
        TreePattern dstPattern = toPatternMarkingUnmod(dst, touched, false, dstToSrc,
                dstParent, unmodDst, nodeIdToHole, seq);
        return new EditPattern(srcPattern, dstPattern, unmodSrc, unmodDst);
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
                                Map<String, String> dstToSrc,
                                Map<String, GumTreeNode> dstIndex,
                                Map<String, GumTreeNode> srcParent,
                                Map<String, GumTreeNode> dstParent,
                                List<EditPattern> out) {
        boolean modified = touched.contains(src.identifier());
        for (GumTreeNode child : src.children()) {
            if (walk(child, touched, srcToDst, dstToSrc, dstIndex,
                    srcParent, dstParent, out)) modified = true;
        }
        if (!modified) return false;
        String dstId = srcToDst.get(src.identifier());
        if (dstId == null) return true;
        GumTreeNode dst = dstIndex.get(dstId);
        if (dst == null || structurallyEqual(src, dst)) return true;
        out.add(buildEditPattern(src, dst, touched, srcToDst, dstToSrc,
                dstIndex, srcParent, dstParent));
        return true;
    }

    /** Share one hole id between matched src/dst identifier pairs so F3 (afterHoles ⊇ beforeHoles) holds. */
    private static void preallocateMatchedHoles(GumTreeNode s,
                                                Map<String, String> srcToDst,
                                                Map<String, GumTreeNode> dstIndex,
                                                Map<String, GumTreeNode> srcParent,
                                                Map<String, GumTreeNode> dstParent,
                                                Map<String, String> nodeIdToHole,
                                                AtomicInteger seq) {
        if (shouldHoleify(s, srcParent)) {
            String dId = srcToDst.get(s.identifier());
            if (dId != null) {
                GumTreeNode d = dstIndex.get(dId);
                if (d != null && shouldHoleify(d, dstParent)) {
                    String hid = "?h" + seq.getAndIncrement();
                    nodeIdToHole.put(s.identifier(), hid);
                    nodeIdToHole.put(d.identifier(), hid);
                }
            }
        }
        for (GumTreeNode c : s.children()) {
            preallocateMatchedHoles(c, srcToDst, dstIndex, srcParent, dstParent, nodeIdToHole, seq);
        }
    }

    /** True iff this SimpleName/QualifiedName should become a Hole (vs. preserved literal). */
    private static boolean shouldHoleify(GumTreeNode n, Map<String, GumTreeNode> parents) {
        if (!isIdentifierNode(n)) return false;
        // QualifiedName wraps inner Names; climb out to judge by the enclosing context.
        GumTreeNode p = parents.get(n.identifier());
        while (p != null && "QualifiedName".equals(p.type())) p = parents.get(p.identifier());
        if (p == null) return true;
        // Type context: empty whitelist → preserve all (default); non-empty → hole-ify if not whitelisted.
        if (LITERAL_PARENT_TYPES.contains(p.type())) {
            return !COMMON_TYPE_NAMES.isEmpty() && !COMMON_TYPE_NAMES.contains(n.label());
        }
        // Variable/method/declaration/expression contexts: always hole.
        return true;
    }

    private static boolean isIdentifierNode(GumTreeNode n) {
        return "SimpleName".equals(n.type()) || "QualifiedName".equals(n.type());
    }

    /** Non-boundary NumberLiteral labels collapse to MAGIC_NUMBER_TOKEN so magic-value swaps cluster. */
    private static String canonicalLabel(GumTreeNode n) {
        if ("NumberLiteral".equals(n.type()) && !CANONICAL_NUMERIC_LABELS.contains(n.label())) {
            return MAGIC_NUMBER_TOKEN;
        }
        return n.label();
    }

    /** Builds the pattern and marks a node as an unmod root when no descendant is touched and a counterpart exists. */
    private static TreePattern toPatternMarkingUnmod(GumTreeNode node,
                                                     Set<String> touched,
                                                     boolean isSrcSide,
                                                     Map<String, String> mapping,
                                                     Map<String, GumTreeNode> parents,
                                                     Set<TreePattern> unmodOut,
                                                     Map<String, String> nodeIdToHole,
                                                     AtomicInteger seq) {
        if (shouldHoleify(node, parents)) {
            String hid = nodeIdToHole.get(node.identifier());
            if (hid == null) {
                hid = "?h" + seq.getAndIncrement();
                nodeIdToHole.put(node.identifier(), hid);
            }
            return new Hole(hid);
        }
        if (isFullyUnmod(node, touched, isSrcSide, mapping)) {
            // Hole-ify inside unmod too so singleton patterns also benefit (no AU pass to do it).
            TreePattern p = toPatternHoleify(node, parents, nodeIdToHole, seq);
            unmodOut.add(p);
            return p;
        }
        List<TreePattern> kids = new ArrayList<>(node.children().size());
        for (GumTreeNode c : node.children()) {
            kids.add(toPatternMarkingUnmod(c, touched, isSrcSide, mapping, parents,
                    unmodOut, nodeIdToHole, seq));
        }
        return new TreeNode(node.type(), canonicalLabel(node), kids);
    }

    /** Recursive conversion that hole-ifies identifiers per §8 but does not propagate unmod marking. */
    private static TreePattern toPatternHoleify(GumTreeNode node,
                                                Map<String, GumTreeNode> parents,
                                                Map<String, String> nodeIdToHole,
                                                AtomicInteger seq) {
        if (shouldHoleify(node, parents)) {
            String hid = nodeIdToHole.get(node.identifier());
            if (hid == null) {
                hid = "?h" + seq.getAndIncrement();
                nodeIdToHole.put(node.identifier(), hid);
            }
            return new Hole(hid);
        }
        List<TreePattern> kids = new ArrayList<>(node.children().size());
        for (GumTreeNode c : node.children()) {
            kids.add(toPatternHoleify(c, parents, nodeIdToHole, seq));
        }
        return new TreeNode(node.type(), canonicalLabel(node), kids);
    }

    private static boolean isFullyUnmod(GumTreeNode node, Set<String> touched,
                                        boolean isSrcSide, Map<String, String> mapping) {
        if (isSrcSide) {
            if (!mapping.containsKey(node.identifier())) return false;
            if (touched.contains(node.identifier())) return false;
        } else {
            String srcId = mapping.get(node.identifier());
            if (srcId == null) return false;
            if (touched.contains(srcId)) return false;
        }
        for (GumTreeNode c : node.children()) {
            if (!isFullyUnmod(c, touched, isSrcSide, mapping)) return false;
        }
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

}
