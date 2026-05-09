package astramut.learn;

import java.util.Map;

public record AstDiff(AstNode before, AstNode after, Map<String, String> beforeToAfter) {
    public AstDiff {
        beforeToAfter = Map.copyOf(beforeToAfter);
    }
}
