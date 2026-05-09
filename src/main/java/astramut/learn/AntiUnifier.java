package astramut.learn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AntiUnifier {
    private AntiUnifier() {}

    public static EditPattern antiUnify(EditPattern p1, EditPattern p2) {
        // Sharing the allocator across before- and after-side traversals lets
        // a hole spanning the same (subtree₁, subtree₂) pair receive the same
        // identifier on both sides. This is what makes the learned pattern
        // usable as a rewrite rule: the variable bound on the LHS is reused
        // on the RHS.
        HoleAllocator alloc = new HoleAllocator();
        TreePattern before = au(p1.before(), p2.before(), alloc);
        TreePattern after = au(p1.after(), p2.after(), alloc);
        return new EditPattern(before, after);
    }

    public static TreePattern au(TreePattern t1, TreePattern t2, HoleAllocator alloc) {
        if (t1.equals(t2)) return t1;
        if (t1 instanceof TreeNode n1 && t2 instanceof TreeNode n2
                && n1.label().equals(n2.label())
                && n1.value().equals(n2.value())
                && sameChildLocations(n1, n2)) {
            List<TreeNode.ChildSlot> kids = new ArrayList<>(n1.children().size());
            for (int i = 0; i < n1.children().size(); i++) {
                TreeNode.ChildSlot c1 = n1.children().get(i);
                TreeNode.ChildSlot c2 = n2.children().get(i);
                kids.add(new TreeNode.ChildSlot(c1.location(), au(c1.child(), c2.child(), alloc)));
            }
            return new TreeNode(n1.label(), n1.value(), kids);
        }
        return alloc.holeFor(t1, t2);
    }

    private static boolean sameChildLocations(TreeNode a, TreeNode b) {
        if (a.children().size() != b.children().size()) return false;
        for (int i = 0; i < a.children().size(); i++) {
            if (!a.children().get(i).location().equals(b.children().get(i).location())) return false;
        }
        return true;
    }

    public static final class HoleAllocator {
        private final Map<HoleKey, Hole> map = new HashMap<>();

        public Hole holeFor(TreePattern left, TreePattern right) {
            return map.computeIfAbsent(new HoleKey(left, right), k -> new Hole("?h" + map.size()));
        }
    }

    private record HoleKey(TreePattern left, TreePattern right) {}
}
