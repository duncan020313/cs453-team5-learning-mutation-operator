package astramut.diff;

import astramut.learn.AstNode;
import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.tree.Tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GumTreeAstDiffAdapter {
    public astramut.learn.AstDiff convert(GumTreeDiff diff) {
        AstNode beforeNode = toAstNode(diff.beforeTree(), "b");
        AstNode afterNode  = toAstNode(diff.afterTree(),  "a");

        Map<String, String> beforeToAfter = new HashMap<>();
        for (Mapping mapping : diff.mappings()) {
            String beforeId = nodeId(mapping.first,  "b");
            String afterId  = nodeId(mapping.second, "a");
            beforeToAfter.put(beforeId, afterId);
        }

        return new astramut.learn.AstDiff(beforeNode, afterNode, beforeToAfter);
    }

    private AstNode toAstNode(Tree tree, String prefix) {
        String id    = nodeId(tree, prefix);
        String label = tree.getType().name;
        String value = tree.getLabel();

        List<AstNode.ChildSlot> children = new ArrayList<>(tree.getChildren().size());
        for (int i = 0; i < tree.getChildren().size(); i++) {
            Tree child = tree.getChild(i);
            String location = String.valueOf(i);
            children.add(new AstNode.ChildSlot(location, toAstNode(child, prefix)));
        }

        return new AstNode(id, label, value, children);
    }

    private static String nodeId(Tree tree, String prefix) {
        return prefix + ":" + tree.getPos() + ":" + tree.getLength()
                + ":" + tree.getType().name;
    }
}