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

        List<TreePattern> children = new ArrayList<>();
        for (TreePattern child : n.children()) {
            children.add(instantiate(child, bindings));
        }

        return new TreeNode(n.type(), n.label(), children);
    }
}