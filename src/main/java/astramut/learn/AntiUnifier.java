package astramut.learn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AntiUnifier {
    private AntiUnifier() {}

    public static EditPattern antiUnify(EditPattern p1, EditPattern p2) {
        // Shared allocator across before/after: same (t1,t2) pair yields same hole id on both sides,
        // which makes the pattern usable as a rewrite rule and implements §4.1.3 populate-back.
        HoleAllocator alloc = new HoleAllocator();
        TreePattern before = au(p1.before(), p2.before(),
                                p1.unmodBefore(), p2.unmodBefore(), alloc);
        TreePattern after = au(p1.after(), p2.after(),
                               p1.unmodAfter(), p2.unmodAfter(), alloc);
        return new EditPattern(before, after);
    }

    public static TreePattern au(TreePattern t1, TreePattern t2, HoleAllocator alloc) {
        return au(t1, t2, Collections.emptySet(), Collections.emptySet(), alloc);
    }

    public static TreePattern au(TreePattern t1, TreePattern t2,
                                 Set<TreePattern> unmod1, Set<TreePattern> unmod2,
                                 HoleAllocator alloc) {
        // Equals check before stripUnmod: identical unmod subtrees stay as-is rather than becoming holes.
        if (t1.equals(t2)) return t1;
        // stripUnmod boundary: collapse the whole unchanged context into one shared hole.
        if (unmod1.contains(t1) && unmod2.contains(t2)) {
            return alloc.holeFor(t1, t2);
        }
        if (t1 instanceof TreeNode n1 && t2 instanceof TreeNode n2
                && n1.type().equals(n2.type())
                && n1.label().equals(n2.label())
                && n1.children().size() == n2.children().size()) {
            List<TreePattern> kids = new ArrayList<>(n1.children().size());
            for (int i = 0; i < n1.children().size(); i++) {
                kids.add(au(n1.children().get(i), n2.children().get(i),
                            unmod1, unmod2, alloc));
            }
            return new TreeNode(n1.type(), n1.label(), kids);
        }
        return alloc.holeFor(t1, t2);
    }

    public static final class HoleAllocator {
        private final Map<HoleKey, Hole> map = new HashMap<>();

        public Hole holeFor(TreePattern left, TreePattern right) {
            return map.computeIfAbsent(new HoleKey(left, right), k -> new Hole("?h" + map.size()));
        }
    }

    private record HoleKey(TreePattern left, TreePattern right) {}
}
