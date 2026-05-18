package astramut.learn;

import java.util.List;

/** One AST diff as produced by gumtree — both trees plus matches and edit script. */
public record GumTreeDiff(GumTreeNode srcTree,
                          GumTreeNode dstTree,
                          List<GumTreeMatch> matches,
                          List<GumTreeAction> actions) {

    public GumTreeDiff {
        matches = List.copyOf(matches);
        actions = List.copyOf(actions);
    }
}
