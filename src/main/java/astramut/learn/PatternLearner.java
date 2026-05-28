package astramut.learn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PatternLearner {

    /** F4 size cap: singleton ground patterns above this node count are over-specific noise. */
    public static final int F4_GROUND_SINGLETON_MAX_NODES = 8;

    public PatternLearner() {}

    public LearnedModel learn(Iterable<GumTreeDiff> diffs) {
        ConcreteEditExtractor extractor = new ConcreteEditExtractor();
        List<EditPattern> concretes = new ArrayList<>();
        for (GumTreeDiff d : diffs) concretes.addAll(extractor.extract(d));

        List<HierarchicalClusterer.Cluster> clusters =
                new HierarchicalClusterer().cluster(concretes);

        // Diagnostic counters
        int dF2 = 0, dF3 = 0, dF4 = 0, dF5 = 0, dF6 = 0;

        List<LearnedPattern> patterns = new ArrayList<>();
        for (HierarchicalClusterer.Cluster c : clusters) {
            int support = c.members().size();
            EditPattern rep = c.representative();
            if (rep.before().equals(rep.after())) { dF2++; continue; }
            if (!rep.isMutationSafe()) { dF3++; continue; }
            if (support == 1 && rep.holeCount() == 0
                    && rep.nodeCount() > F4_GROUND_SINGLETON_MAX_NODES) { dF4++; continue; }
            if (support == 1 && isBareIdentifierRoot(rep.before()) && isBareIdentifierRoot(rep.after())) {
                dF5++; continue;
            }
            // F6: drop bare-Hole root only if its id is absent on the other side (else it's a valid wrap/unwrap).
            if (isDestructiveBareHole(rep)) { dF6++; continue; }
            patterns.add(new LearnedPattern(
                    rep, support, specificity(rep), c.members()));
        }
        if (System.getenv("ASTRAMUT_FILTER_STATS") != null) {
            System.err.printf("[filter-stats] concretes=%d clusters=%d kept=%d  F2=%d F3=%d F4=%d F5=%d F6=%d%n",
                    concretes.size(), clusters.size(), patterns.size(), dF2, dF3, dF4, dF5, dF6);
        }
        patterns.sort(Comparator.comparingDouble(LearnedPattern::score).reversed());
        return new LearnedModel(patterns);
    }

    private static boolean isDestructiveBareHole(EditPattern rep) {
        if (rep.before() instanceof Hole h) {
            return !rep.afterHoleIds().contains(h.id());
        }
        if (rep.after() instanceof Hole h) {
            return !rep.beforeHoleIds().contains(h.id());
        }
        return false;
    }

    private static boolean isBareIdentifierRoot(TreePattern p) {
        if (!(p instanceof TreeNode n)) return false;
        if (!n.children().isEmpty()) return false;
        String t = n.type();
        return "SimpleName".equals(t) || "QualifiedName".equals(t);
    }

    private static double specificity(EditPattern p) {
        int holes = p.holeCount();
        int nodes = p.nodeCount();
        return nodes == 0 ? 0.0 : 1.0 - (double) holes / nodes;
    }
}
