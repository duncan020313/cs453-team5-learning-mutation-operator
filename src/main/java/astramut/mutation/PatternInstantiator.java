package astramut.mutation;

import astramut.learn.Hole;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PatternInstantiator {
    private PatternInstantiator() {
    }

    public static TreePattern instantiate(TreePattern pattern, Map<String, TreePattern> bindings) {
        return instantiate(pattern, bindings, null);
    }

    /** Bind holes from {@code bindings} and replace NumberLiteral(__MAGIC__) with {@code sampler.sample()}. */
    public static TreePattern instantiate(
            TreePattern pattern,
            Map<String, TreePattern> bindings,
            MagicValueSampler sampler) {
        if (pattern instanceof Hole h) {
            TreePattern bound = bindings.get(h.id());
            if (bound == null) {
                throw new IllegalStateException("Unbound hole: " + h.id());
            }
            return bound;
        }

        if (!(pattern instanceof TreeNode n)) {
            throw new IllegalArgumentException("Unknown TreePattern implementation: " + pattern);
        }

        // Materialize NumberLiteral(__MAGIC__) with a sampled concrete value.
        if ("NumberLiteral".equals(n.type())
                && PatternMatcher.MAGIC_NUMBER_TOKEN.equals(n.label())) {
            String concrete = sampler != null ? sampler.sample() : "2";
            return new TreeNode(n.type(), concrete, List.of());
        }

        List<TreePattern> children = new ArrayList<>();
        for (TreePattern child : n.children()) {
            children.add(instantiate(child, bindings, sampler));
        }

        return new TreeNode(n.type(), n.label(), children);
    }
}
