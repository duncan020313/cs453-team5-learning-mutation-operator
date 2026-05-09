package astramut.learn;

public record EditPattern(TreePattern before, TreePattern after) {
    public int holeCount() { return before.holeCount() + after.holeCount(); }
    public int nodeCount() { return before.nodeCount() + after.nodeCount(); }
}
