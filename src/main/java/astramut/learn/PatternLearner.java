package astramut.learn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PatternLearner {

    public static final int DEFAULT_MIN_SUPPORT = 2;
    public static final int DEFAULT_MAX_HOLES = 4;

    private final int minSupport;
    private final int maxHoles;

    public PatternLearner() {
        this(DEFAULT_MIN_SUPPORT, DEFAULT_MAX_HOLES);
    }

    public PatternLearner(int minSupport, int maxHoles) {
        this.minSupport = minSupport;
        this.maxHoles = maxHoles;
    }

    public LearnedModel learn(Iterable<GumTreeDiff> diffs) {
        ConcreteEditExtractor extractor = new ConcreteEditExtractor();
        List<EditPattern> concretes = new ArrayList<>();
        for (GumTreeDiff d : diffs) concretes.addAll(extractor.extract(d));

        List<HierarchicalClusterer.Cluster> clusters =
                new HierarchicalClusterer(maxHoles).cluster(concretes);

        List<LearnedPattern> patterns = new ArrayList<>();
        for (HierarchicalClusterer.Cluster c : clusters) {
            int support = c.members().size();
            if (support < minSupport) continue;
            // Drop patterns that aren't mutation-safe: after swap they'd have unbound new-RHS holes.
            if (!c.representative().isMutationSafe()) continue;
            patterns.add(new LearnedPattern(
                    c.representative(), support,
                    specificity(c.representative()), c.members()));
        }
        patterns.sort(Comparator.comparingDouble(LearnedPattern::score).reversed());
        return new LearnedModel(patterns);
    }

    private static double specificity(EditPattern p) {
        int holes = p.holeCount();
        int nodes = p.nodeCount();
        return nodes == 0 ? 0.0 : 1.0 - (double) holes / nodes;
    }
}
