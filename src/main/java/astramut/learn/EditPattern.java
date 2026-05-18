package astramut.learn;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/** Before/after pattern pair. Unmod sets mark maximal unchanged subtree roots for Getafix §4.1.3 stripUnmod. */
public record EditPattern(TreePattern before,
                          TreePattern after,
                          Set<TreePattern> unmodBefore,
                          Set<TreePattern> unmodAfter) {

    public EditPattern(TreePattern before, TreePattern after) {
        this(before, after, identitySet(), identitySet());
    }

    public int holeCount() { return before.holeCount() + after.holeCount(); }
    public int nodeCount() { return before.nodeCount() + after.nodeCount(); }

    public Set<String> beforeHoleIds() { return collectHoleIds(before); }
    public Set<String> afterHoleIds() { return collectHoleIds(after); }

    /** Holes in {@code after} not in {@code before} — Getafix §4.2.3 step 1 "unbound holes in the after part". */
    public int unboundAfterHoleCount() {
        Set<String> lhs = beforeHoleIds();
        int n = 0;
        for (String id : afterHoleIds()) if (!lhs.contains(id)) n++;
        return n;
    }

    /** True iff every LHS hole is in RHS — after Mutation Monkey swap, every new-RHS hole is bindable. */
    public boolean isMutationSafe() {
        return afterHoleIds().containsAll(beforeHoleIds());
    }

    public static Set<TreePattern> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static Set<String> collectHoleIds(TreePattern p) {
        Set<String> ids = new HashSet<>();
        collectInto(p, ids);
        return ids;
    }

    private static void collectInto(TreePattern p, Set<String> ids) {
        if (p instanceof Hole h) { ids.add(h.id()); return; }
        if (p instanceof TreeNode n) {
            for (TreePattern c : n.children()) collectInto(c, ids);
        }
    }
}
