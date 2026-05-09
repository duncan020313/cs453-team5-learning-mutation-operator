package astramut.learn;

public sealed interface TreePattern permits TreeNode, Hole {
    int holeCount();
    int nodeCount();
}
