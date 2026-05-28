package astramut.learn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class AntiUnifier {
    private AntiUnifier() {}

    public static EditPattern antiUnify(EditPattern p1, EditPattern p2) {
        // Rename input holes onto a shared seq so pre-existing ids never collide.
        AtomicInteger seq = new AtomicInteger();
        EditPattern r1 = renameHoles(p1, seq);
        EditPattern r2 = renameHoles(p2, seq);
        HoleAllocator alloc = new HoleAllocator(seq);
        TreePattern before = au(r1.before(), r2.before(),
                                r1.unmodBefore(), r2.unmodBefore(), alloc);
        TreePattern after = au(r1.after(), r2.after(),
                               r1.unmodAfter(), r2.unmodAfter(), alloc);
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

    private static EditPattern renameHoles(EditPattern e, AtomicInteger seq) {
        Map<String, String> remap = new HashMap<>();
        Set<TreePattern> newUnmodBefore = EditPattern.identitySet();
        Set<TreePattern> newUnmodAfter = EditPattern.identitySet();
        TreePattern newBefore = rename(e.before(), e.unmodBefore(), newUnmodBefore, remap, seq);
        TreePattern newAfter = rename(e.after(), e.unmodAfter(), newUnmodAfter, remap, seq);
        return new EditPattern(newBefore, newAfter, newUnmodBefore, newUnmodAfter);
    }

    private static TreePattern rename(TreePattern p, Set<TreePattern> oldUnmod,
                                      Set<TreePattern> newUnmod,
                                      Map<String, String> remap, AtomicInteger seq) {
        boolean wasUnmod = oldUnmod.contains(p);
        TreePattern result;
        if (p instanceof Hole h) {
            String hid = remap.computeIfAbsent(h.id(), k -> "?h" + seq.getAndIncrement());
            result = new Hole(hid);
        } else {
            TreeNode n = (TreeNode) p;
            List<TreePattern> kids = new ArrayList<>(n.children().size());
            for (TreePattern c : n.children()) kids.add(rename(c, oldUnmod, newUnmod, remap, seq));
            result = new TreeNode(n.type(), n.label(), kids);
        }
        if (wasUnmod) newUnmod.add(result);
        return result;
    }

    public static final class HoleAllocator {
        private final Map<HoleKey, Hole> map = new HashMap<>();
        private final AtomicInteger seq;

        public HoleAllocator() { this(new AtomicInteger()); }
        public HoleAllocator(AtomicInteger seq) { this.seq = seq; }

        public Hole holeFor(TreePattern left, TreePattern right) {
            return map.computeIfAbsent(new HoleKey(left, right),
                    k -> new Hole("?h" + seq.getAndIncrement()));
        }
    }

    private record HoleKey(TreePattern left, TreePattern right) {}
}
