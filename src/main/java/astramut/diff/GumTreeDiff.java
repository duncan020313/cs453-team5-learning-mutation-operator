package astramut.diff;

import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.Tree;

import java.util.List;

public record GumTreeDiff(
    String beforeCode,
    String afterCode,
    Tree beforeTree,
    Tree afterTree,
    List<Action> actions,
    MappingStore mappings
) {
    public boolean hasChanges() {
        return actions != null && !actions.isEmpty();
    }
}