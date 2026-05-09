package astramut.learn;

import java.util.List;

public record LearnedPattern(EditPattern pattern, int support, double specificity, List<EditPattern> examples) {
    public LearnedPattern {
        examples = List.copyOf(examples);
    }

    public double score() { return support * specificity; }
}
