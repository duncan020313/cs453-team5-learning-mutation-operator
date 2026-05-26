package astramut.learn;

public record LearnedPatternEntry(
    String bugType, int cohortSize, int rank, double score, LearnedPattern pattern) {}
