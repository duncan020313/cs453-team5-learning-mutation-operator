package astramut.learn;

import java.util.List;

public record LearnedModel(List<LearnedPattern> patterns) {
    public LearnedModel {
        patterns = List.copyOf(patterns);
    }
}
