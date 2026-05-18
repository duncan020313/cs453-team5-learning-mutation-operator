package astramut.mutation;

import astramut.learn.Hole;
import astramut.learn.TreeNode;
import astramut.learn.TreePattern;

import java.util.HashMap;
import java.util.Map;

public final class PatternMatcher {
    private PatternMatcher() {
    }

    public static MatchResult match(TreePattern pattern, TreePattern target) {
        Map<String, TreePattern> bindings = new HashMap<>();
        boolean matched = matchInto(pattern, target, bindings);
        return new MatchResult(matched, bindings);
    }

    private static boolean matchInto(
            TreePattern pattern,
            TreePattern target,
            Map<String, TreePattern> bindings
    ) {
        if (pattern instanceof Hole h) {
            TreePattern previous = bindings.get(h.id());

            if (previous == null) {
                bindings.put(h.id(), target);
                return true;
            }

            return previous.equals(target);
        }

        if (!(pattern instanceof TreeNode p)) {
            return false;
        }

        if (!(target instanceof TreeNode t)) {
            return false;
        }

        if (!typeMatches(p.type(), t.type())) {
            return false;
        }

        if (!labelMatches(p.label(), t.label())) {
            return false;
        }

        if (p.children().size() != t.children().size()) {
            return false;
        }

        for (int i = 0; i < p.children().size(); i++) {
            if (!matchInto(p.children().get(i), t.children().get(i), bindings)) {
                return false;
            }
        }

        return true;
    }

    private static boolean typeMatches(String patternType, String targetType) {
        if (patternType.equals(targetType)) {
            return true;
        }

        if (patternType.equals("InfixExpression") && targetType.equals("InfixExpression")) {
            return true;
        }

        return false;
    }

    private static boolean labelMatches(String patternLabel, String targetLabel) {
        if (patternLabel == null || patternLabel.isBlank()) {
            return true;
        }

        return patternLabel.equals(targetLabel);
    }

    public record MatchResult(boolean matched, Map<String, TreePattern> bindings) {
        public MatchResult {
            bindings = Map.copyOf(bindings);
        }
    }
}