package astramut.learn;

import java.util.List;

/**
 * One AST diff as produced by GumTree: the parsed src and dst trees plus the
 * matches and edit script that {@code gumtree textdiff -f JSON} emits.
 *
 * <p>This is the contract the diff team hands us — once a real GumTree run is
 * wired in, populating this record from the JSON output is a mechanical
 * Gson/Jackson deserialization. The learn module never re-derives the
 * matches or actions; it consumes them directly.
 */
public record GumTreeDiff(GumTreeNode srcTree,
                          GumTreeNode dstTree,
                          List<GumTreeMatch> matches,
                          List<GumTreeAction> actions) {

    public GumTreeDiff {
        matches = List.copyOf(matches);
        actions = List.copyOf(actions);
    }
}
