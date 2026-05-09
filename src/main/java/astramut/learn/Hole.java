package astramut.learn;

public record Hole(String id) implements TreePattern {
    @Override public int holeCount() { return 1; }
    @Override public int nodeCount() { return 1; }
}
